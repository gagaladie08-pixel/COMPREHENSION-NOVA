package com.novastats.app.ui.navigation

import com.novastats.app.domain.Chart
import com.novastats.app.domain.Period
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** Téléporteur Stats → Billboard : ouvre le chart de la période et surligne la ligne de l'entité. */
object BillboardFocus {
    data class Focus(
        val chart: Chart,
        val period: Period,
        val entityId: Long,
        val anchor: LocalDate? = null,
        val expectedPosition: Int? = null,
        val expectedPlays: Int? = null
    )
    val state = MutableStateFlow<Focus?>(null)
    fun request(f: Focus) { state.value = f }
    fun consume() { state.value = null }
}
