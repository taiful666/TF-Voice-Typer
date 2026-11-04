package com.taiful.tfvoicetyper.ui

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taiful.tfvoicetyper.TFApp
import com.taiful.tfvoicetyper.ai.GeminiException
import com.taiful.tfvoicetyper.data.AppSettings
import com.taiful.tfvoicetyper.data.db.Record
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val tf = application as TFApp
    private val dao = tf.db.records()

    val settings: StateFlow<AppSettings?> = tf.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val savedModels: StateFlow<List<String>> = tf.settings.savedModels.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val engine = tf.engine.state
    val latest: StateFlow<Record?> = dao.latestDone().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val recent: StateFlow<List<Record>> = dao.all().map { it.take(6) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val count: StateFlow<Int> = dao.count().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val query = MutableStateFlow("")
    val history: StateFlow<List<Record>> = query
        .flatMapLatest { q -> if (q.isBlank()) dao.all() else dao.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun record(id: Long): Flow<Record?> = dao.observe(id)

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { tf.settings.set(key, value) }
    }

    fun saveText(id: Long, bangla: String, english: String) {
        viewModelScope.launch { tf.engine.saveText(id, bangla, english) }
    }

    fun delete(ids: List<Long>) {
        viewModelScope.launch { tf.engine.deleteRecords(ids) }
    }

    fun deleteAll() {
        viewModelScope.launch { tf.engine.deleteAll() }
    }

    fun cleanupAudio() {
        viewModelScope.launch { tf.engine.cleanupAudio() }
    }

    suspend fun storedKey(): String = tf.settings.apiKey().orEmpty()

    suspend fun saveKey(key: String) = tf.settings.setApiKey(key)

    suspend fun storedGroqKey(): String = tf.settings.groqKey().orEmpty()

    suspend fun saveGroqKey(key: String) = tf.settings.setGroqKey(key)

    suspend fun testGroq(key: String): Pair<Boolean, String> = try {
        true to tf.groq.test(key)
    } catch (e: GeminiException) {
        false to e.userMessage
    } catch (e: Exception) {
        false to (e.message ?: "অজানা সমস্যা")
    }

    suspend fun test(key: String, model: String): Pair<Boolean, String> = try {
        true to tf.gemini.test(key, model)
    } catch (e: GeminiException) {
        false to e.userMessage
    } catch (e: Exception) {
        false to (e.message ?: "অজানা সমস্যা")
    }

    suspend fun loadModels(key: String): Pair<Boolean, String> = try {
        val list = tf.gemini.listModels(key)
        tf.settings.setModels(list)
        true to "${list.size}টি মডেল পাওয়া গেছে"
    } catch (e: GeminiException) {
        false to e.userMessage
    } catch (e: Exception) {
        false to (e.message ?: "অজানা সমস্যা")
    }
}
