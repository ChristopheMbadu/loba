package com.congolingo.loba

import android.Manifest
import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.congolingo.loba.core.AppStore
import com.congolingo.loba.core.Reminder
import com.congolingo.loba.core.appContext
import com.congolingo.loba.ui.CoverStack
import com.congolingo.loba.ui.DebugFlags
import com.congolingo.loba.ui.GoogleSignIn
import com.congolingo.loba.ui.RootView

class LobaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
        AppStore.load()
    }
}

class MainActivity : ComponentActivity() {
    private var notificationAnswer: ((Boolean) -> Unit)? = null
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationAnswer?.invoke(granted)
        notificationAnswer = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Loba is a light app: dark icons in the system bars, whatever the phone's theme.
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        DebugFlags.read(intent)
        Reminder.askPermission = { done ->
            if (Build.VERSION.SDK_INT >= 33) {
                notificationAnswer = done
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else done(true)
        }
        setContent { CoverStack { RootView() } }
    }

    override fun onStart() {
        super.onStart()
        AppStore.tick()
    }

    override fun onStop() {
        super.onStop()
        AppStore.flush()
    }

    override fun onResume() {
        super.onResume()
        GoogleSignIn.resumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The browser coming back from Google sign-in: loba://auth?code=…&state=…
        intent.dataString?.let { GoogleSignIn.returned(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        Reminder.askPermission = null
    }
}
