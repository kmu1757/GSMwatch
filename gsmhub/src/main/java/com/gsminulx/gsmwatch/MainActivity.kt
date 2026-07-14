package com.gsminulx.gsmwatch

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gsminulx.gsmwatch.ui.theme.GSMwatchTheme

class MainActivity : ComponentActivity() {

    // DataGSM OAuth 리다이렉트(gsmhub://oauth2redirect?code=...)를 받아 화면으로 전달
    private var oauthRedirect by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleRedirect(intent)
        setContent {
            GSMwatchTheme {
                HubApp(oauthRedirect = oauthRedirect, onRedirectHandled = { oauthRedirect = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRedirect(intent)
    }

    private fun handleRedirect(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "gsmhub") oauthRedirect = data
    }
}
