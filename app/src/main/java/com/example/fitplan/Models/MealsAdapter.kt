package com.example.fitplan.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.fitplan.Models.MealProductDisplay
import com.example.fitplan.R
import com.example.fitplan.ui.models.DisplayMeal

class MealsAdapter(
    private val meals: MutableList<DisplayMeal>,
    private val onAddClick: (String) -> Unit,
    private val onMealClick: (String) -> Unit,
    private val onEditProduct: (MealProductDisplay, String) -> Unit,
    private val onDeleteProduct: (MealProductDisplay, String) -> Unit
) : RecyclerView.Adapter<MealsAdapter.MealVH>() {

    private val expandedMeals = mutableSetOf<String>()
    private val mealProductsMap = mutableMapOf<String, List<MealProductDisplay>>()

    inner class MealVH(v: View) : RecyclerView.ViewHolder(v) {
        private val cardRoot: View = v.findViewById(R.id.mealCardRoot)
        private val tvMealName: TextView = v.findViewById(R.id.tvMealName)
        private val tvMealCalories: TextView = v.findViewById(R.id.tvMealCalories)
        private val tvMealDescription: TextView = v.findViewById(R.id.tvMealDescription)
        private val btnAddMealItem: Button = v.findViewById(R.id.btnAddMealItem)
        private val productsContainer: LinearLayout = v.findViewById(R.id.productsContainer)
        private val arrowIcon: TextView = v.findViewById(R.id.arrowIcon)
        private val topRowLayout: LinearLayout = v.findViewById(R.id.topRowLayout)

        fun bind(meal: DisplayMeal) {
            arrowIcon.text = if (expandedMeals.contains(meal.mealType)) "▼" else "▶"
            tvMealName.text = meal.mealType
            tvMealCalories.text = "${meal.totalCalories}"

            // Обновляем описание в зависимости от наличия продуктов
            if (meal.productCount > 0) {
                tvMealDescription.text = "${meal.productCount} продукт(ов) • ${meal.totalCalories} ккал"
                tvMealCalories.setTextColor(itemView.context.resources.getColor(android.R.color.holo_green_dark, null))
                tvMealDescription.setTextColor(itemView.context.resources.getColor(android.R.color.darker_gray, null))
            } else {
                tvMealDescription.text = "Нет добавленных продуктов"
                tvMealCalories.setTextColor(itemView.context.resources.getColor(android.R.color.darker_gray, null))
                tvMealDescription.setTextColor(itemView.context.resources.getColor(android.R.color.darker_gray, null))
            }

            // Показываем/скрываем контейнер с продуктами
            productsContainer.visibility = if (expandedMeals.contains(meal.mealType)) View.VISIBLE else View.GONE

            // Заполняем контейнер продуктами, если он раскрыт
            if (expandedMeals.contains(meal.mealType)) {
                val products = mealProductsMap[meal.mealType] ?: emptyList()
                fillProductsContainer(products, meal.mealType)
            }

            // Обработка клика на верхнюю строку (стрелочка + название + калории)
            topRowLayout.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleExpansion(meal.mealType)
                }
            }

            // Обработка клика на карточку (кроме верхней строки и кнопки)
            cardRoot.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleExpansion(meal.mealType)
                }
            }

            // Обработка клика на стрелочку
            arrowIcon.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleExpansion(meal.mealType)
                }
            }

            btnAddMealItem.setOnClickListener {
                onAddClick(meal.mealType)
            }
        }

        private fun toggleExpansion(mealType: String) {
            val position = meals.indexOfFirst { it.mealType == mealType }
            if (position != -1) {
                if (expandedMeals.contains(mealType)) {
                    expandedMeals.remove(mealType)
                } else {
                    expandedMeals.add(mealType)
                    onMealClick(mealType)
                }
                notifyItemChanged(position)
            }
        }

        private fun fillProductsContainer(products: List<MealProductDisplay>, mealType: String) {
            productsContainer.removeAllViews()

            if (products.isEmpty()) {
                val textView = TextView(itemView.context).apply {
                    text = "Нет продуктов"
                    setPadding(16, 16, 16, 16)
                    textSize = 14f
                    gravity = View.TEXT_ALIGNMENT_CENTER
                    setTextColor(itemView.context.resources.getColor(android.R.color.darker_gray, null))
                }
                productsContainer.addView(textView)
                return
            }

            // Используем product_item.xml для каждого продукта
            products.forEach { product ->
                val productView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.card_product, null) // Исправлено на product_item

                bindProductView(productView, product, mealType)
                productsContainer.addView(productView)
            }
        }

        private fun bindProductView(productView: View, product: MealProductDisplay, mealType: String) {
            val tvProductName: TextView = productView.findViewById(R.id.tvProductName)
            val tvPortionSize: TextView = productView.findViewById(R.id.tvPortionSize)
            val tvProductCalories: TextView = productView.findViewById(R.id.tvProductCalories)
            val tvProteinAmount: TextView = productView.findViewById(R.id.tvProteinAmount)
            val tvFatAmount: TextView = productView.findViewById(R.id.tvFatAmount)
            val tvCarbsAmount: TextView = productView.findViewById(R.id.tvCarbsAmount)
            val btnDeleteProduct: TextView = productView.findViewById(R.id.btnDeleteProduct)
            val btnEditProduct: Button = productView.findViewById(R.id.btnEditProduct)

            // Устанавливаем данные продукта
            tvProductName.text = product.productName
            tvPortionSize.text = "${product.quantity} г"
            tvProductCalories.text = product.calories.toInt().toString()
            tvProteinAmount.text = product.protein.toInt().toString()
            tvFatAmount.text = product.fat.toInt().toString()
            tvCarbsAmount.text = product.carbs.toInt().toString()

            // Обработчики для кнопок удаления и редактирования
            btnDeleteProduct.setOnClickListener {
                onDeleteProduct(product, mealType)
            }

            btnEditProduct.setOnClickListener {
                onEditProduct(product, mealType)
            }

            // Клик на карточку продукта тоже вызывает редактирование
            productView.findViewById<View>(R.id.productCardRoot).setOnClickListener {
                onEditProduct(product, mealType)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal, parent, false)
        return MealVH(view)
    }

    override fun getItemCount() = meals.size

    override fun onBindViewHolder(holder: MealVH, position: Int) {
        holder.bind(meals[position])
    }

    fun updateData(newMeals: List<DisplayMeal>) {
        meals.clear()
        meals.addAll(newMeals)
        notifyDataSetChanged()
    }

    fun updateMealProducts(mealType: String, products: List<MealProductDisplay>) {
        mealProductsMap[mealType] = products

        // Если прием пищи уже раскрыт, обновляем его
        if (expandedMeals.contains(mealType)) {
            val position = meals.indexOfFirst { it.mealType == mealType }
            if (position != -1) {
                notifyItemChanged(position)
            }
        }
    }

    fun isMealExpanded(mealType: String): Boolean {
        return expandedMeals.contains(mealType)
    }

    // Добавляем метод для ручного переключения раскрытия
    fun toggleMealExpansion(mealType: String) {
        if (expandedMeals.contains(mealType)) {
            expandedMeals.remove(mealType)
        } else {
            expandedMeals.add(mealType)
        }

        val position = meals.indexOfFirst { it.mealType == mealType }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }
}