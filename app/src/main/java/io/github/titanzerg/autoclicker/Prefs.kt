package io.github.titanzerg.autoclicker

import android.content.Context

data class SavedTarget(val x: Int, val y: Int, val intervalMs: Long)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("autoclicker", Context.MODE_PRIVATE)

    /** Interval given to a newly added click point. */
    var defaultIntervalMs: Long
        get() = sp.getLong("defaultInterval", 200L)
        set(v) = sp.edit().putLong("defaultInterval", v).apply()

    /** How long each tap is held down. */
    var tapDurationMs: Long
        get() = sp.getLong("tapDuration", 20L)
        set(v) = sp.edit().putLong("tapDuration", v).apply()

    /** Random offset (+/- px) applied to each tap position. 0 = exact. */
    var positionJitterPx: Int
        get() = sp.getInt("posJitter", 0)
        set(v) = sp.edit().putInt("posJitter", v).apply()

    /** Random variation (+/- %) applied to each interval. 0 = exact. */
    var timingJitterPercent: Int
        get() = sp.getInt("timeJitter", 0)
        set(v) = sp.edit().putInt("timeJitter", v).apply()

    /** Stop automatically after this many minutes. 0 = never. */
    var autoStopMinutes: Int
        get() = sp.getInt("autoStop", 0)
        set(v) = sp.edit().putInt("autoStop", v).apply()

    var targets: List<SavedTarget>
        get() = sp.getString("targets", "").orEmpty()
            .split(";")
            .mapNotNull { entry ->
                val p = entry.split(",")
                if (p.size != 3) return@mapNotNull null
                val x = p[0].toIntOrNull() ?: return@mapNotNull null
                val y = p[1].toIntOrNull() ?: return@mapNotNull null
                val ms = p[2].toLongOrNull() ?: return@mapNotNull null
                SavedTarget(x, y, ms)
            }
        set(v) = sp.edit()
            .putString("targets", v.joinToString(";") { "${it.x},${it.y},${it.intervalMs}" })
            .apply()
}
