package com.taiful.tfvoicetyper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tf_settings")

data class AppSettings(
    val hasApiKey: Boolean = false,
    val hasGroqKey: Boolean = false,
    /** auto = Gemini first then Groq (then the offline model, if downloaded); groq = Groq first; offline = only the offline Bangla model; phone = phone's own voice typing (no key) */
    val engine: String = ENGINE_AUTO,
    val model: String = DEFAULT_MODEL,
    val translate: Boolean = true,
    val autoCopy: Boolean = true,
    val bubbleEnabled: Boolean = false,
    val bubbleAlpha: Float = 0.7f,
    val bubbleSizeDp: Int = 52,
    /** float = full round button always shown; edge = tucks into the screen edge as a small tab */
    val bubbleStyle: String = STYLE_FLOAT,
    /** 0 = delete after success, -1 = keep forever, otherwise days */
    val audioRetentionDays: Int = 7,
    val theme: String = "emerald",
    /** 0 = follow phone, 1 = light, 2 = dark */
    val darkMode: Int = 0,
) {
    companion object {
        const val DEFAULT_MODEL = "gemini-flash-latest"
        const val ENGINE_AUTO = "auto"
        const val ENGINE_GROQ = "groq"
        const val ENGINE_PHONE = "phone"
        const val ENGINE_OFFLINE = "offline"
        const val STYLE_FLOAT = "float"
        const val STYLE_EDGE = "edge"
    }
}

class SettingsRepo(private val context: Context) {
    private val ds get() = context.dataStore

    object K {
        val apiKey = stringPreferencesKey("api_key_enc")
        val groqKey = stringPreferencesKey("groq_key_enc")
        val engine = stringPreferencesKey("engine")
        val model = stringPreferencesKey("model")
        val translate = booleanPreferencesKey("translate")
        val autoCopy = booleanPreferencesKey("auto_copy")
        val bubbleEnabled = booleanPreferencesKey("bubble_enabled")
        val bubbleAlpha = floatPreferencesKey("bubble_alpha")
        val bubbleSize = intPreferencesKey("bubble_size")
        val bubbleStyle = stringPreferencesKey("bubble_style")
        val retention = intPreferencesKey("audio_retention")
        val theme = stringPreferencesKey("theme")
        val darkMode = intPreferencesKey("dark_mode")
        val bubbleX = intPreferencesKey("bubble_x")
        val bubbleY = intPreferencesKey("bubble_y")
        val models = stringPreferencesKey("model_list")
    }

    val flow: Flow<AppSettings> = ds.data.map { p ->
        AppSettings(
            hasApiKey = !p[K.apiKey].isNullOrEmpty(),
            hasGroqKey = !p[K.groqKey].isNullOrEmpty(),
            engine = p[K.engine] ?: AppSettings.ENGINE_AUTO,
            model = p[K.model] ?: AppSettings.DEFAULT_MODEL,
            translate = p[K.translate] ?: true,
            autoCopy = p[K.autoCopy] ?: true,
            bubbleEnabled = p[K.bubbleEnabled] ?: false,
            bubbleAlpha = p[K.bubbleAlpha] ?: 0.7f,
            bubbleSizeDp = p[K.bubbleSize] ?: 52,
            bubbleStyle = p[K.bubbleStyle] ?: AppSettings.STYLE_FLOAT,
            audioRetentionDays = p[K.retention] ?: 7,
            theme = p[K.theme] ?: "emerald",
            darkMode = p[K.darkMode] ?: 0,
        )
    }

    /** Models fetched from Google (saved so the list is there next time). */
    val savedModels: Flow<List<String>> = ds.data.map { p ->
        p[K.models]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        ds.edit { it[key] = value }
    }

    suspend fun apiKey(): String? = withContext(Dispatchers.Default) {
        val enc = ds.data.first()[K.apiKey] ?: return@withContext null
        SecureStore.decrypt(enc)?.takeIf { it.isNotBlank() }
    }

    suspend fun setApiKey(key: String?) = withContext(Dispatchers.Default) {
        val clean = key?.trim().orEmpty()
        ds.edit {
            if (clean.isEmpty()) it.remove(K.apiKey) else it[K.apiKey] = SecureStore.encrypt(clean)
        }
    }

    suspend fun groqKey(): String? = withContext(Dispatchers.Default) {
        val enc = ds.data.first()[K.groqKey] ?: return@withContext null
        SecureStore.decrypt(enc)?.takeIf { it.isNotBlank() }
    }

    suspend fun setGroqKey(key: String?) = withContext(Dispatchers.Default) {
        val clean = key?.trim().orEmpty()
        ds.edit {
            if (clean.isEmpty()) it.remove(K.groqKey) else it[K.groqKey] = SecureStore.encrypt(clean)
        }
    }

    suspend fun bubblePos(): Pair<Int, Int>? {
        val p = ds.data.first()
        val x = p[K.bubbleX] ?: return null
        val y = p[K.bubbleY] ?: return null
        return x to y
    }

    suspend fun setBubblePos(x: Int, y: Int) {
        ds.edit { it[K.bubbleX] = x; it[K.bubbleY] = y }
    }

    suspend fun setModels(list: List<String>) {
        ds.edit { it[K.models] = list.joinToString("\n") }
    }
}
