package com.atlasfpt.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atlasfpt.data.db.entity.CategoryEntity
import com.atlasfpt.data.db.entity.PersonEntity
import com.atlasfpt.data.db.entity.TransactionEntity
import com.atlasfpt.data.db.entity.TransactionPersonCrossRef
import com.atlasfpt.domain.model.CategoryType
import com.atlasfpt.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TransactionCascadeTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun deletingTransactionCascadesPersonCrossRefs() = runTest {
        val categoryId = db.categoryDao().insert(
            CategoryEntity(name = "Test", iconRes = "icon", color = 0, type = CategoryType.EXPENSE)
        )
        val personId = db.personDao().insert(PersonEntity(name = "Alice"))
        val transactionEntity = TransactionEntity(
            amount = 10.0,
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            date = LocalDate.of(2026, 1, 1),
        )
        val transactionId = db.transactionDao().insert(transactionEntity)
        db.transactionDao().insertPersonCrossRef(
            TransactionPersonCrossRef(transactionId = transactionId, personId = personId)
        )

        assertEquals(1, db.personDao().countTransactions(personId))

        db.transactionDao().delete(transactionEntity.copy(id = transactionId))

        assertEquals(
            "FK CASCADE should remove the cross-ref when the transaction is deleted",
            0,
            db.personDao().countTransactions(personId),
        )
    }
}
