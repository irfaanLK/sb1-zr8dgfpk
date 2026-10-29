package com.pet.measure.data

import android.content.Context
import com.pet.measure.vision.BallSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.roundToInt

enum class GameFormat { DOUBLETTE, TRIPLETTE }

enum class Team {
    NONE, A, B;

    fun next() = entries[(ordinal + 1) % entries.size]
}

data class AppSettings(
    val format: GameFormat = GameFormat.TRIPLETTE,
    /** Blank means "use the translated default name". */
    val teamAName: String = "",
    val teamBName: String = "",
    val shownCount: Int = 3,
    val bouleDiameterMm: Int = 74,
    val jackDiameterMm: Int = 30,
    val tenthPrecision: Boolean = false,
    val saveToGallery: Boolean = true,
) {
    val ballSpec get() = BallSpec(jackRadius = jackDiameterMm / 2000f, bouleRadius = bouleDiameterMm / 2000f)
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load() = AppSettings(
        format = runCatching { GameFormat.valueOf(prefs.getString("format", null) ?: "") }.getOrDefault(GameFormat.TRIPLETTE),
        teamAName = prefs.getString("teamA", "") ?: "",
        teamBName = prefs.getString("teamB", "") ?: "",
        shownCount = prefs.getInt("shown", 3),
        bouleDiameterMm = prefs.getInt("bouleMm", 74),
        jackDiameterMm = prefs.getInt("jackMm", 30),
        tenthPrecision = prefs.getBoolean("tenth", false),
        saveToGallery = prefs.getBoolean("gallery", true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        _state.value = s
        prefs.edit()
            .putString("format", s.format.name)
            .putString("teamA", s.teamAName)
            .putString("teamB", s.teamBName)
            .putInt("shown", s.shownCount)
            .putInt("bouleMm", s.bouleDiameterMm)
            .putInt("jackMm", s.jackDiameterMm)
            .putBoolean("tenth", s.tenthPrecision)
            .putBoolean("gallery", s.saveToGallery)
            .apply()
    }
}

/** "12 cm", or "12.4 cm" (with the locale's decimal separator) at tenth precision. */
fun formatDistance(meters: Float, tenth: Boolean): String {
    val cm = meters * 100f
    return if (tenth) String.format(Locale.getDefault(), "%.1f cm", cm) else "${cm.roundToInt()} cm"
}
