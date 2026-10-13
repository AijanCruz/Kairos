package com.campusflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import com.campusflow.app.domain.ProfileRules

private val Context.preferences by preferencesDataStore("preferences")
enum class Appearance(val label: String) { SYSTEM("Seguir sistema"), LIGHT("Claro"), DARK("Oscuro") }
data class UserPreferences(
    val appearance: Appearance = Appearance.DARK,
    val dynamicColor: Boolean = false,
    val reminderMinutes: Int = 15,
    val studyMinutes: Int = 30,
    val notifications: Boolean = true,
    val displayName: String = "",
    val welcomeCompleted: Boolean = false,
    val loaded: Boolean = false,
)

class PreferencesRepository(private val context: Context) {
    private val theme = stringPreferencesKey("appearance")
    private val dynamic = booleanPreferencesKey("dynamic")
    private val reminder = intPreferencesKey("reminder")
    private val study = intPreferencesKey("study")
    private val notifications = booleanPreferencesKey("notifications")
    private val displayName = stringPreferencesKey("display_name")
    private val welcomeCompleted = booleanPreferencesKey("welcome_completed")
    val flow = context.preferences.data.map { values ->
        UserPreferences(
            Appearance.entries.find { it.name == values[theme] } ?: Appearance.DARK,
            values[dynamic] ?: false, values[reminder] ?: 15,
            values[study] ?: 30, values[notifications] ?: true,
            displayName = values[displayName].orEmpty(), welcomeCompleted = values[welcomeCompleted] ?: false, loaded = true,
        )
    }
    .catch { error ->
        // A failed read is not a first launch: don't show onboarding or overwrite a profile.
        if (error is IOException) emit(UserPreferences()) else throw error
    }
    suspend fun setProfile(name: String, completed: Boolean = true) {
        val normalized = ProfileRules.normalizeName(name)
        context.preferences.edit { it[displayName] = normalized; it[welcomeCompleted] = completed }
    }
    suspend fun setAppearance(value: Appearance) { context.preferences.edit { it[theme] = value.name } }
    suspend fun setDynamic(value: Boolean) { context.preferences.edit { it[dynamic] = value } }
    suspend fun setReminder(value: Int) { context.preferences.edit { it[reminder] = value } }
    suspend fun setStudy(value: Int) { context.preferences.edit { it[study] = value } }
    suspend fun setNotifications(value: Boolean) { context.preferences.edit { it[notifications] = value } }
}
