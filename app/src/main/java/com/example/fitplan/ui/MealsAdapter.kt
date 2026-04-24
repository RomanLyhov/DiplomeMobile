package com.example.fitplan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.fitplan.Models.Meal
import com.example.fitplan.R

// Адаптер для отображения списка приемов пищи в RecyclerView
class MealsAdapter(
    private var meals: List<Meal>,
    private val onAddClick: (mealType: String) -> Unit
) : RecyclerView.Adapter<MealsAdapter.MealViewHolder>() {

    // ViewHolder для хранения ссылок на элементы интерфейса одного элемента списка
    inner class MealViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMealName: TextView = view.findViewById(R.id.tvMealName)
        val btnAddMeal: Button = view.findViewById(R.id.btnAddMeal)
        val tvCalories: TextView = view.findViewById(R.id.tvMealCalories)
    }

    // Создание нового ViewHolder при необходимости
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal, parent, false)
        return MealViewHolder(view)
    }

    // Возвращает количество элементов в списке
    override fun getItemCount(): Int = meals.size

    // Привязка данных к ViewHolder на указанной позиции
    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        val meal = meals[position]

        // Установка названия приема пищи
        holder.tvMealName.text = meal.mealType ?: meal.productName

        // Установка количества калорий
        holder.tvCalories.text = "${meal.calories} kcal"

        // Установка обработчика клика на кнопку добавления
        holder.btnAddMeal.setOnClickListener {
            onAddClick(meal.mealType ?: "")
        }
    }

    // Обновление данных в адаптере новым списком приемов пищи
    fun updateData(newMeals: List<Meal>) {
        meals = newMeals
        notifyDataSetChanged()
    }
}