package com.pet.measure.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HistoryEntry(val team: Team, val teamName: String, val distanceM: Float)

data class HistoryRecord(
    val id: String,
    val timestamp: Long,
    val format: GameFormat,
    val tenthPrecision: Boolean,
    val entries: List<HistoryEntry>,
)

/** Saved measurements: a JSON index plus one JPEG per record, all in app-private storage. */
class HistoryStore(private val context: Context) {
    private val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val index = File(context.filesDir, "history.json")
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<HistoryRecord>> = _records.asStateFlow()

    fun photoFile(id: String) = File(dir, "$id.jpg")

    fun find(id: String) = _records.value.firstOrNull { it.id == id }

    /** Saves the photo and its distances. Returns true if it was also copied to the gallery. */
    suspend fun add(
        photo: Bitmap, format: GameFormat, tenth: Boolean, entries: List<HistoryEntry>, toGallery: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val record = HistoryRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), format, tenth, entries)
        photoFile(record.id).outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        synchronized(this@HistoryStore) {
            _records.value = listOf(record) + _records.value
            save(_records.value)
        }
        toGallery && runCatching { saveToGallery(photo, record.timestamp) }.isSuccess
    }

    fun delete(id: String) {
        synchronized(this) {
            photoFile(id).delete()
            _records.value = _records.value.filterNot { it.id == id }
            save(_records.value)
        }
    }

    fun loadThumbnail(id: String, targetWidth: Int): Bitmap? {
        val file = photoFile(id)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun shareIntent(record: HistoryRecord, text: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile(record.id))
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun saveToGallery(photo: Bitmap, timestamp: Long) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "PET_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/P.E.T")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
        resolver.openOutputStream(uri)?.use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun load(): List<HistoryRecord> = runCatching {
        if (!index.exists()) return emptyList()
        val arr = JSONArray(index.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val entries = o.getJSONArray("entries")
            HistoryRecord(
                id = o.getString("id"),
                timestamp = o.getLong("timestamp"),
                format = runCatching { GameFormat.valueOf(o.getString("format")) }.getOrDefault(GameFormat.TRIPLETTE),
                tenthPrecision = o.optBoolean("tenth", false),
                entries = (0 until entries.length()).map { j ->
                    val e = entries.getJSONObject(j)
                    HistoryEntry(
                        team = runCatching { Team.valueOf(e.getString("team")) }.getOrDefault(Team.NONE),
                        teamName = e.optString("teamName", ""),
                        distanceM = e.getDouble("distance").toFloat(),
                    )
                },
            )
        }
    }.getOrDefault(emptyList())

    private fun save(records: List<HistoryRecord>) {
        val arr = JSONArray()
        for (r in records) {
            val entries = JSONArray()
            for (e in r.entries) {
                entries.put(JSONObject().put("team", e.team.name).put("teamName", e.teamName).put("distance", e.distanceM.toDouble()))
            }
            arr.put(
                JSONObject().put("id", r.id).put("timestamp", r.timestamp).put("format", r.format.name)
                    .put("tenth", r.tenthPrecision).put("entries", entries),
            )
        }
        val tmp = File(index.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(index)
    }
}
