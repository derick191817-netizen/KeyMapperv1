package com.example.keymapper

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableIntStateOf
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)
    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        val repo = SettingsRepository(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SettingsScreen(repo, tick) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}
