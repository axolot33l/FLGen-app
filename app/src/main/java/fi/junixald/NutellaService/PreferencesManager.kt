package fi.junixald.NutellaService

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    companion object {
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val SHARED_SECRET = stringPreferencesKey("shared_secret")
    }

    val startOnBoot: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[START_ON_BOOT] ?: false
    }

    val sharedSecret: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SHARED_SECRET] ?: ""
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[START_ON_BOOT] = enabled
        }
    }

    suspend fun setSharedSecret(secret: String) {
        context.dataStore.edit { preferences ->
            preferences[SHARED_SECRET] = secret
        }
    }
}
