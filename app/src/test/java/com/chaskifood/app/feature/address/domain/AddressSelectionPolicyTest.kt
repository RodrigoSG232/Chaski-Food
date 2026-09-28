package com.chaskifood.app.feature.address.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AddressSelectionPolicyTest(
    private val scenario: String,
    private val availableIds: Set<String>,
    private val selectedId: String?,
    private val defaultId: String?,
    private val expectedId: String?,
) {
    @Test
    fun `resuelve seleccion sin alterar predeterminada ni elegir el primer elemento`() {
        assertEquals(
            scenario,
            expectedId,
            AddressSelectionPolicy.resolve(availableIds, selectedId, defaultId),
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun scenarios(): List<Array<Any?>> = listOf(
            arrayOf("Cuenta vacía", emptySet<String>(), null, null, null),
            arrayOf("Primera alta confirmada", setOf("A"), null, "A", "A"),
            arrayOf("Alta posterior conserva selección", setOf("A", "B"), "A", "A", "A"),
            arrayOf("Reinicio conserva selección distinta", setOf("A", "B"), "B", "A", "B"),
            arrayOf("Cambio de predeterminada no cambia selección", setOf("A", "B"), "A", "B", "A"),
            arrayOf("Edición mantiene identidad seleccionada", setOf("A", "B"), "B", "A", "B"),
            arrayOf("Borrado de seleccionada utiliza predeterminada", setOf("A"), "B", "A", "A"),
            arrayOf("Borrado con reemplazo explícito", setOf("B"), "A", "B", "B"),
            arrayOf("Borrado de última limpia selección", emptySet<String>(), "A", null, null),
            arrayOf("Referencias inválidas requieren elegir", setOf("B", "C"), "A", "D", null),
            arrayOf("Sin preferencias no elige primer elemento", setOf("B", "A"), null, null, null),
            arrayOf("Predeterminada inválida no destruye selección", setOf("A"), "A", "B", "A"),
            arrayOf("ID vacío no es selección", setOf("", "A"), "", "A", "A"),
            arrayOf("ID blanco no es predeterminada", setOf(" ", "A"), null, " ", null),
        )
    }
}
