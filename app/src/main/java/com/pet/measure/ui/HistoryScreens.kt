package com.pet.measure.ui

import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pet.measure.R
import com.pet.measure.data.GameFormat
import com.pet.measure.data.HistoryEntry
import com.pet.measure.data.HistoryRecord
import com.pet.measure.data.HistoryStore
import com.pet.measure.data.formatDistance
import com.pet.measure.ui.theme.DistanceStyle
import com.pet.measure.ui.theme.Pet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(history: HistoryStore, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val records by history.records.collectAsState()
    Scaffold(
        containerColor = Pet.Night,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { BackButton(onBack) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Pet.Night),
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(Pet.Lavender.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.PhotoLibrary, null, tint = Pet.Lavender, modifier = Modifier.size(44.dp)) }
                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.history_empty_body),
                        color = Pet.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryCard(history, record) { onOpen(record.id) }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: HistoryStore, record: HistoryRecord, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Pet.Stone)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = rememberPhoto(history, record.id, 300)
        Box(
            Modifier.size(width = 84.dp, height = 112.dp).clip(RoundedCornerShape(14.dp)).background(Pet.StoneHigh),
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(formatDate(record.timestamp), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (record.format == GameFormat.DOUBLETTE) R.string.format_doublette_short else R.string.format_triplette_short) +
                    " · " + pluralStringResource(R.plurals.boule_count, record.entries.size, record.entries.size),
                style = MaterialTheme.typography.bodySmall,
                color = Pet.Muted,
            )
            Spacer(Modifier.height(8.dp))
            record.entries.take(3).forEachIndexed { i, e ->
                EntryRow(e, record.tenthPrecision, closest = i == 0, compact = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(history: HistoryStore, id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val record = history.find(id)
    var confirmDelete by remember { mutableStateOf(false) }
    val shareSubject = stringResource(R.string.share_subject)
    Scaffold(
        containerColor = Pet.Night,
        topBar = {
            TopAppBar(
                title = { Text(record?.let { formatDate(it.timestamp) } ?: "") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (record != null) {
                        IconButton(onClick = {
                            val text = shareSubject + "\n" + record.entries.joinToString("\n") { e ->
                                "${e.teamName}: ${formatDistance(e.distanceM, record.tenthPrecision)}"
                            }
                            context.startActivity(Intent.createChooser(history.shareIntent(record, text), shareSubject))
                        }) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.delete)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Pet.Night),
            )
        },
    ) { padding ->
        if (record == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val photo = rememberPhoto(history, record.id, 1440)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pet.Stone)
                    .then(if (photo != null) Modifier.aspectRatio(photo.width.toFloat() / photo.height) else Modifier.height(320.dp)),
            ) {
                photo?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pet.Stone).padding(14.dp),
            ) {
                Text(
                    stringResource(R.string.closest_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Pet.Muted,
                )
                Spacer(Modifier.height(6.dp))
                record.entries.forEachIndexed { i, e -> EntryRow(e, record.tenthPrecision, closest = i == 0, compact = false) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete && record != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    history.delete(record.id)
                    onBack()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = Pet.Stone,
        )
    }
}

@Composable
private fun EntryRow(e: HistoryEntry, tenth: Boolean, closest: Boolean, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (compact) 8.dp else 12.dp).clip(CircleShape).background(Pet.team(e.team)))
        Spacer(Modifier.width(10.dp))
        Text(
            e.teamName.ifBlank { stringResource(R.string.boule) },
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            formatDistance(e.distanceM, tenth),
            style = if (compact) MaterialTheme.typography.titleSmall else DistanceStyle.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize),
            color = if (closest) Pet.Gold else Pet.Cream,
        )
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
}

@Composable
private fun rememberPhoto(history: HistoryStore, id: String, width: Int): Bitmap? {
    val bmp by produceState<Bitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { history.loadThumbnail(id, width) }
    }
    return bmp
}

@Composable
private fun formatDate(timestamp: Long): String {
    val context = LocalContext.current
    val date = Date(timestamp)
    return DateFormat.getMediumDateFormat(context).format(date) + " · " + DateFormat.getTimeFormat(context).format(date)
}
