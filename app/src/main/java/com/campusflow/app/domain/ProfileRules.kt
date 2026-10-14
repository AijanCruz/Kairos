package com.campusflow.app.domain

import java.time.LocalDateTime

object ProfileRules {
    fun normalizeName(value: String): String = value.trim().replace(Regex("\\s+"), " ").also {
        require(it.length <= 40) { "Usa un nombre de hasta 40 caracteres." }
    }
    fun greeting(now: LocalDateTime, name: String): String {
        val (text, icon) = when (now.hour) {
            in 5..11 -> "Buenos días" to "☀️"
            in 12..18 -> "Buenas tardes" to "🌤️"
            else -> "Buenas noches" to "🌙"
        }
        return "$text${name.trim().takeIf { it.isNotEmpty() }?.let { ", $it" }.orEmpty()} $icon"
    }
}
