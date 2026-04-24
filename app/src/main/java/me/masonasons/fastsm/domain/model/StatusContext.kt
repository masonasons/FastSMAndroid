package me.masonasons.fastsm.domain.model

data class StatusContext(
    val ancestors: List<UniversalStatus>,
    val descendants: List<UniversalStatus>,
)
