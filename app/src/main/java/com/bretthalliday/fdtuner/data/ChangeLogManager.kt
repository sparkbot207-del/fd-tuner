package com.bretthalliday.fdtuner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ChangeLogEntry(
    val timestamp: String,   // ISO-8601
    val paramAddr: Int,
    val paramName: String,
    val oldValue: Int,
    val newValue: Int,
    val isDemo: Boolean
)

/**
 * Stores param change history in SharedPreferences as a JSON array.
 * Keeps the last 200 entries (newest-first). Thread-safe for reads;
 * writes should be made from a single coroutine context.
 */
object ChangeLogManager {
    private const val PREFS_NAME = "fd_changelog"
    private const val KEY_ENTRIES = "log_entries"
    private const val MAX_ENTRIES = 200

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun nowIso(): String = isoFmt.format(Date())

    /** Prepend a new entry, dropping the oldest if over MAX_ENTRIES. */
    fun log(ctx: Context, entry: ChangeLogEntry) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = try {
            JSONArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

        val newObj = JSONObject().apply {
            put("ts", entry.timestamp)
            put("addr", entry.paramAddr)
            put("name", entry.paramName)
            put("old", entry.oldValue)
            put("new", entry.newValue)
            put("demo", entry.isDemo)
        }

        val updated = JSONArray()
        updated.put(newObj)
        for (i in 0 until minOf(existing.length(), MAX_ENTRIES - 1)) {
            updated.put(existing.getJSONObject(i))
        }

        prefs.edit().putString(KEY_ENTRIES, updated.toString()).apply()
    }

    /** Returns all entries newest-first. */
    fun getAll(ctx: Context): List<ChangeLogEntry> {
        val raw = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    ChangeLogEntry(
                        timestamp = o.getString("ts"),
                        paramAddr = o.getInt("addr"),
                        paramName = o.getString("name"),
                        oldValue = o.getInt("old"),
                        newValue = o.getInt("new"),
                        isDemo = o.optBoolean("demo", false)
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENTRIES, "[]").apply()
    }
}
