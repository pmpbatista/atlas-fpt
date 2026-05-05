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
