package com.bretthalliday.fdtuner.data

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedProfile(
    val name: String,
    val paramCount: Int,
    val savedAt: String,
    val params: Map<Int, Int>
)

/**
 * Stores/retrieves tuning profiles using SharedPreferences + JSON.
 * No external files — everything lives inside the app's private storage.
 */
object ProfileManager {

    private const val PREFS_NAME = "fd_profiles"
    private const val KEY_INDEX = "profile_index"
    private const val KEY_PREFIX = "profile_"
    private val DATE_FMT = SimpleDateFormat("MMM d yyyy  h:mm a", Locale.US)

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Save or overwrite a named profile with the current raw param map. */
    fun save(ctx: Context, name: String, params: Map<Int, Int>) {
        val p = prefs(ctx)
        val json = JSONObject().apply {
            put("name", name)
            put("savedAt", DATE_FMT.format(Date()))
            put("params", JSONObject().apply {
                params.forEach { (addr, value) -> put(addr.toString(), value) }
            })
        }
        // Update index
        val index = loadIndex(p).toMutableSet().apply { add(name) }
        p.edit()
            .putString(KEY_INDEX, index.joinToString("|"))
            .putString(KEY_PREFIX + name, json.toString())
            .apply()
    }

    /** Load all profile metadata (without full param maps) for the list screen. */
    fun listProfiles(ctx: Context): List<SavedProfile> {
        val p = prefs(ctx)
        return loadIndex(p).mapNotNull { name ->
            val raw = p.getString(KEY_PREFIX + name, null) ?: return@mapNotNull null
            try {
                val json = JSONObject(raw)
                val paramsJson = json.getJSONObject("params")
                SavedProfile(
                    name = name,
                    paramCount = paramsJson.length(),
                    savedAt = json.optString("savedAt", "Unknown"),
                    params = parseParams(paramsJson)
                )
            } catch (e: Exception) { null }
        }.sortedBy { it.name }
    }

    /** Load a single profile by name, returns the param map or null. */
    fun load(ctx: Context, name: String): Map<Int, Int>? {
        val raw = prefs(ctx).getString(KEY_PREFIX + name, null) ?: return null
        return try {
            parseParams(JSONObject(raw).getJSONObject("params"))
        } catch (e: Exception) { null }
    }

    /** Delete a profile by name. */
    fun delete(ctx: Context, name: String) {
        val p = prefs(ctx)
        val index = loadIndex(p).toMutableSet().apply { remove(name) }
        p.edit()
            .putString(KEY_INDEX, index.joinToString("|"))
            .remove(KEY_PREFIX + name)
            .apply()
    }

    fun profileExists(ctx: Context, name: String): Boolean =
        name in loadIndex(prefs(ctx))

    private fun loadIndex(p: android.content.SharedPreferences): Set<String> =
        p.getString(KEY_INDEX, "")
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: emptySet()

    private fun parseParams(json: JSONObject): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        json.keys().forEach { key ->
            map[key.toInt()] = json.getInt(key)
        }
        return map
    }
}
