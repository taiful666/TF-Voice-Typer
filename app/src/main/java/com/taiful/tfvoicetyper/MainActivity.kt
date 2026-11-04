package com.taiful.tfvoicetyper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.taiful.tfvoicetyper.ui.AppRoot
import com.taiful.tfvoicetyper.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val resumeTick = mutableIntStateOf(0)
    private val openRecord = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            AppRoot(vm, resumeTick.intValue, openRecord.value, onOpenRecord = { openRecord.value = it })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val id = i?.getLongExtra(EXTRA_RECORD, -1L) ?: -1L
        if (id > 0) openRecord.value = id
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    companion object {
        const val EXTRA_RECORD = "open_record"
    }
}
