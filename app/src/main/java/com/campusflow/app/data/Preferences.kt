package com.campusflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.preferences by preferencesDataStore("preferences")
enum class Appearance(val label: String) { SYSTEM("Seguir sistema"), LIGHT("Claro"), DARK("Oscuro") }
data class UserPreferences(
    val appearance: Appearance = Appearance.SYSTEM,
    val dynamicColor: Boolean = false,
    val reminderMinutes: Int = 15,
    val studyMinutes: Int = 30,
    val notifications: Boolean = true,
)

class PreferencesRepository(private val context: Context) {
    private val theme = stringPreferencesKey("appearance")
    private val dynamic = booleanPreferencesKey("dynamic")
    private val reminder = intPreferencesKey("reminder")
    private val study = intPreferencesKey("study")
    private val notifications = booleanPreferencesKey("notifications")
    val flow = context.preferences.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        UserPreferences(
            Appearance.entries.find { it.name == values[theme] } ?: Appearance.SYSTEM,
            values[dynamic] ?: false, values[reminder] ?: 15,
            values[study] ?: 30, values[notifications] ?: true,
        )
    }
    suspend fun setAppearance(value: Appearance) { context.preferences.edit { it[theme] = value.name } }
    suspend fun setDynamic(value: Boolean) { context.preferences.edit { it[dynamic] = value } }
    suspend fun setReminder(value: Int) { context.preferences.edit { it[reminder] = value } }
    suspend fun setStudy(value: Int) { context.preferences.edit { it[study] = value } }
    suspend fun setNotifications(value: Boolean) { context.preferences.edit { it[notifications] = value } }
}
