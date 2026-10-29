package com.pet.measure.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Texture
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pet.measure.R
import com.pet.measure.ar.ArMeasureView
import com.pet.measure.ar.ArSessionHolder
import com.pet.measure.ar.ArStartResult
import com.pet.measure.ar.MeasureStatus
import com.pet.measure.ar.MeasureUiState
import com.pet.measure.data.AppSettings
import com.pet.measure.data.GameFormat
import com.pet.measure.data.HistoryEntry
import com.pet.measure.data.HistoryStore
import com.pet.measure.data.Team
import com.pet.measure.data.formatDistance
import com.pet.measure.overlay.OverlayBall
import com.pet.measure.ui.theme.DistanceStyle
import com.pet.measure.ui.theme.Pet
import com.pet.measure.vision.BallKind
import kotlinx.coroutines.launch

private class LongPress(val x: Float, val y: Float, val ball: OverlayBall?)

@Composable
fun MeasureScreen(
    holder: ArSessionHolder,
    settings: AppSettings,
    history: HistoryStore,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!granted && !askedOnce) launcher.launch(Manifest.permission.CAMERA)
    }
    if (!granted) {
        val activity = context.findActivity()
        val permanentlyDenied = askedOnce && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onGrant = { launcher.launch(Manifest.permission.CAMERA) },
        )
        return
    }
    ArMeasureContent(holder, settings, history, onOpenHistory, onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArMeasureContent(
    holder: ArSessionHolder,
    settings: AppSettings,
    history: HistoryStore,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var ui by remember { mutableStateOf(MeasureUiState()) }
    var startProblem by remember { mutableStateOf<ArStartResult?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var locked by remember { mutableStateOf(holder.measureState.locked) }
    var longPress by remember { mutableStateOf<LongPress?>(null) }
    var teamToast by remember { mutableStateOf<Team?>(null) }
    var hintDismissed by rememberSaveable { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }

    val teamAName = settings.teamAName.ifBlank { stringResource(R.string.team_a_default) }
    val teamBName = settings.teamBName.ifBlank { stringResource(R.string.team_b_default) }
    val noTeam = stringResource(R.string.boule)
    fun teamName(team: Team) = when (team) {
        Team.A -> teamAName
        Team.B -> teamBName
        Team.NONE -> noTeam
    }

    val view = remember { ArMeasureView(context, holder.measureState) }
    view.spec = settings.ballSpec
    view.shownCount = settings.shownCount
    val tenth = settings.tenthPrecision
    view.formatLabel = { formatDistance(it, tenth) }
    view.onState = { ui = it }
    view.overlay.onBallTap = { ball ->
        if (ball.kind == BallKind.BOULE) {
            teamToast = view.cycleTeam(ball)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    view.overlay.onLongPress = { x, y, ball ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        longPress = LongPress(x, y, ball)
    }

    DisposableEffect(lifecycleOwner, retryKey) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val act = activity ?: return@LifecycleEventObserver
                    when (val r = holder.start(act)) {
                        is ArStartResult.Ready -> {
                            try {
                                r.session.resume()
                                view.session = r.session
                                view.onResume()
                                startProblem = null
                            } catch (e: Exception) {
                                startProblem = ArStartResult.Failed(e.localizedMessage ?: e.javaClass.simpleName)
                            }
                        }
                        ArStartResult.Installing -> Unit
                        else -> { startProblem = r }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    view.onPause()
                    holder.session?.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
            holder.session?.pause()
            view.session = null
        }
    }
    DisposableEffect(view) { onDispose { view.release() } }

    // Gentle haptic confirmation when the jack is first found.
    LaunchedEffect(ui.jackFound) {
        if (ui.jackFound) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(teamToast) {
        if (teamToast != null) {
            kotlinx.coroutines.delay(1200)
            teamToast = null
        }
    }

    startProblem?.let { problem ->
        ArProblemScreen(problem) { startProblem = null; retryKey++ }
        return
    }

    val msgSaved = stringResource(R.string.captured)
    val msgSavedGallery = stringResource(R.string.captured_gallery)
    val msgFailed = stringResource(R.string.capture_failed)
    val msgNoGround = stringResource(R.string.menu_no_ground)

    Box(Modifier.fillMaxSize().background(Pet.Night)) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())

        // Top scrim and bar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Brand()
                Spacer(Modifier.width(10.dp))
                GlassChip(
                    text = stringResource(
                        if (settings.format == GameFormat.DOUBLETTE) R.string.format_doublette_short else R.string.format_triplette_short,
                    ),
                    onClick = onOpenSettings,
                )
                Spacer(Modifier.weight(1f))
                RoundIconButton(Icons.Rounded.RestartAlt, stringResource(R.string.action_reset), size = 44) {
                    view.reset()
                    locked = false
                    holder.measureState.locked = false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.Settings, stringResource(R.string.action_settings), size = 44, onClick = onOpenSettings)
            }
            Spacer(Modifier.height(10.dp))
            StatusPill(ui.status)
            AnimatedVisibility(visible = ui.status == MeasureStatus.MEASURING && !hintDismissed) {
                Row(
                    Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Pet.Glass)
                        .clickable { hintDismissed = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.TouchApp, null, tint = Pet.Lavender, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.hint_gestures),
                        style = MaterialTheme.typography.bodySmall,
                        color = Pet.Muted,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Close, null, tint = Pet.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Team toast in the middle of the screen.
        AnimatedVisibility(
            visible = teamToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val t = teamToast ?: Team.NONE
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Pet.Glass)
                    .border(2.dp, Pet.team(t), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Pet.team(t)))
                Spacer(Modifier.width(10.dp))
                Text(if (t == Team.NONE) stringResource(R.string.no_team) else teamName(t), fontWeight = FontWeight.Bold)
            }
        }

        // Bottom: results + controls.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ResultsCard(ui, tenth, ::teamName)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledRoundButton(Icons.Rounded.History, stringResource(R.string.action_history), onClick = onOpenHistory)
                Shutter(enabled = !capturing) {
                    capturing = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val entries = ui.distances.map { HistoryEntry(it.team, teamName(it.team), it.distanceM) }
                    scope.launch { flash.snapTo(0.8f); flash.animateTo(0f, tween(350)) }
                    view.capture { bmp ->
                        scope.launch {
                            val message = if (bmp == null) {
                                msgFailed
                            } else {
                                val gallery = history.add(bmp, settings.format, tenth, entries, settings.saveToGallery)
                                if (gallery) msgSavedGallery else msgSaved
                            }
                            capturing = false
                            snackbar.showSnackbar(message)
                        }
                    }
                }
                LabeledRoundButton(
                    icon = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    label = stringResource(if (locked) R.string.action_unlock else R.string.action_lock),
                    active = locked,
                ) {
                    locked = !locked
                    holder.measureState.locked = locked
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }

        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 200.dp),
        )
    }

    longPress?.let { lp ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { longPress = null }, sheetState = sheetState, containerColor = Pet.Stone) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(stringResource(R.string.menu_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                val ball = lp.ball
                if (ball != null) {
                    if (ball.kind == BallKind.BOULE) {
                        var current by remember { mutableStateOf(holder.measureState.teams[ball.id] ?: Team.NONE) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (t in Team.entries) {
                                FilterChip(
                                    selected = current == t,
                                    onClick = {
                                        holder.measureState.teams[ball.id] = t
                                        current = t
                                    },
                                    label = { Text(if (t == Team.NONE) stringResource(R.string.no_team) else teamName(t)) },
                                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(Pet.team(t))) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pet.StoneHigh),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    SheetAction(Icons.Rounded.DeleteOutline, stringResource(R.string.menu_remove)) {
                        view.removeBall(ball.id)
                        longPress = null
                    }
                } else {
                    SheetAction(Icons.Rounded.SportsBaseball, stringResource(R.string.menu_add_boule)) {
                        view.addBallAt(BallKind.BOULE, lp.x, lp.y) { ok -> if (!ok) scope.launch { snackbar.showSnackbar(msgNoGround) } }
                        longPress = null
                    }
                    SheetAction(Icons.Rounded.CenterFocusStrong, stringResource(R.string.menu_set_jack)) {
                        view.addBallAt(BallKind.JACK, lp.x, lp.y) { ok -> if (!ok) scope.launch { snackbar.showSnackbar(msgNoGround) } }
                        longPress = null
                    }
                }
                TextButton(onClick = { longPress = null }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun Brand() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Pet.Glass)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Pet.Ochre))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 18.sp)
    }
}

@Composable
private fun GlassChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(CircleShape)
            .background(Pet.Glass)
            .border(1.dp, Pet.Lavender.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Pet.Lavender,
    )
}

@Composable
private fun StatusPill(status: MeasureStatus) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (fadeIn() + slideInVertically { -it / 2 }) togetherWith (fadeOut() + slideOutVertically { it / 2 })
        },
        label = "status",
    ) { s ->
        val (icon, text, tint) = statusVisual(s)
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Pet.Glass)
                .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun statusVisual(s: MeasureStatus): Triple<ImageVector, String, Color> = when (s) {
    MeasureStatus.STARTING -> Triple(Icons.Rounded.CameraAlt, stringResource(R.string.status_starting), Pet.Muted)
    MeasureStatus.SCAN -> Triple(Icons.Rounded.Sync, stringResource(R.string.status_scan), Pet.Lavender)
    MeasureStatus.FIND_JACK -> Triple(Icons.Rounded.CenterFocusStrong, stringResource(R.string.status_find_jack), Pet.Ochre)
    MeasureStatus.FIND_BOULES -> Triple(Icons.Rounded.Search, stringResource(R.string.status_find_boules), Pet.Ochre)
    MeasureStatus.MEASURING -> Triple(Icons.Rounded.Straighten, stringResource(R.string.status_measuring), Pet.Olive)
    MeasureStatus.LOCKED -> Triple(Icons.Rounded.Lock, stringResource(R.string.status_locked), Pet.Lavender)
    MeasureStatus.LOST_DARK -> Triple(Icons.Rounded.WbSunny, stringResource(R.string.status_dark), MaterialTheme.colorScheme.error)
    MeasureStatus.LOST_FAST -> Triple(Icons.Rounded.Speed, stringResource(R.string.status_fast), MaterialTheme.colorScheme.error)
    MeasureStatus.LOST_TEXTURE -> Triple(Icons.Rounded.Texture, stringResource(R.string.status_texture), MaterialTheme.colorScheme.error)
    MeasureStatus.LOST -> Triple(Icons.Rounded.ErrorOutline, stringResource(R.string.status_lost), MaterialTheme.colorScheme.error)
}

@Composable
private fun ResultsCard(ui: MeasureUiState, tenth: Boolean, teamName: (Team) -> String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Pet.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(14.dp),
    ) {
        Text(
            stringResource(R.string.closest_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Pet.Muted,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (ui.distances.isEmpty()) {
            Text(stringResource(R.string.no_measure), color = Pet.Muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            ui.distances.forEachIndexed { index, d ->
                val closest = index == 0
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (closest) Pet.Gold.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (closest) Pet.Gold.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Pet.team(d.team))
                            .border(1.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(teamName(d.team), style = MaterialTheme.typography.titleMedium)
                        if (closest) {
                            Text(stringResource(R.string.closest), style = MaterialTheme.typography.labelSmall, color = Pet.Gold)
                        }
                    }
                    Text(
                        formatDistance(d.distanceM, tenth),
                        style = if (closest) DistanceStyle else DistanceStyle.copy(fontSize = 22.sp),
                        color = if (closest) Pet.Gold else Pet.Cream,
                    )
                }
            }
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) Pet.Ochre else Pet.Ochre.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.CameraAlt, stringResource(R.string.action_capture), tint = Color(0xFF2A1A05), modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun LabeledRoundButton(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        RoundIconButton(icon, label, size = 56, active = active, onClick = onClick)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Pet.Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, size: Int, active: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) Pet.Lavender else Pet.Glass,
        contentColor = if (active) Color(0xFF1E1533) else Pet.Cream,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, modifier = Modifier.size((size * 0.46f).dp))
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Pet.StoneHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Pet.Lavender) }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is android.content.ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
