# Persons Linking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to link one or more named persons to any expense or income entry, manage persons in a dedicated Settings sub-screen, and show/edit the linked persons in the Add/Edit Transaction screen.

**Architecture:** Mirrors the existing Labels many-to-many pattern exactly — a `persons` table + `transaction_person_cross_ref` table, a `@Relation` via `Junction` in `TransactionWithDetails`, and person cross-refs saved/deleted in `TransactionRepository.save()`. The DB migrates from version 1 to 2. A new `PersonsScreen` (Settings sub-screen) provides full CRUD. The Add/Edit Transaction screen gains a `PersonPickerBottomSheet` and person chips.

**Tech Stack:** Kotlin 2.0.21, Room 2.6.1, Hilt 2.51.1, Jetpack Compose + Material 3, JUnit 4 + MockK + Turbine

---

## File Map

| File | Action |
|---|---|
| `domain/model/Person.kt` | **create** |
| `domain/model/Transaction.kt` | modify — add `persons` field |
| `data/db/entity/PersonEntity.kt` | **create** |
| `data/db/entity/TransactionPersonCrossRef.kt` | **create** |
| `data/db/entity/TransactionWithDetails.kt` | modify — add persons `@Relation` |
| `data/db/entity/Mappers.kt` | modify — add Person mappers, update `toDomain()` |
| `data/db/dao/PersonDao.kt` | **create** |
| `data/db/dao/TransactionDao.kt` | modify — add person cross-ref methods |
| `data/repository/PersonRepository.kt` | **create** |
| `data/repository/TransactionRepository.kt` | modify — save person cross-refs |
| `data/db/AppDatabase.kt` | modify — version 2, new entities, migration, personDao |
| `di/DatabaseModule.kt` | modify — add `providePersonDao` |
| `data/importer/CsvImporter.kt` | modify — add `persons = emptyList()` to Transaction constructor |
| `ui/component/LabelChip.kt` | modify — add optional color params |
| `ui/component/PersonChip.kt` | **create** |
| `ui/component/PersonPickerBottomSheet.kt` | **create** |
| `ui/feature/addtransaction/AddTransactionViewModel.kt` | modify — inject PersonRepository, new persons handlers |
| `ui/feature/addtransaction/AddTransactionScreen.kt` | modify — person chips + row + bottom sheet |
| `ui/feature/persons/PersonsViewModel.kt` | **create** |
| `ui/feature/persons/PersonsScreen.kt` | **create** |
| `ui/feature/settings/SettingsScreen.kt` | modify — add Persons row |
| `ui/navigation/AppNavGraph.kt` | modify — add `Screen.Persons`, composable |
| `test/.../PersonMappersTest.kt` | **create** |
| `test/.../PersonRepositoryTest.kt` | **create** |
| `test/.../TransactionRepositoryPersonsTest.kt` | **create** |
| `test/.../AddTransactionViewModelDeleteTest.kt` | modify — add `personRepository` param, `persons` field |
| `test/.../AddTransactionViewModelPersonsTest.kt` | **create** |
| `test/.../PersonsViewModelTest.kt` | **create** |

All paths are relative to `app/src/main/java/com/spendtrack/` (source) or `app/src/test/java/com/spendtrack/` (test).

---

## Task 1: Domain model, entities, and mappers

**Files:**
- Create: `app/src/main/java/com/spendtrack/domain/model/Person.kt`
- Modify: `app/src/main/java/com/spendtrack/domain/model/Transaction.kt`
- Create: `app/src/main/java/com/spendtrack/data/db/entity/PersonEntity.kt`
- Create: `app/src/main/java/com/spendtrack/data/db/entity/TransactionPersonCrossRef.kt`
- Modify: `app/src/main/java/com/spendtrack/data/db/entity/TransactionWithDetails.kt`
- Modify: `app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt`
- Modify: `app/src/main/java/com/spendtrack/data/importer/CsvImporter.kt`
- Test: `app/src/test/java/com/spendtrack/data/db/entity/PersonMappersTest.kt`

- [ ] **Step 1: Create `Person.kt`**

```kotlin
// app/src/main/java/com/spendtrack/domain/model/Person.kt
package com.spendtrack.domain.model

data class Person(val id: Long, val name: String)
```

- [ ] **Step 2: Add `persons` field to `Transaction.kt`**

Replace the existing `Transaction` data class (file is at `app/src/main/java/com/spendtrack/domain/model/Transaction.kt`):

```kotlin
package com.spendtrack.domain.model

import java.time.LocalDate

data class Transaction(
    val id: Long,
    val amount: Double,
    val type: TransactionType,
    val category: Category,
    val date: LocalDate,
    val note: String?,
    val photoUri: String?,
    val labels: List<Label>,
    val persons: List<Person>,
    val recurringRuleId: Long?,
    val isScheduled: Boolean
)
```

- [ ] **Step 3: Create `PersonEntity.kt`**

```kotlin
// app/src/main/java/com/spendtrack/data/db/entity/PersonEntity.kt
package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
```

- [ ] **Step 4: Create `TransactionPersonCrossRef.kt`**

```kotlin
// app/src/main/java/com/spendtrack/data/db/entity/TransactionPersonCrossRef.kt
package com.spendtrack.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "transaction_person_cross_ref",
    primaryKeys = ["transactionId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId")]
)
data class TransactionPersonCrossRef(
    val transactionId: Long,
    val personId: Long
)
```

- [ ] **Step 5: Add persons `@Relation` to `TransactionWithDetails.kt`**

```kotlin
// app/src/main/java/com/spendtrack/data/db/entity/TransactionWithDetails.kt
package com.spendtrack.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TransactionWithDetails(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: CategoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionLabelCrossRef::class,
            parentColumn = "transactionId",
            entityColumn = "labelId"
        )
    )
    val labels: List<LabelEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TransactionPersonCrossRef::class,
            parentColumn = "transactionId",
            entityColumn = "personId"
        )
    )
    val persons: List<PersonEntity>
)
```

- [ ] **Step 6: Update `Mappers.kt`** — add Person mappers and update `toDomain()`

```kotlin
// app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt
package com.spendtrack.data.db.entity

import com.spendtrack.domain.model.Category
import com.spendtrack.domain.model.Label
import com.spendtrack.domain.model.Person
import com.spendtrack.domain.model.RecurringRule
import com.spendtrack.domain.model.Transaction

fun TransactionWithDetails.toDomain(): Transaction = Transaction(
    id = transaction.id,
    amount = transaction.amount,
    type = transaction.type,
    category = category.toDomain(),
    date = transaction.date,
    note = transaction.note,
    photoUri = transaction.photoUri,
    labels = labels.map { it.toDomain() },
    persons = persons.map { it.toDomain() },
    recurringRuleId = transaction.recurringRuleId,
    isScheduled = transaction.isScheduled
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    iconRes = iconRes,
    color = color,
    type = type
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    iconRes = iconRes,
    color = color,
    type = type
)

fun LabelEntity.toDomain(): Label = Label(id = id, name = name)

fun Label.toEntity(): LabelEntity = LabelEntity(id = id, name = name)

fun PersonEntity.toDomain(): Person = Person(id = id, name = name)

fun Person.toEntity(): PersonEntity = PersonEntity(id = id, name = name)

fun RecurringRuleEntity.toDomain(): RecurringRule = RecurringRule(
    id = id,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
    nextTriggerDate = nextTriggerDate
)

fun RecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
    nextTriggerDate = nextTriggerDate
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    amount = amount,
    type = type,
    categoryId = category.id,
    date = date,
    note = note,
    photoUri = photoUri,
    recurringRuleId = recurringRuleId,
    isScheduled = isScheduled
)
```

- [ ] **Step 7: Fix `CsvImporter.kt`** — add `persons = emptyList()` to the `Transaction` constructor call (line ~93)

Change the `Transaction(...)` block inside `import()` to:

```kotlin
val tx = Transaction(
    id = 0,
    amount = amount,
    type = type,
    category = category,
    date = date,
    note = note,
    photoUri = null,
    labels = emptyList(),
    persons = emptyList(),
    recurringRuleId = null,
    isScheduled = false
)
```

- [ ] **Step 8: Write mapper tests**

```kotlin
// app/src/test/java/com/spendtrack/data/db/entity/PersonMappersTest.kt
package com.spendtrack.data.db.entity

import com.spendtrack.domain.model.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonMappersTest {

    @Test
    fun `PersonEntity toDomain maps id and name`() {
        val entity = PersonEntity(id = 1L, name = "João")
        assertEquals(Person(id = 1L, name = "João"), entity.toDomain())
    }

    @Test
    fun `Person toEntity maps id and name`() {
        val domain = Person(id = 2L, name = "Maria")
        assertEquals(PersonEntity(id = 2L, name = "Maria"), domain.toEntity())
    }

    @Test
    fun `round-trip toEntity then toDomain preserves values`() {
        val original = Person(id = 3L, name = "Pedro")
        assertEquals(original, original.toEntity().toDomain())
    }
}
```

- [ ] **Step 9: Run mapper tests**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.data.db.entity.PersonMappersTest"
```

Expected: 3 tests pass.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/spendtrack/domain/model/Person.kt \
        app/src/main/java/com/spendtrack/domain/model/Transaction.kt \
        app/src/main/java/com/spendtrack/data/db/entity/PersonEntity.kt \
        app/src/main/java/com/spendtrack/data/db/entity/TransactionPersonCrossRef.kt \
        app/src/main/java/com/spendtrack/data/db/entity/TransactionWithDetails.kt \
        app/src/main/java/com/spendtrack/data/db/entity/Mappers.kt \
        app/src/main/java/com/spendtrack/data/importer/CsvImporter.kt \
        app/src/test/java/com/spendtrack/data/db/entity/PersonMappersTest.kt
git commit -m "feat: add Person domain model, entities, and mappers"
```

---

## Task 2: PersonDao, DB migration, and AppDatabase

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/db/dao/PersonDao.kt`
- Modify: `app/src/main/java/com/spendtrack/data/db/AppDatabase.kt`

- [ ] **Step 1: Create `PersonDao.kt`**

```kotlin
// app/src/main/java/com/spendtrack/data/db/dao/PersonDao.kt
package com.spendtrack.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spendtrack.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(person: PersonEntity): Long

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("SELECT COUNT(*) FROM transaction_person_cross_ref WHERE personId = :personId")
    suspend fun countTransactions(personId: Long): Int
}
```

- [ ] **Step 2: Update `AppDatabase.kt`** — bump to version 2, register new entities, add migration, add `personDao()`

```kotlin
// app/src/main/java/com/spendtrack/data/db/AppDatabase.kt
package com.spendtrack.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendtrack.data.db.dao.CategoryDao
import com.spendtrack.data.db.dao.LabelDao
import com.spendtrack.data.db.dao.PersonDao
import com.spendtrack.data.db.dao.RecurringRuleDao
import com.spendtrack.data.db.dao.TransactionDao
import com.spendtrack.data.db.entity.CategoryEntity
import com.spendtrack.data.db.entity.LabelEntity
import com.spendtrack.data.db.entity.PersonEntity
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
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun labelDao(): LabelDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun personDao(): PersonDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `persons` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `transaction_person_cross_ref` (
                        `transactionId` INTEGER NOT NULL,
                        `personId` INTEGER NOT NULL,
                        PRIMARY KEY(`transactionId`, `personId`),
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON DELETE CASCADE
                    )"""
                )
                db.execSQL(
                    """CREATE INDEX IF NOT EXISTS `index_transaction_person_cross_ref_personId`
                       ON `transaction_person_cross_ref` (`personId`)"""
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendtrack.db")
                .addCallback(SeedCallback())
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
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
```

- [ ] **Step 3: Verify the project compiles**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL (no errors).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/dao/PersonDao.kt \
        app/src/main/java/com/spendtrack/data/db/AppDatabase.kt
git commit -m "feat: add PersonDao and DB migration 1→2"
```

---

## Task 3: PersonRepository and DI

**Files:**
- Create: `app/src/main/java/com/spendtrack/data/repository/PersonRepository.kt`
- Modify: `app/src/main/java/com/spendtrack/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/spendtrack/data/repository/PersonRepositoryTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/spendtrack/data/repository/PersonRepositoryTest.kt
package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.PersonDao
import com.spendtrack.data.db.entity.PersonEntity
import com.spendtrack.domain.model.Person
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonRepositoryTest {

    private val dao: PersonDao = mockk()
    private val repo = PersonRepository(dao)

    @Test
    fun `save calls dao insert and returns id`() = runTest {
        coEvery { dao.insert(PersonEntity(id = 0, name = "João")) } returns 1L
        val result = repo.save(Person(id = 0, name = "João"))
        assertEquals(1L, result)
        coVerify { dao.insert(PersonEntity(id = 0, name = "João")) }
    }

    @Test
    fun `delete calls dao delete`() = runTest {
        coEvery { dao.delete(PersonEntity(id = 1L, name = "João")) } just Runs
        repo.delete(Person(id = 1L, name = "João"))
        coVerify { dao.delete(PersonEntity(id = 1L, name = "João")) }
    }

    @Test
    fun `countTransactions delegates to dao`() = runTest {
        coEvery { dao.countTransactions(1L) } returns 5
        assertEquals(5, repo.countTransactions(1L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.data.repository.PersonRepositoryTest"
```

Expected: FAIL — `PersonRepository` does not exist yet.

- [ ] **Step 3: Create `PersonRepository.kt`**

```kotlin
// app/src/main/java/com/spendtrack/data/repository/PersonRepository.kt
package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.PersonDao
import com.spendtrack.data.db.entity.toDomain
import com.spendtrack.data.db.entity.toEntity
import com.spendtrack.domain.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepository @Inject constructor(private val dao: PersonDao) {

    fun observeAll(): Flow<List<Person>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(person: Person): Long = withContext(Dispatchers.IO) {
        dao.insert(person.toEntity())
    }

    suspend fun delete(person: Person) = withContext(Dispatchers.IO) {
        dao.delete(person.toEntity())
    }

    suspend fun countTransactions(personId: Long): Int = withContext(Dispatchers.IO) {
        dao.countTransactions(personId)
    }
}
```

- [ ] **Step 4: Add `providePersonDao` to `DatabaseModule.kt`**

Add this method inside the `DatabaseModule` object, after `provideRecurringRuleDao`:

```kotlin
@Provides
fun providePersonDao(db: AppDatabase): PersonDao = db.personDao()
```

Also add to imports at top of the file:
```kotlin
import com.spendtrack.data.db.dao.PersonDao
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.data.repository.PersonRepositoryTest"
```

Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/repository/PersonRepository.kt \
        app/src/main/java/com/spendtrack/di/DatabaseModule.kt \
        app/src/test/java/com/spendtrack/data/repository/PersonRepositoryTest.kt
git commit -m "feat: add PersonRepository and DI wiring"
```

---

## Task 4: TransactionDao and TransactionRepository persons support

**Files:**
- Modify: `app/src/main/java/com/spendtrack/data/db/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/spendtrack/data/repository/TransactionRepository.kt`
- Test: `app/src/test/java/com/spendtrack/data/repository/TransactionRepositoryPersonsTest.kt`

- [ ] **Step 1: Write a failing test**

```kotlin
// app/src/test/java/com/spendtrack/data/repository/TransactionRepositoryPersonsTest.kt
package com.spendtrack.data.repository

import com.spendtrack.data.db.dao.TransactionDao
import com.spendtrack.data.db.entity.TransactionPersonCrossRef
import com.spendtrack.domain.model.Category
import com.spendtrack.domain.model.CategoryType
import com.spendtrack.domain.model.Person
import com.spendtrack.domain.model.Transaction
import com.spendtrack.domain.model.TransactionType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

class TransactionRepositoryPersonsTest {

    private val dao: TransactionDao = mockk()
    private val repo = TransactionRepository(dao)

    private val fakeCategory = Category(id = 1L, name = "Food", iconRes = "", color = 0, type = CategoryType.EXPENSE)

    private fun makeTransaction(persons: List<Person>) = Transaction(
        id = 0L,
        amount = 10.0,
        type = TransactionType.EXPENSE,
        category = fakeCategory,
        date = LocalDate.of(2026, 1, 1),
        note = null,
        photoUri = null,
        labels = emptyList(),
        persons = persons,
        recurringRuleId = null,
        isScheduled = false
    )

    @Test
    fun `save inserts person cross-refs for each person`() = runTest {
        coEvery { dao.insert(any()) } returns 42L
        coEvery { dao.deleteAllLabelsForTransaction(any()) } just Runs
        coEvery { dao.insertCrossRef(any()) } just Runs
        coEvery { dao.deleteAllPersonsForTransaction(any()) } just Runs
        coEvery { dao.insertPersonCrossRef(any()) } just Runs

        val persons = listOf(Person(id = 1L, name = "João"), Person(id = 2L, name = "Maria"))
        repo.save(makeTransaction(persons))

        coVerify { dao.deleteAllPersonsForTransaction(42L) }
        coVerify { dao.insertPersonCrossRef(TransactionPersonCrossRef(42L, 1L)) }
        coVerify { dao.insertPersonCrossRef(TransactionPersonCrossRef(42L, 2L)) }
    }

    @Test
    fun `save with no persons still calls deleteAllPersonsForTransaction`() = runTest {
        coEvery { dao.insert(any()) } returns 7L
        coEvery { dao.deleteAllLabelsForTransaction(any()) } just Runs
        coEvery { dao.deleteAllPersonsForTransaction(any()) } just Runs

        repo.save(makeTransaction(emptyList()))

        coVerify { dao.deleteAllPersonsForTransaction(7L) }
        coVerify(exactly = 0) { dao.insertPersonCrossRef(any()) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.data.repository.TransactionRepositoryPersonsTest"
```

Expected: FAIL — `deleteAllPersonsForTransaction` and `insertPersonCrossRef` do not exist yet.

- [ ] **Step 3: Add person cross-ref methods to `TransactionDao.kt`**

Add these two methods at the end of the interface, after `deleteAllLabelsForTransaction`:

```kotlin
@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertPersonCrossRef(crossRef: TransactionPersonCrossRef)

@Query("DELETE FROM transaction_person_cross_ref WHERE transactionId = :transactionId")
suspend fun deleteAllPersonsForTransaction(transactionId: Long)
```

Also add the import at the top of the file:
```kotlin
import com.spendtrack.data.db.entity.TransactionPersonCrossRef
```

- [ ] **Step 4: Update `TransactionRepository.save()`** — add persons block after the existing labels block

Replace the `save` function body in `TransactionRepository.kt`:

```kotlin
suspend fun save(transaction: Transaction): Long = withContext(Dispatchers.IO) {
    val entity = transaction.toEntity()
    val id = if (entity.id == 0L) dao.insert(entity) else {
        dao.update(entity)
        entity.id
    }
    dao.deleteAllLabelsForTransaction(id)
    transaction.labels.forEach { label ->
        dao.insertCrossRef(TransactionLabelCrossRef(id, label.id))
    }
    dao.deleteAllPersonsForTransaction(id)
    transaction.persons.forEach { person ->
        dao.insertPersonCrossRef(TransactionPersonCrossRef(id, person.id))
    }
    id
}
```

Also add the import at the top of `TransactionRepository.kt`:
```kotlin
import com.spendtrack.data.db.entity.TransactionPersonCrossRef
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.data.repository.TransactionRepositoryPersonsTest"
```

Expected: 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/spendtrack/data/db/dao/TransactionDao.kt \
        app/src/main/java/com/spendtrack/data/repository/TransactionRepository.kt \
        app/src/test/java/com/spendtrack/data/repository/TransactionRepositoryPersonsTest.kt
git commit -m "feat: save and delete person cross-refs in TransactionRepository"
```

---

## Task 5: AddTransactionViewModel persons support

**Files:**
- Modify: `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModel.kt`
- Modify: `app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelDeleteTest.kt`
- Test: `app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelPersonsTest.kt`

- [ ] **Step 1: Write failing ViewModel persons tests**

```kotlin
// app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelPersonsTest.kt
package com.spendtrack.ui.feature.addtransaction

import app.cash.turbine.test
import com.spendtrack.data.repository.CategoryRepository
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.data.repository.TransactionRepository
import com.spendtrack.data.settings.AppSettings
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.model.Person
import com.spendtrack.domain.usecase.DeleteTransactionUseCase
import com.spendtrack.domain.usecase.SaveTransactionUseCase
import com.spendtrack.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelPersonsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val saveTransaction: SaveTransactionUseCase = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val deleteTransaction: DeleteTransactionUseCase = mockk(relaxed = true)
    private val personRepository: PersonRepository = mockk()

    private lateinit var viewModel: AddTransactionViewModel

    @Before
    fun setup() {
        every { categoryRepository.observeAll() } returns flowOf(emptyList())
        every { settingsRepository.settings } returns MutableStateFlow(AppSettings())
        every { personRepository.observeAll() } returns flowOf(emptyList())
        viewModel = AddTransactionViewModel(
            saveTransaction, categoryRepository, transactionRepository,
            settingsRepository, deleteTransaction, personRepository
        )
    }

    @Test
    fun `onPersonAdded adds person to state`() = runTest {
        val person = Person(id = 1L, name = "João")
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.onPersonAdded(person)
            assertTrue(awaitItem().persons.contains(person))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onPersonAdded does not add duplicate`() = runTest {
        val person = Person(id = 1L, name = "João")
        viewModel.onPersonAdded(person)
        viewModel.onPersonAdded(person)
        assertEquals(1, viewModel.uiState.value.persons.size)
    }

    @Test
    fun `onPersonRemoved removes person from state`() = runTest {
        val person = Person(id = 1L, name = "João")
        viewModel.onPersonAdded(person)
        viewModel.uiState.test {
            awaitItem() // state with person
            viewModel.onPersonRemoved(person)
            assertTrue(awaitItem().persons.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onShowPersonPicker sets showPersonPicker true`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onShowPersonPicker()
            assertTrue(awaitItem().showPersonPicker)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onDismissPersonPicker sets showPersonPicker false`() = runTest {
        viewModel.onShowPersonPicker()
        viewModel.uiState.test {
            awaitItem() // showPersonPicker = true
            viewModel.onDismissPersonPicker()
            assertFalse(awaitItem().showPersonPicker)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 2: Run to verify the tests fail**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.ui.feature.addtransaction.AddTransactionViewModelPersonsTest"
```

Expected: FAIL — `PersonRepository` not yet in the constructor, persons handlers don't exist.

- [ ] **Step 3: Update `AddTransactionViewModelDeleteTest.kt`** — add `personRepository` to setup and `persons` to `fakeTransaction`

Change `setup()` and `fakeTransaction` in the existing test file:

```kotlin
private val personRepository: PersonRepository = mockk()

private val fakeTransaction = Transaction(
    id = 42L,
    amount = 10.0,
    type = TransactionType.EXPENSE,
    category = fakeCategory,
    date = LocalDate.of(2026, 1, 1),
    note = null,
    photoUri = null,
    labels = emptyList(),
    persons = emptyList(),
    recurringRuleId = null,
    isScheduled = false
)

@Before
fun setup() {
    every { categoryRepository.observeAll() } returns flowOf(emptyList())
    every { settingsRepository.settings } returns MutableStateFlow(AppSettings())
    every { personRepository.observeAll() } returns flowOf(emptyList())
    coEvery { transactionRepository.getById(42L) } returns fakeTransaction
    viewModel = AddTransactionViewModel(
        saveTransaction,
        categoryRepository,
        transactionRepository,
        settingsRepository,
        deleteTransaction,
        personRepository
    )
}
```

Also add the import at the top:
```kotlin
import com.spendtrack.data.repository.PersonRepository
```

- [ ] **Step 4: Update `AddTransactionViewModel.kt`**

Replace the entire file:

```kotlin
// app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModel.kt
package com.spendtrack.ui.feature.addtransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.repository.CategoryRepository
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.data.repository.TransactionRepository
import com.spendtrack.data.settings.AppSettings
import com.spendtrack.data.settings.SettingsRepository
import com.spendtrack.domain.model.Category
import com.spendtrack.domain.model.Label
import com.spendtrack.domain.model.Person
import com.spendtrack.domain.model.Transaction
import com.spendtrack.domain.model.TransactionType
import com.spendtrack.domain.usecase.DeleteTransactionUseCase
import com.spendtrack.domain.usecase.SaveTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class AddTransactionUiState(
    val amountCents: Long = 0L,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val selectedCategory: Category? = null,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val photoUri: String? = null,
    val labels: List<Label> = emptyList(),
    val persons: List<Person> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val availablePersons: List<Person> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val showCategoryPicker: Boolean = false,
    val showPersonPicker: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleted: Boolean = false
)

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val saveTransaction: SaveTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _form = MutableStateFlow(AddTransactionUiState())
    private var editingTransactionId: Long = 0L
    private var loadedTransaction: Transaction? = null

    val uiState: StateFlow<AddTransactionUiState> = combine(
        _form,
        categoryRepository.observeAll(),
        settingsRepository.settings,
        personRepository.observeAll()
    ) { form, categories, settings, persons ->
        form.copy(availableCategories = categories, settings = settings, availablePersons = persons)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddTransactionUiState()
    )

    fun loadTransaction(id: Long) {
        editingTransactionId = id
        viewModelScope.launch {
            val tx = transactionRepository.getById(id) ?: return@launch
            loadedTransaction = tx
            _form.update {
                it.copy(
                    amountCents = (tx.amount * 100).toLong(),
                    transactionType = tx.type,
                    selectedCategory = tx.category,
                    date = tx.date,
                    note = tx.note ?: "",
                    photoUri = tx.photoUri,
                    labels = tx.labels,
                    persons = tx.persons
                )
            }
        }
    }

    fun onDigit(digit: Int) {
        _form.update { s ->
            if (s.amountCents >= 9_999_999L) s
            else s.copy(amountCents = s.amountCents * 10 + digit)
        }
    }

    fun onBackspace() {
        _form.update { it.copy(amountCents = it.amountCents / 10) }
    }

    fun onTypeToggle(type: TransactionType) {
        _form.update { it.copy(transactionType = type, selectedCategory = null) }
    }

    fun onCategorySelected(category: Category) {
        _form.update { it.copy(selectedCategory = category, showCategoryPicker = false) }
    }

    fun onShowCategoryPicker() { _form.update { it.copy(showCategoryPicker = true) } }
    fun onDismissCategoryPicker() { _form.update { it.copy(showCategoryPicker = false) } }
    fun onDateChanged(newDate: LocalDate) { _form.update { it.copy(date = newDate) } }
    fun onNoteChanged(text: String) { _form.update { it.copy(note = text) } }
    fun onPhotoUri(uri: String?) { _form.update { it.copy(photoUri = uri) } }

    fun onLabelAdded(label: Label) {
        _form.update { s ->
            if (s.labels.any { it.id == label.id }) s
            else s.copy(labels = s.labels + label)
        }
    }

    fun onLabelRemoved(label: Label) {
        _form.update { it.copy(labels = it.labels.filter { l -> l.id != label.id }) }
    }

    fun onPersonAdded(person: Person) {
        _form.update { s ->
            if (s.persons.any { it.id == person.id }) s
            else s.copy(persons = s.persons + person)
        }
    }

    fun onPersonRemoved(person: Person) {
        _form.update { it.copy(persons = it.persons.filter { p -> p.id != person.id }) }
    }

    fun onShowPersonPicker() { _form.update { it.copy(showPersonPicker = true) } }
    fun onDismissPersonPicker() { _form.update { it.copy(showPersonPicker = false) } }

    fun save() {
        val state = _form.value
        val category = state.selectedCategory ?: return
        if (state.amountCents == 0L) return
        _form.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val tx = Transaction(
                id = editingTransactionId,
                amount = state.amountCents / 100.0,
                type = state.transactionType,
                category = category,
                date = state.date,
                note = state.note.takeIf { it.isNotBlank() },
                photoUri = state.photoUri,
                labels = state.labels,
                persons = state.persons,
                recurringRuleId = null,
                isScheduled = false
            )
            saveTransaction(tx)
            _form.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    fun onDeleteRequested() { _form.update { it.copy(showDeleteConfirmation = true) } }

    fun onDeleteDismissed() { _form.update { it.copy(showDeleteConfirmation = false) } }

    fun delete() {
        val tx = loadedTransaction ?: return
        viewModelScope.launch {
            deleteTransaction(tx)
            _form.update { it.copy(isDeleted = true) }
        }
    }
}
```

- [ ] **Step 5: Run all AddTransaction ViewModel tests**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.ui.feature.addtransaction.*"
```

Expected: all 8 tests pass (3 delete tests + 5 persons tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModel.kt \
        app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelDeleteTest.kt \
        app/src/test/java/com/spendtrack/ui/feature/addtransaction/AddTransactionViewModelPersonsTest.kt
git commit -m "feat: AddTransactionViewModel persons support"
```

---

## Task 6: LabelChip update, PersonChip, and PersonPickerBottomSheet

**Files:**
- Modify: `app/src/main/java/com/spendtrack/ui/component/LabelChip.kt`
- Create: `app/src/main/java/com/spendtrack/ui/component/PersonChip.kt`
- Create: `app/src/main/java/com/spendtrack/ui/component/PersonPickerBottomSheet.kt`

- [ ] **Step 1: Add optional color params to `LabelChip.kt`**

Replace the file:

```kotlin
// app/src/main/java/com/spendtrack/ui/component/LabelChip.kt
package com.spendtrack.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LabelChip(
    label: String,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove label",
                tint = contentColor,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
}
```

- [ ] **Step 2: Create `PersonChip.kt`**

```kotlin
// app/src/main/java/com/spendtrack/ui/component/PersonChip.kt
package com.spendtrack.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PersonChip(
    name: String,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LabelChip(
        label = name,
        onRemove = onRemove,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}
```

- [ ] **Step 3: Create `PersonPickerBottomSheet.kt`**

```kotlin
// app/src/main/java/com/spendtrack/ui/component/PersonPickerBottomSheet.kt
package com.spendtrack.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.spendtrack.domain.model.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPickerBottomSheet(
    allPersons: List<Person>,
    selectedPersons: List<Person>,
    onToggle: (Person) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds = selectedPersons.map { it.id }.toSet()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Link persons",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (allPersons.isEmpty()) {
            Text(
                text = "No persons yet. Add them in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn {
                items(allPersons, key = { it.id }) { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(person) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (person.id in selectedIds) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        Text(
            text = "Persons are managed in Settings",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
```

- [ ] **Step 4: Verify the project compiles**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/component/LabelChip.kt \
        app/src/main/java/com/spendtrack/ui/component/PersonChip.kt \
        app/src/main/java/com/spendtrack/ui/component/PersonPickerBottomSheet.kt
git commit -m "feat: PersonChip and PersonPickerBottomSheet components"
```

---

## Task 7: AddTransactionScreen UI

**Files:**
- Modify: `app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionScreen.kt`

- [ ] **Step 1: Add person chips, "Link persons…" row, and `PersonPickerBottomSheet` to `AddTransactionScreen.kt`**

Add the following imports at the top of the file (alongside existing imports):

```kotlin
import androidx.compose.material.icons.filled.Person
import com.spendtrack.ui.component.PersonChip
import com.spendtrack.ui.component.PersonPickerBottomSheet
```

Inside the `Column` in the `Scaffold` body, insert this block **after** the existing labels section (after the labels `FlowRow` + its `Spacer`), and **before** the `Spacer(modifier = Modifier.weight(1f))`:

```kotlin
// Person chips
if (uiState.persons.isNotEmpty()) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        uiState.persons.forEach { person ->
            PersonChip(
                name = person.name,
                onRemove = { viewModel.onPersonRemoved(person) }
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

// Link persons row
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable { viewModel.onShowPersonPicker() }
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(
        Icons.Default.Person,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(
        text = if (uiState.persons.isEmpty()) "Link persons (optional)" else "Edit linked persons",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

Spacer(modifier = Modifier.height(8.dp))
```

Add the `PersonPickerBottomSheet` call **outside the `Scaffold`**, after the existing `CategoryPickerBottomSheet` block:

```kotlin
if (uiState.showPersonPicker) {
    PersonPickerBottomSheet(
        allPersons = uiState.availablePersons,
        selectedPersons = uiState.persons,
        onToggle = { person ->
            if (uiState.persons.any { it.id == person.id }) {
                viewModel.onPersonRemoved(person)
            } else {
                viewModel.onPersonAdded(person)
            }
        },
        onDismiss = viewModel::onDismissPersonPicker
    )
}
```

- [ ] **Step 2: Verify the project compiles**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/addtransaction/AddTransactionScreen.kt
git commit -m "feat: person chips and picker in AddTransactionScreen"
```

---

## Task 8: PersonsViewModel

**Files:**
- Create: `app/src/main/java/com/spendtrack/ui/feature/persons/PersonsViewModel.kt`
- Test: `app/src/test/java/com/spendtrack/ui/feature/persons/PersonsViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
// app/src/test/java/com/spendtrack/ui/feature/persons/PersonsViewModelTest.kt
package com.spendtrack.ui.feature.persons

import app.cash.turbine.test
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.domain.model.Person
import com.spendtrack.util.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val personRepository: PersonRepository = mockk()
    private lateinit var viewModel: PersonsViewModel

    @Before
    fun setup() {
        every { personRepository.observeAll() } returns flowOf(emptyList())
        viewModel = PersonsViewModel(personRepository)
    }

    @Test
    fun `showAddDialog sets showAddDialog true`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.showAddDialog()
            assertTrue(awaitItem().showAddDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissAddDialog sets showAddDialog false`() = runTest {
        viewModel.showAddDialog()
        viewModel.uiState.test {
            awaitItem() // showAddDialog = true
            viewModel.dismissAddDialog()
            assertFalse(awaitItem().showAddDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addPerson calls repository save and dismisses dialog`() = runTest {
        coEvery { personRepository.save(Person(id = 0, name = "João")) } returns 1L
        viewModel.showAddDialog()
        viewModel.addPerson("João")
        advanceUntilIdle()
        coVerify { personRepository.save(Person(id = 0, name = "João")) }
        assertFalse(viewModel.uiState.value.showAddDialog)
    }

    @Test
    fun `addPerson trims whitespace`() = runTest {
        coEvery { personRepository.save(Person(id = 0, name = "João")) } returns 1L
        viewModel.addPerson("  João  ")
        advanceUntilIdle()
        coVerify { personRepository.save(Person(id = 0, name = "João")) }
    }

    @Test
    fun `addPerson does nothing for blank name`() = runTest {
        viewModel.addPerson("   ")
        advanceUntilIdle()
        coVerify(exactly = 0) { personRepository.save(any()) }
    }

    @Test
    fun `requestDelete sets deleteTarget and fetches transaction count`() = runTest {
        val person = Person(id = 1L, name = "Maria")
        coEvery { personRepository.countTransactions(1L) } returns 3
        viewModel.requestDelete(person)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(person, state.deleteTarget)
        assertEquals(3, state.deleteTransactionCount)
    }

    @Test
    fun `dismissDelete clears deleteTarget`() = runTest {
        val person = Person(id = 1L, name = "Maria")
        coEvery { personRepository.countTransactions(1L) } returns 0
        viewModel.requestDelete(person)
        advanceUntilIdle()
        viewModel.dismissDelete()
        assertNull(viewModel.uiState.value.deleteTarget)
    }

    @Test
    fun `confirmDelete calls repository delete and clears target`() = runTest {
        val person = Person(id = 1L, name = "Maria")
        coEvery { personRepository.countTransactions(1L) } returns 0
        coEvery { personRepository.delete(person) } just Runs
        viewModel.requestDelete(person)
        advanceUntilIdle()
        viewModel.confirmDelete()
        advanceUntilIdle()
        coVerify { personRepository.delete(person) }
        assertNull(viewModel.uiState.value.deleteTarget)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.ui.feature.persons.PersonsViewModelTest"
```

Expected: FAIL — `PersonsViewModel` does not exist yet.

- [ ] **Step 3: Create `PersonsViewModel.kt`**

```kotlin
// app/src/main/java/com/spendtrack/ui/feature/persons/PersonsViewModel.kt
package com.spendtrack.ui.feature.persons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.data.repository.PersonRepository
import com.spendtrack.domain.model.Person
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonsUiState(
    val persons: List<Person> = emptyList(),
    val showAddDialog: Boolean = false,
    val deleteTarget: Person? = null,
    val deleteTransactionCount: Int = 0
)

@HiltViewModel
class PersonsViewModel @Inject constructor(
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PersonsUiState())

    val uiState: StateFlow<PersonsUiState> = combine(
        _state,
        personRepository.observeAll()
    ) { state, persons ->
        state.copy(persons = persons)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PersonsUiState()
    )

    fun showAddDialog() { _state.update { it.copy(showAddDialog = true) } }

    fun dismissAddDialog() { _state.update { it.copy(showAddDialog = false) } }

    fun addPerson(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            personRepository.save(Person(id = 0, name = trimmed))
            _state.update { it.copy(showAddDialog = false) }
        }
    }

    fun requestDelete(person: Person) {
        viewModelScope.launch {
            val count = personRepository.countTransactions(person.id)
            _state.update { it.copy(deleteTarget = person, deleteTransactionCount = count) }
        }
    }

    fun dismissDelete() {
        _state.update { it.copy(deleteTarget = null, deleteTransactionCount = 0) }
    }

    fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        viewModelScope.launch {
            personRepository.delete(target)
            _state.update { it.copy(deleteTarget = null, deleteTransactionCount = 0) }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew testDebugUnitTest --tests "com.spendtrack.ui.feature.persons.PersonsViewModelTest"
```

Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/persons/PersonsViewModel.kt \
        app/src/test/java/com/spendtrack/ui/feature/persons/PersonsViewModelTest.kt
git commit -m "feat: PersonsViewModel with add/delete person logic"
```

---

## Task 9: PersonsScreen

**Files:**
- Create: `app/src/main/java/com/spendtrack/ui/feature/persons/PersonsScreen.kt`

- [ ] **Step 1: Create `PersonsScreen.kt`**

```kotlin
// app/src/main/java/com/spendtrack/ui/feature/persons/PersonsScreen.kt
package com.spendtrack.ui.feature.persons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonsScreen(
    navController: NavController,
    viewModel: PersonsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var addPersonName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Persons") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showAddDialog) {
                        Icon(Icons.Default.Add, contentDescription = "Add person")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.persons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No persons yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(uiState.persons, key = { it.id }) { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.requestDelete(person) }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete ${person.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissAddDialog()
                addPersonName = ""
            },
            title = { Text("Add person") },
            text = {
                OutlinedTextField(
                    value = addPersonName,
                    onValueChange = { addPersonName = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addPerson(addPersonName)
                        addPersonName = ""
                    },
                    enabled = addPersonName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissAddDialog()
                    addPersonName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text("Delete \"${target.name}\"?") },
            text = {
                if (uiState.deleteTransactionCount > 0) {
                    val n = uiState.deleteTransactionCount
                    Text("This person is linked to $n transaction${if (n == 1) "" else "s"}. Deleting will remove the link from all of them.")
                } else {
                    Text("This cannot be undone.")
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("Cancel")
                }
            }
        )
    }
}
```

- [ ] **Step 2: Verify the project compiles**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/feature/persons/PersonsScreen.kt
git commit -m "feat: PersonsScreen with add/delete person UI"
```

---

## Task 10: Navigation and SettingsScreen

**Files:**
- Modify: `app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/spendtrack/ui/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: Add `Screen.Persons` and its composable to `AppNavGraph.kt`**

In `AppNavGraph.kt`, add `Persons` to the `Screen` sealed class:

```kotlin
object Persons : Screen("persons")
```

Add the import at the top of the file:
```kotlin
import com.spendtrack.ui.feature.persons.PersonsScreen
```

Inside the `NavHost` block, add after the existing `settings` composable entry:

```kotlin
composable(Screen.Persons.route) {
    PersonsScreen(navController = navController)
}
```

- [ ] **Step 2: Add the Persons row to `SettingsScreen.kt`**

Add this import at the top of `SettingsScreen.kt`:

```kotlin
import androidx.compose.material.icons.filled.Person
```

Inside `SettingsScreen`, add the Persons row **before** the existing Currency row (i.e., immediately after the first `HorizontalDivider()`):

```kotlin
SettingsRow(
    icon = Icons.Default.Person,
    title = "Persons",
    subtitle = "Manage persons",
    onClick = { navController.navigate(Screen.Persons.route) }
)

HorizontalDivider()
```

- [ ] **Step 3: Run the full unit test suite**

```bash
./gradlew testDebugUnitTest
```

Expected: All tests pass.

- [ ] **Step 4: Build the final APK**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/spendtrack/ui/navigation/AppNavGraph.kt \
        app/src/main/java/com/spendtrack/ui/feature/settings/SettingsScreen.kt
git commit -m "feat: wire Persons screen into navigation and Settings"
```
