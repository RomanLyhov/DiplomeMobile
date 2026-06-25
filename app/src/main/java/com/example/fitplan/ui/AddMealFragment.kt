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
import com.example.fitplan.Models.MealDto
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
    private var currentProducts: List<Product> = emptyList()

    private var selectedProduct: Product? = null
    private lateinit var adapter: ArrayAdapter<String>
    private var searchJob: Job? = null
    private var searchRequestId = 0L
    private var lastQuery: String = ""

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
        productSearch.threshold = 2

        setupListeners()

        return view
    }

    private fun setupListeners() {
        // ===== TextWatcher для поиска продукта =====
        productSearch.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                lastQuery = query

                // Отменяем прошлый поиск
                searchJob?.cancel()

                if (query.length < 2) {
                    adapter.clear()
                    adapter.notifyDataSetChanged()
                    selectedProduct = null
                    recalcNutrition()
                    searchProgress.visibility = View.GONE
                    return
                }

                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(500) // ждём пока пользователь перестанет печатать
                    if (!isActive) return@launch
                    startSearch(query)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // ===== Обработчик выбора продукта из списка =====
        productSearch.setOnItemClickListener { _, _, position, _ ->
            val name = adapter.getItem(position) ?: return@setOnItemClickListener
            selectedProduct = currentProducts.firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                        it.name.contains(name, ignoreCase = true) ||
                        name.contains(it.name, ignoreCase = true)
            }
            Log.d("FOOD_SEARCH", "SELECTED = ${selectedProduct?.name}")
            recalcNutrition()
        }

        // ===== TextWatcher для количества (ТОЛЬКО ПЕРЕСЧЕТ!) =====
        quantityEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // 🔥 ВОТ ЗДЕСЬ БЫЛА ОШИБКА - теперь просто пересчитываем
                recalcNutrition()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // ===== Кнопка "Добавить" =====
        btnAdd.setOnClickListener {
            addProductToMeal()
        }

        // ===== Кнопка "Отмена" =====
        btnCancel.setOnClickListener {
            searchJob?.cancel()
            parentFragmentManager.popBackStack()
        }
    }

    private fun startSearch(query: String) {

        val q = query.trim().lowercase()
        if (q.length < 2) return

        val requestId = ++searchRequestId

        searchProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                Log.d("AddMealFragment", "Поиск: '$q'")

                // 🔥 1. LOCAL FAST FIRST
                val localResults = withContext(Dispatchers.IO) {
                    db.getAllProductsMatching(q)
                }

                if (requestId != searchRequestId) return@launch

                updateAdapterUI(localResults)

                // 🔥 2. CACHE CHECK (мгновенно)
                val cached = ApiManager.getCachedSearch(q)
                if (cached != null) {
                    if (requestId == searchRequestId) {
                        updateAdapterUI((localResults + cached).distinctBy { it.name.lowercase() })
                    }
                    searchProgress.visibility = View.GONE
                    return@launch
                }

                // 🔥 3. API CALL (с защитой от зависаний)
                val apiResults = withContext(Dispatchers.IO) {
                    try {
                        withTimeout(3000) {
                            ApiManager.searchProducts(q)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                // ❗ если пользователь уже ввёл другое — игнор
                if (requestId != searchRequestId) return@launch

                val combined = (localResults + apiResults)
                    .distinctBy { it.name.lowercase() }
                    .take(20)

                updateAdapterUI(combined)

                searchProgress.visibility = View.GONE

            } catch (e: CancellationException) {
                Log.d("FOOD_SEARCH", "cancelled")
            } catch (e: Exception) {
                Log.e("AddMealFragment", "Ошибка поиска", e)
                searchProgress.visibility = View.GONE
            }
        }
    }

    private fun updateAdapterUI(products: List<Product>) {
        if (!isAdded) return
        if (products.isEmpty()) return

        currentProducts = products

        val query = productSearch.text.toString().lowercase()

        val sorted = products.sortedBy { product ->
            val name = product.name.lowercase()
            when {
                name == query -> 0
                name.startsWith(query) -> 1
                name.contains(query) -> 2
                else -> 3
            }
        }.take(15)

        adapter.clear()
        adapter.addAll(sorted.map { it.name })
        adapter.notifyDataSetChanged()

        if (!productSearch.isPopupShowing && adapter.count > 0) {
            productSearch.showDropDown()
        }
    }

    private fun addProductToMeal() {

        val product = selectedProduct

        if (product == null) {
            Toast.makeText(
                requireContext(),
                "Выберите продукт",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val quantity =
            quantityEdit.text.toString()
                .toIntOrNull() ?: 100

        if (quantity <= 0) {
            Toast.makeText(
                requireContext(),
                "Введите количество",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val prefs = requireContext()
                    .getSharedPreferences(
                        "session",
                        Context.MODE_PRIVATE
                    )

                val serverUserId =
                    prefs.getLong(
                        "server_user_id",
                        -1L
                    )

                if (serverUserId == -1L) {

                    Toast.makeText(
                        requireContext(),
                        "Пользователь не авторизован",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                val factor = quantity / 100f

                val dto = MealDto(
                    userId = serverUserId,

                    productName = product.name,

                    quantity = quantity,

                    calories =
                        (product.calories * factor).toInt(),

                    protein =
                        (product.protein * factor).toInt(),

                    fat =
                        (product.fat * factor).toInt(),

                    carbs =
                        (product.carbs * factor).toInt(),

                    mealType = mealName,

                    date =
                        System.currentTimeMillis()
                )

                val success =
                    withContext(Dispatchers.IO) {
                        ApiManager.addMeal(dto)
                    }

                if (success) {

                    Toast.makeText(
                        requireContext(),
                        "Продукт добавлен",
                        Toast.LENGTH_SHORT
                    ).show()

                    parentFragmentManager
                        .setFragmentResult(
                            "meal_added",
                            Bundle.EMPTY
                        )

                    parentFragmentManager
                        .popBackStack()

                } else {

                    Toast.makeText(
                        requireContext(),
                        "Ошибка сервера",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {

                Log.e(
                    "AddMealFragment",
                    "ADD MEAL ERROR",
                    e
                )

                Toast.makeText(
                    requireContext(),
                    "Ошибка: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun recalcNutrition() {
        val product = selectedProduct

        // 🔥 БЕЗОПАСНОЕ ПОЛУЧЕНИЕ КОЛИЧЕСТВА
        val quantityText = quantityEdit.text.toString().trim()
        val quantity = if (quantityText.isEmpty()) {
            100 // если поле пустое - используем 100 грамм
        } else {
            quantityText.toIntOrNull() ?: 100
        }

        val factor = quantity / 100f

        val calories = ((product?.calories ?: 0f) * factor).toInt()
        val protein = ((product?.protein ?: 0f) * factor).toInt()
        val fat = ((product?.fat ?: 0f) * factor).toInt()
        val carbs = ((product?.carbs ?: 0f) * factor).toInt()

        caloriesTv.text = calories.toString()
        proteinTv.text = "$protein г"
        fatTv.text = "$fat г"
        carbsTv.text = "$carbs г"
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