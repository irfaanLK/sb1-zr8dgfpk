package com.pet.measure.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.pet.measure.R
import com.pet.measure.data.GameFormat
import com.pet.measure.data.SettingsStore
import com.pet.measure.data.Team
import com.pet.measure.ui.theme.Pet
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit) {
    val s by store.state.collectAsState()
    Scaffold(
        containerColor = Pet.Night,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Pet.Night),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.settings_game)) {
                Segmented(
                    options = listOf(stringResource(R.string.format_doublette), stringResource(R.string.format_triplette)),
                    selected = s.format.ordinal,
                ) { i -> store.update { it.copy(format = GameFormat.entries[i]) } }
            }

            Section(stringResource(R.string.settings_teams)) {
                TeamField(Team.A, s.teamAName, stringResource(R.string.team_a_default)) { v -> store.update { it.copy(teamAName = v) } }
                Spacer(Modifier.height(10.dp))
                TeamField(Team.B, s.teamBName, stringResource(R.string.team_b_default)) { v -> store.update { it.copy(teamBName = v) } }
            }

            Section(stringResource(R.string.settings_measure)) {
                SliderRow(
                    label = stringResource(R.string.settings_shown),
                    caption = stringResource(R.string.settings_shown_body),
                    value = s.shownCount,
                    range = 1..6,
                    display = s.shownCount.toString(),
                ) { v -> store.update { it.copy(shownCount = v) } }
                SliderRow(
                    label = stringResource(R.string.settings_boule_diameter),
                    value = s.bouleDiameterMm,
                    range = 71..80,
                    display = stringResource(R.string.mm_value, s.bouleDiameterMm),
                ) { v -> store.update { it.copy(bouleDiameterMm = v) } }
                SliderRow(
                    label = stringResource(R.string.settings_jack_diameter),
                    value = s.jackDiameterMm,
                    range = 25..35,
                    display = stringResource(R.string.mm_value, s.jackDiameterMm),
                ) { v -> store.update { it.copy(jackDiameterMm = v) } }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_precision), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Segmented(
                    options = listOf(stringResource(R.string.precision_cm), stringResource(R.string.precision_tenth)),
                    selected = if (s.tenthPrecision) 1 else 0,
                ) { i -> store.update { it.copy(tenthPrecision = i == 1) } }
            }

            Section(stringResource(R.string.settings_saving)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_gallery), modifier = Modifier.weight(1f))
                    Switch(
                        checked = s.saveToGallery,
                        onCheckedChange = { v -> store.update { it.copy(saveToGallery = v) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = Pet.Lavender),
                    )
                }
            }

            Section(stringResource(R.string.settings_language)) {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                val selected = when {
                    current.startsWith("fr") -> 2
                    current.startsWith("en") -> 1
                    else -> 0
                }
                Segmented(
                    options = listOf(
                        stringResource(R.string.language_system),
                        stringResource(R.string.language_en),
                        stringResource(R.string.language_fr),
                    ),
                    selected = selected,
                ) { i ->
                    val tags = when (i) { 1 -> "en"; 2 -> "fr"; else -> "" }
                    AppCompatDelegate.setApplicationLocales(
                        if (tags.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tags),
                    )
                }
            }

            Section(stringResource(R.string.settings_about)) {
                Text(stringResource(R.string.about_body), color = Pet.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Pet.Stone)
            .padding(16.dp),
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Pet.Ochre, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Pet.Lavender.copy(alpha = 0.22f),
                    activeContentColor = Pet.Cream,
                    inactiveContainerColor = Color.Transparent,
                ),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun TeamField(team: Team, value: String, placeholder: String, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Pet.team(team)))
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.take(24)
                onChange(text)
            },
            placeholder = { Text(placeholder) },
            label = { Text(stringResource(R.string.team_name_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pet.team(team)),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SliderRow(label: String, caption: String? = null, value: Int, range: IntRange, display: String, onChange: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = Pet.Muted)
            }
            Text(display, style = MaterialTheme.typography.titleMedium, color = Pet.Lavender)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = SliderDefaults.colors(thumbColor = Pet.Lavender, activeTrackColor = Pet.Lavender),
        )
    }
}
