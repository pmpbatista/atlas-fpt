package com.spendtrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendtrack.data.db.dao.CategoryDao
import com.spendtrack.data.db.dao.LabelDao
import com.spendtrack.data.db.dao.RecurringRuleDao
import com.spendtrack.data.db.dao.TransactionDao
import com.spendtrack.data.db.entity.CategoryEntity
import com.spendtrack.data.db.entity.LabelEntity
import com.spendtrack.data.db.entity.RecurringRuleEntity
import com.spendtrack.data.db.entity.TransactionEntity
import com.spendtrack.data.db.entity.PersonEntity
import com.spendtrack.data.db.entity.TransactionLabelCrossRef
import com.spendtrack.data.db.entity.TransactionPersonCrossRef
import com.spendtrack.domain.model.CategoryType

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        LabelEntity::class,
        TransactionLabelCrossRef::class,
        RecurringRuleEntity::class,
        PersonEntity::class,
        TransactionPersonCrossRef::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun labelDao(): LabelDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendtrack.db")
                .addCallback(SeedCallback())
                .build()
    }
}

private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Room calls onCreate on a background thread; execSQL is synchronous — no coroutine needed
        seedCategories(db)
    }

    private fun seedCategories(db: SupportSQLiteDatabase) {
        val expenseCategories = listOf(
            Triple("Compras", "shopping_cart", 0xFF4CAF50.toInt()),
            Triple("Restaurantes", "restaurant", 0xFFF44336.toInt()),
            Triple("Saúde", "health_and_safety", 0xFF2196F3.toInt()),
            Triple("Desporto", "sports", 0xFF00BCD4.toInt()),
            Triple("Contas", "receipt_long", 0xFF9C27B0.toInt()),
            Triple("Transportes", "directions_bus", 0xFFFF9800.toInt()),
            Triple("Empréstimo", "account_balance", 0xFF795548.toInt()),
            Triple("Escolas", "school", 0xFF3F51B5.toInt()),
            Triple("Carro", "directions_car", 0xFF607D8B.toInt()),
            Triple("Outros", "category", 0xFF9E9E9E.toInt()),
        )
        val incomeCategories = listOf(
            Triple("Salário", "payments", 0xFF4CAF50.toInt()),
            Triple("Reembolso", "currency_exchange", 0xFF2196F3.toInt()),
            Triple("Rendas", "home", 0xFFFF9800.toInt()),
            Triple("Outros (Receita)", "add_circle", 0xFF9E9E9E.toInt()),
        )

        expenseCategories.forEach { (name, icon, color) ->
            db.execSQL(
                "INSERT INTO categories (name, iconRes, color, type) VALUES (?, ?, ?, ?)",
                arrayOf(name, icon, color, CategoryType.EXPENSE.name)
            )
        }
        incomeCategories.forEach { (name, icon, color) ->
            db.execSQL(
                "INSERT INTO categories (name, iconRes, color, type) VALUES (?, ?, ?, ?)",
                arrayOf(name, icon, color, CategoryType.INCOME.name)
            )
        }
    }
}
