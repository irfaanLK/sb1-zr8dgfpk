package com.pet.measure

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pet.measure.ar.ArSessionHolder
import com.pet.measure.data.HistoryStore
import com.pet.measure.data.SettingsStore
import com.pet.measure.ui.HistoryDetailScreen
import com.pet.measure.ui.HistoryScreen
import com.pet.measure.ui.MeasureScreen
import com.pet.measure.ui.SettingsScreen
import com.pet.measure.ui.theme.PetTheme

class MainActivity : AppCompatActivity() {

    private val arHolder = ArSessionHolder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val settingsStore = SettingsStore(applicationContext)
        val history = HistoryStore(applicationContext)

        setContent {
            PetTheme {
                val settings by settingsStore.state.collectAsState()
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "measure") {
                    composable("measure") {
                        MeasureScreen(
                            holder = arHolder,
                            settings = settings,
                            history = history,
                            onOpenHistory = { nav.navigate("history") },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("history") {
                        HistoryScreen(history, onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("history/$id") })
                    }
                    composable("history/{id}") { entry ->
                        HistoryDetailScreen(history, entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(settingsStore, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        arHolder.close()
    }
}
