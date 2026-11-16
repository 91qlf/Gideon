package app.gideon.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class UserStore(context: Context) {
    private val prefs = context.getSharedPreferences("gideon", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val savedSerializer = ListSerializer(NewsItem.serializer())
    private val affinitySerializer = MapSerializer(String.serializer(), Float.serializer())

    var liked: Set<String>
        get() = prefs.getStringSet("liked", emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit { putStringSet("liked", v) }

    var saved: List<NewsItem>
        get() = runCatching { json.decodeFromString(savedSerializer, prefs.getString("saved_v2", "[]")!!) }
            .getOrDefault(emptyList())
        set(v) = prefs.edit { putString("saved_v2", json.encodeToString(savedSerializer, v)) }

    var disabledGroups: Set<String>
        get() = prefs.getStringSet("disabled_v2", emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit { putStringSet("disabled_v2", v) }

    var affinity: Map<String, Float>
        get() = runCatching { json.decodeFromString(affinitySerializer, prefs.getString("affinity", "{}")!!) }
            .getOrDefault(emptyMap())
        set(v) = prefs.edit { putString("affinity", json.encodeToString(affinitySerializer, v)) }

    var dailyBrief: Boolean
        get() = prefs.getBoolean("daily_brief", true)
        set(v) = prefs.edit { putBoolean("daily_brief", v) }

    var soundOn: Boolean
        get() = prefs.getBoolean("sound_on", true)
        set(v) = prefs.edit { putBoolean("sound_on", v) }

    var includeShorts: Boolean
        get() = prefs.getBoolean("include_shorts", false)
        set(v) = prefs.edit { putBoolean("include_shorts", v) }

    var textScale: Float
        get() = prefs.getFloat("text_scale", 1f)
        set(v) = prefs.edit { putFloat("text_scale", v) }

    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notif", false)
        set(v) = prefs.edit { putBoolean("asked_notif", v) }
}
