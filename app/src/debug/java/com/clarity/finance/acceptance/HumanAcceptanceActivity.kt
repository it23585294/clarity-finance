package com.clarity.finance.acceptance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clarity.finance.ui.navigation.AppNavigation
import com.clarity.finance.ui.theme.ClarityTheme

/** Explicit debug opt-in only. The external launcher MUST inspect default auth/recovery first:
 * Application initializes before Activity, so an Activity check cannot protect a live profile. */
class HumanAcceptanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.getBooleanExtra("isolatedAcceptance", false)) { finish(); return }
        enableEdgeToEdge()
        val fixture = AcceptanceSession.fixture
        setContent { ClarityTheme {
            Scaffold(topBar = {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Text("ISOLATED ACCEPTANCE · Fictional, memory-only data · No cloud", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { AppNavigation(fixture.graph.koin) } }
        } }
    }
}
