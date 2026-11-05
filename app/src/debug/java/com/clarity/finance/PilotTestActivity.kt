package com.clarity.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.clarity.finance.ui.theme.ClarityTheme

/** Debug-only recreation host. No graph fallback; instrumentation must provide isolated content. */
class PilotTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val isolated = content ?: run { finish(); return }
        // Match MainActivity's window/frame while injecting only an explicit fake graph body.
        setContent { ClarityTheme {
            Scaffold(Modifier.fillMaxSize()) { padding -> Box(Modifier.padding(padding)) { isolated() } }
        } }
    }
    companion object { var content: (@Composable () -> Unit)? = null }
}
