package com.spendtrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendtrack.data.db.dao.AssetDao
import com.spendtrack.data.db.dao.CategoryDao
import com.spendtrack.data.db.dao.LabelDao
import com.spendtrack.data.db.dao.PersonDao
import com.spendtrack.data.db.dao.RealEstateDao
import com.spendtrack.data.db.dao.RecurringRuleDao
import com.spendtrack.data.db.dao.TransactionDao
import com.spendtrack.data.db.entity.AssetEntity
import com.spendtrack.data.db.entity.CategoryEntity
import com.spendtrack.data.db.entity.LabelEntity
import com.spendtrack.data.db.entity.PersonEntity
import com.spendtrack.data.db.entity.RealEstateDetailsEntity
import com.spendtrack.data.db.entity.RecurringRuleEntity
import com.spendtrack.data.db.entity.TransactionEntity
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
        AssetEntity::class,
        RealEstateDetailsEntity::class,
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun labelDao(): LabelDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun personDao(): PersonDao
    abstract fun assetDao(): AssetDao
    abstract fun realEstateDao(): RealEstateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `persons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transaction_person_cross_ref` (`transactionId` INTEGER NOT NULL, `personId` INTEGER NOT NULL, PRIMARY KEY(`transactionId`, `personId`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transaction_person_cross_ref_personId` ON `transaction_person_cross_ref` (`personId`)"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendtrack.db")
                .addCallback(SeedCallback())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

// SQL strings copied verbatim from Room's generated AppDatabase_Impl.createAllTables.
// Room's runtime identity-hash check rejects any deviation (e.g. inline `INTEGER PRIMARY KEY`
// vs trailing `PRIMARY KEY(col)`) — keep these in sync if entities change.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `currentValue` REAL NOT NULL, `currentValueUpdatedAt` INTEGER NOT NULL, `purchaseDate` TEXT, `notes` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `real_estate_details` (`assetId` INTEGER NOT NULL, `cost` REAL NOT NULL, `investedCapital` REAL NOT NULL, `debtAmount` REAL, `outstandingDebt` REAL, `interestType` TEXT, `fixedRate` REAL, `referenceRate` TEXT, `spread` REAL, `creditEndDate` TEXT, `district` TEXT NOT NULL, `council` TEXT NOT NULL, `parish` TEXT NOT NULL, `sizeM2` REAL NOT NULL, `energyRating` TEXT NOT NULL, PRIMARY KEY(`assetId`), FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_real_estate_details_assetId` ON `real_estate_details` (`assetId`)")
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
