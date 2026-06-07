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
    val params: Map<Int, Int>,
    val isDemo: Boolean = false
)

/**
 * Stores/retrieves tuning profiles using SharedPreferences + JSON.
 * Demo profiles and controller profiles are stored in separate buckets
 * and never mixed — pass isDemo=true to work with the demo store.
 */
object ProfileManager {

    private const val PREFS_CONTROLLER = "fd_profiles"
    private const val PREFS_DEMO       = "fd_profiles_demo"
    private const val KEY_INDEX        = "profile_index"
    private const val KEY_PREFIX       = "profile_"
    private val DATE_FMT = SimpleDateFormat("MMM d yyyy  h:mm a", Locale.US)

    private fun prefs(ctx: Context, isDemo: Boolean) =
        ctx.getSharedPreferences(
            if (isDemo) PREFS_DEMO else PREFS_CONTROLLER,
            Context.MODE_PRIVATE
        )

    /** Save or overwrite a named profile. */
    fun save(ctx: Context, name: String, params: Map<Int, Int>, isDemo: Boolean = false) {
        val p = prefs(ctx, isDemo)
        val json = JSONObject().apply {
            put("name", name)
            put("savedAt", DATE_FMT.format(Date()))
            put("isDemo", isDemo)
            put("params", JSONObject().apply {
                params.forEach { (addr, value) -> put(addr.toString(), value) }
            })
        }
        val index = loadIndex(p).toMutableSet().apply { add(name) }
        p.edit()
            .putString(KEY_INDEX, index.joinToString("|"))
            .putString(KEY_PREFIX + name, json.toString())
            .apply()
    }

    /** List all profiles from the correct bucket. */
    fun listProfiles(ctx: Context, isDemo: Boolean = false): List<SavedProfile> {
        val p = prefs(ctx, isDemo)
        return loadIndex(p).mapNotNull { name ->
            val raw = p.getString(KEY_PREFIX + name, null) ?: return@mapNotNull null
            try {
                val json = JSONObject(raw)
                val paramsJson = json.getJSONObject("params")
                SavedProfile(
                    name = name,
                    paramCount = paramsJson.length(),
                    savedAt = json.optString("savedAt", "Unknown"),
                    params = parseParams(paramsJson),
                    isDemo = isDemo
                )
            } catch (e: Exception) { null }
        }.sortedBy { it.name }
    }

    /** Load a single profile by name from the correct bucket. */
    fun load(ctx: Context, name: String, isDemo: Boolean = false): Map<Int, Int>? {
        val raw = prefs(ctx, isDemo).getString(KEY_PREFIX + name, null) ?: return null
        return try {
            parseParams(JSONObject(raw).getJSONObject("params"))
        } catch (e: Exception) { null }
    }

    /** Delete a profile from the correct bucket. */
    fun delete(ctx: Context, name: String, isDemo: Boolean = false) {
        val p = prefs(ctx, isDemo)
        val index = loadIndex(p).toMutableSet().apply { remove(name) }
        p.edit()
            .putString(KEY_INDEX, index.joinToString("|"))
            .remove(KEY_PREFIX + name)
            .apply()
    }

    fun profileExists(ctx: Context, name: String, isDemo: Boolean = false): Boolean =
        name in loadIndex(prefs(ctx, isDemo))

    private fun loadIndex(p: android.content.SharedPreferences): Set<String> =
        p.getString(KEY_INDEX, "")
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.toSet() ?: emptySet()

    private fun parseParams(json: JSONObject): Map<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        json.keys().forEach { key -> map[key.toInt()] = json.getInt(key) }
        return map
    }
}
