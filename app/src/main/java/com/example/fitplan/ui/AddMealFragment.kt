package com.example.fitplan.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fitplan.App
import com.example.fitplan.Models.Api.ApiManager
import com.example.fitplan.Models.Product
import com.example.fitplan.R
import kotlinx.coroutines.*

class AddMealFragment : Fragment() {

    private lateinit var mealName: String
    private val db by lazy { App.instance.db }

    private lateinit var productSearch: AutoCompleteTextView
    private lateinit var quantityEdit: EditText
    private lateinit var caloriesTv: TextView
    private lateinit var proteinTv: TextView
    private lateinit var fatTv: TextView
    private lateinit var carbsTv: TextView
    private lateinit var btnAdd: Button
    private lateinit var btnCancel: Button
    private lateinit var searchProgress: ProgressBar

    private var selectedProduct: Product? = null
    private lateinit var adapter: ArrayAdapter<String>
    private var searchJob: Job? = null
    private var lastQuery: String = ""
    private var searchAttempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mealName = requireArguments().getString("mealName") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_add_meal, container, false)

        productSearch = view.findViewById(R.id.productSearchAutoComplete)
        quantityEdit = view.findViewById(R.id.quantityEditText)
        caloriesTv = view.findViewById(R.id.caloriesValue)
        proteinTv = view.findViewById(R.id.proteinValue)
        fatTv = view.findViewById(R.id.fatValue)
        carbsTv = view.findViewById(R.id.carbsValue)
        btnAdd = view.findViewById(R.id.btnAddMeal)
        btnCancel = view.findViewById(R.id.btnCancel)
        searchProgress = view.findViewById(R.id.searchProgress)

        adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        productSearch.setAdapter(adapter)
        productSearch.threshold = 1

        setupListeners()

        return view
    }

    private fun setupListeners() {
        productSearch.addTextChangedListener(object : TextWatcher {
            private val handler = android.os.Handler()
            private val runnable = Runnable {
                val currentQuery = productSearch.text.toString().trim()
                if (currentQuery.isNotEmpty() && currentQuery.length >= 2) {
                    startSearch(currentQuery)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                lastQuery = query

                // Отменяем предыдущий отложенный поиск
                handler.removeCallbacks(runnable)
                searchJob?.cancel()

                if (query.isEmpty()) {
                    adapter.clear()
                    adapter.notifyDataSetChanged()
                    selectedProduct = null
                    recalcNutrition()
                    searchProgress.visibility = View.GONE
                    return
                }

                // Сразу показываем кэшированные результаты
                showCachedResults(query)

                // Если запрос короткий, не ищем в API
                if (query.length < 3) return

                // Запускаем новый поиск через 500мс (дебаунс)
                searchProgress.visibility = View.VISIBLE
                handler.postDelayed(runnable, 500)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        productSearch.setOnItemClickListener { _, _, position, _ ->
            val name = adapter.getItem(position) ?: return@setOnItemClickListener

            searchJob?.cancel()
            searchProgress.visibility = View.GONE

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Сначала ищем в кэше API
                    selectedProduct = ApiManager.getCachedProductByName(name)

                    // Если не нашли, ищем в локальной БД
                    if (selectedProduct == null) {
                        selectedProduct = withContext(Dispatchers.IO) {
                            db.getProductByName(name)
                        }
                    }

                    // Если все еще не нашли, создаем пустой продукт
                    if (selectedProduct == null) {
                        selectedProduct = Product(
                            id = 0,
                            name = name,
                            calories = 0f,
                            protein = 0f,
                            fat = 0f,
                            carbs = 0f
                        )
                    }

                    recalcNutrition()

                } catch (e: Exception) {
                    Log.e("AddMealFragment", "Ошибка выбора", e)
                }
            }
        }

        quantityEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = recalcNutrition()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnAdd.setOnClickListener {
            addProductToMeal()
        }

        btnCancel.setOnClickListener {
            searchJob?.cancel()
            parentFragmentManager.popBackStack()
        }
    }

    private fun showCachedResults(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Ищем локальные результаты из БД
                val localResults = withContext(Dispatchers.IO) {
                    db.getAllProductsMatching(query)
                }.take(10)

                if (localResults.isNotEmpty()) {
                    updateAdapterUI(localResults)
                }

                // Дополняем кэшированными результатами из API
                    //val cachedApiResults = ApiManager.getCachedResults(query)
                //if (cachedApiResults.isNotEmpty()) {
                //    val combined = (localResults + cachedApiResults)
                 //       .distinctBy { it.name }
                //        .take(15)
                 //   updateAdapterUI(combined)
               // }

            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
    }

    private fun startSearch(query: String) {
        searchJob?.cancel()

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                searchAttempts++
                Log.d("AddMealFragment", "Поиск #$searchAttempts: '$query'")

                // Параллельно ищем в API и локальной БД
                val apiJob = async(Dispatchers.IO) {
                    ApiManager.searchProducts(query)
                }

                val localJob = async(Dispatchers.IO) {
                    db.getAllProductsMatching(query)
                }

                // Ждем оба результата с таймаутом
                val results = withTimeoutOrNull(3000) {
                    val apiResults = apiJob.await()
                    val localResults = localJob.await()

                    // Объединяем результаты
                    val combined = (localResults + apiResults)
                        .distinctBy { it.name }
                        .take(20)

                    combined
                }

                // Проверяем что запрос все еще актуален
                if (query == lastQuery) {
                    withContext(Dispatchers.Main) {
                        searchProgress.visibility = View.GONE

                        results?.let {
                            if (it.isNotEmpty()) {
                                updateAdapterUI(it)

                                // Сохраняем API результаты в БД
                                launch(Dispatchers.IO) {
                                    it.forEach { product ->
                                        try {
                                            db.insertOrGetProduct(product)
                                        } catch (e: Exception) {
                                            // Игнорируем ошибки сохранения
                                        }
                                    }
                                }
                            } else if (query.length >= 3) {
                                Toast.makeText(
                                    requireContext(),
                                    "Ничего не найдено",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                // Поиск отменен
            } catch (e: TimeoutCancellationException) {
                Log.d("AddMealFragment", "Таймаут поиска")
                withContext(Dispatchers.Main) {
                    searchProgress.visibility = View.GONE
                    if (query == lastQuery && adapter.count == 0) {
                        Toast.makeText(
                            requireContext(),
                            "Поиск занял слишком много времени",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AddMealFragment", "Ошибка поиска", e)
                withContext(Dispatchers.Main) {
                    searchProgress.visibility = View.GONE
                }
            }
        }
    }

    private fun updateAdapterUI(products: List<Product>) {
        if (!isAdded || products.isEmpty()) return

        // Ограничиваем количество
        val limitedProducts = products.take(15)

        // Сортируем по релевантности
        val query = productSearch.text.toString().lowercase()
        val sortedProducts = limitedProducts.sortedBy { product ->
            val name = product.name.lowercase()
            when {
                name == query -> 0
                name.startsWith(query) -> 1
                name.contains(query) -> 2
                else -> 3
            }
        }

        adapter.clear()
        adapter.addAll(sortedProducts.map { it.name })
        adapter.notifyDataSetChanged()

        if (!productSearch.isPopupShowing && adapter.count > 0) {
            productSearch.showDropDown()
        }
    }

    private fun addProductToMeal() {
        val product = selectedProduct
        if (product == null) {
            Toast.makeText(requireContext(), "Выберите продукт", Toast.LENGTH_SHORT).show()
            return
        }

        val quantity = quantityEdit.text.toString().toIntOrNull() ?: 100
        if (quantity <= 0) {
            Toast.makeText(requireContext(), "Введите корректное количество", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    try {
                        val prefs = requireContext().getSharedPreferences("session", Context.MODE_PRIVATE)
                        val userId = prefs.getLong("user_id", -1L)
                        if (userId == -1L) return@withContext false

                        val factor = quantity / 100f
                        val productId = db.insertOrGetProduct(product)

                        db.addMeal(
                            userId, productId, mealName, quantity,
                            product.calories * factor,
                            product.protein * factor,
                            product.fat * factor,
                            product.carbs * factor
                        )
                        true
                    } catch (e: Exception) {
                        Log.e("AddMealFragment", "Ошибка БД", e)
                        false
                    }
                }

                if (success) {
                    parentFragmentManager.setFragmentResult("meal_added", Bundle.EMPTY)
                    parentFragmentManager.popBackStack()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка при добавлении",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun recalcNutrition() {
        val product = selectedProduct
        val quantity = quantityEdit.text.toString().toIntOrNull() ?: 100
        val factor = if (quantity > 0) quantity / 100f else 0f

        val calories = ((product?.calories ?: 0f) * factor).toInt()
        val protein = ((product?.protein ?: 0f) * factor).toInt()
        val fat = ((product?.fat ?: 0f) * factor).toInt()
        val carbs = ((product?.carbs ?: 0f) * factor).toInt()

        caloriesTv.text = calories.toString()
        proteinTv.text = "$protein г"
        fatTv.text = "$fat г"
        carbsTv.text = "$carbs г"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        searchJob = null
    }

    companion object {
        fun newInstance(mealName: String) =
            AddMealFragment().apply {
                arguments = Bundle().apply {
                    putString("mealName", mealName)
                }
            }
    }
}