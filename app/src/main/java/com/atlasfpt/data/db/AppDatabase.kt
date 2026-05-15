package com.atlasfpt.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.atlasfpt.data.db.dao.AssetDao
import com.atlasfpt.data.db.dao.CategoryDao
import com.atlasfpt.data.db.dao.FinancialDao
import com.atlasfpt.data.db.dao.FxRateDao
import com.atlasfpt.data.db.dao.LabelDao
import com.atlasfpt.data.db.dao.PersonDao
import com.atlasfpt.data.db.dao.RealEstateDao
import com.atlasfpt.data.db.dao.RecurringRuleDao
import com.atlasfpt.data.db.dao.TransactionDao
import com.atlasfpt.data.db.entity.AssetEntity
import com.atlasfpt.data.db.entity.CategoryEntity
import com.atlasfpt.data.db.entity.FinancialHoldingEntity
import com.atlasfpt.data.db.entity.FinancialLotEntity
import com.atlasfpt.data.db.entity.FxRateEntity
import com.atlasfpt.data.db.entity.LabelEntity
import com.atlasfpt.data.db.entity.PersonEntity
import com.atlasfpt.data.db.entity.RealEstateDetailsEntity
import com.atlasfpt.data.db.entity.RecurringRuleEntity
import com.atlasfpt.data.db.entity.TransactionEntity
import com.atlasfpt.data.db.entity.TransactionLabelCrossRef
import com.atlasfpt.data.db.entity.TransactionPersonCrossRef
import com.atlasfpt.domain.model.CategoryType

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
        FinancialHoldingEntity::class,
        FinancialLotEntity::class,
        FxRateEntity::class,
    ],
    version = 7,
    exportSchema = true
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
    abstract fun financialDao(): FinancialDao
    abstract fun fxRateDao(): FxRateDao

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
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .build()
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `fx_rates` (`currencyCode` TEXT NOT NULL, `unitsPerEur` REAL NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`currencyCode`))")
    }
}

// Adds the nullable `assetId` FK on `transactions` so a transaction can be linked
// to an asset (e.g. a mortgage payment on a property, a deposit on a brokerage).
// ON DELETE SET NULL so removing an asset does not lose transaction history.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `transactions` ADD COLUMN `assetId` INTEGER REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_assetId` ON `transactions` (`assetId`)"
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `real_estate_details` ADD COLUMN `photoUri` TEXT")
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

// SQL strings copied verbatim from Room's generated AppDatabase_Impl.createAllTables.
// See real-estate spec's MIGRATION_2_3 fix-commit (0114e64) for the identity-hash lesson —
// these MUST match Room's text byte-for-byte.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `financial_holdings` (`assetId` INTEGER NOT NULL, `ticker` TEXT NOT NULL, `displayName` TEXT NOT NULL, `latestPrice` REAL, `latestPriceAt` INTEGER, PRIMARY KEY(`assetId`), FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `financial_lots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `assetId` INTEGER NOT NULL, `purchaseDate` TEXT NOT NULL, `quantity` REAL NOT NULL, `pricePerUnit` REAL NOT NULL, FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_lots_assetId` ON `financial_lots` (`assetId`)")
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
