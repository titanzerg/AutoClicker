package io.github.titanzerg.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Accessibility service that draws a floating control panel plus draggable
 * click targets, and taps each target on its own interval via dispatchGesture().
 */
class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
            private set

        private const val MIN_INTERVAL_MS = 40L

        val INTERVAL_STEPS = longArrayOf(
            50, 100, 150, 200, 300, 500, 750,
            1_000, 2_000, 3_000, 5_000, 10_000, 15_000, 30_000,
            60_000, 120_000, 300_000, 600_000,
        )

        fun formatInterval(ms: Long): String = when {
            ms < 1_000 -> "${ms}ms"
            ms < 60_000 -> if (ms % 1_000 == 0L) "${ms / 1_000}s" else "%.1fs".format(ms / 1_000.0)
            else -> if (ms % 60_000 == 0L) "${ms / 60_000}m" else "%.1fm".format(ms / 60_000.0)
        }
    }

    private class Target(
        val view: TextView,
        val params: WindowManager.LayoutParams,
        var intervalMs: Long,
    ) {
        var nextDue = 0L
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val targets = mutableListOf<Target>()

    private var panel: View? = null
    private var playButton: TextView? = null
    private var editor: View? = null

    var isRunning = false
        private set

    // Settings snapshot taken when a run starts.
    private var tapDurationMs = 20L
    private var posJitter = 0
    private var timeJitter = 0

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = stop()

    override fun onUnbind(intent: Intent?): Boolean {
        hidePanel()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hidePanel()
        instance = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- panel

    fun showPanel() {
        if (panel != null) return

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xDD202124.toInt(), dp(14))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val params = overlayParams(dp(8), dp(160))

        val handle = iconButton("⠿")
        playButton = iconButton("▶") { toggle() }
        box.addView(handle)
        box.addView(playButton)
        box.addView(iconButton("+") { addTarget() })
        box.addView(iconButton("−") { targets.lastOrNull()?.let { removeTarget(it) } })
        box.addView(iconButton("✕") { hidePanel() })

        makeDraggable(handle, box, params)
        wm.addView(box, params)
        panel = box

        prefs.targets.forEach { addTarget(it.x, it.y, it.intervalMs, save = false) }
    }

    fun hidePanel() {
        if (panel == null) return
        stop()
        closeEditor()
        saveTargets()
        targets.forEach { removeView(it.view) }
        targets.clear()
        removeView(panel)
        panel = null
        playButton = null
    }

    // ---------------------------------------------------------------- targets

    private fun addTarget(
        x: Int? = null,
        y: Int? = null,
        intervalMs: Long = prefs.defaultIntervalMs,
        save: Boolean = true,
    ) {
        if (isRunning) return
        val size = dp(56)
        val bounds = screenBounds()
        val offset = targets.size * dp(24)
        val params = overlayParams(
            x ?: (bounds.first / 2 - size / 2 + offset),
            y ?: (bounds.second / 2 - size / 2 + offset),
            size, size,
        )
        val view = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66E53935)
                setStroke(dp(3), 0xFFE53935.toInt())
            }
        }
        val target = Target(view, params, intervalMs)
        makeDraggable(view, view, params, onTap = { openEditor(target) }, onMoved = { saveTargets() })
        wm.addView(view, params)
        targets += target
        relabel()
        if (save) saveTargets()
    }

    private fun removeTarget(t: Target) {
        if (isRunning) return
        removeView(t.view)
        targets.remove(t)
        relabel()
        saveTargets()
    }

    private fun relabel() {
        targets.forEachIndexed { i, t -> t.view.text = "${i + 1}\n${formatInterval(t.intervalMs)}" }
    }

    private fun saveTargets() {
        prefs.targets = targets.map { SavedTarget(it.params.x, it.params.y, it.intervalMs) }
    }

    private fun setTouchable(t: Target, touchable: Boolean) {
        t.params.flags = if (touchable) {
            t.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            t.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        wm.updateViewLayout(t.view, t.params)
    }

    // ---------------------------------------------------------------- editor popup

    private fun openEditor(t: Target) {
        if (isRunning) return
        closeEditor()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(0xEE202124.toInt(), dp(16))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        box.addView(label("จุดที่ ${targets.indexOf(t) + 1} — คลิกทุก", 15f))

        val value = label(formatInterval(t.intervalMs), 22f).apply {
            minWidth = dp(96)
            gravity = Gravity.CENTER
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(iconButton("−") {
            t.intervalMs = INTERVAL_STEPS.lastOrNull { it < t.intervalMs } ?: INTERVAL_STEPS.first()
            value.text = formatInterval(t.intervalMs)
            relabel()
        })
        row.addView(value)
        row.addView(iconButton("+") {
            t.intervalMs = INTERVAL_STEPS.firstOrNull { it > t.intervalMs } ?: INTERVAL_STEPS.last()
            value.text = formatInterval(t.intervalMs)
            relabel()
        })
        box.addView(row)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(textButton("ลบจุดนี้", 0xFFC62828.toInt()) { closeEditor(); removeTarget(t) })
        actions.addView(textButton("เสร็จ", 0xFF1E88E5.toInt()) { closeEditor(); saveTargets() })
        box.addView(actions)

        val params = overlayParams(0, 0).apply { gravity = Gravity.CENTER }
        wm.addView(box, params)
        editor = box
    }

    private fun closeEditor() {
        removeView(editor)
        editor = null
    }

    // ---------------------------------------------------------------- run loop

    private fun toggle() = if (isRunning) stop() else start()

    private fun start() {
        if (targets.isEmpty()) {
            Toast.makeText(this, "กด + เพื่อเพิ่มจุดคลิกก่อน", Toast.LENGTH_SHORT).show()
            return
        }
        closeEditor()
        saveTargets()
        tapDurationMs = prefs.tapDurationMs.coerceIn(1, 5_000)
        posJitter = prefs.positionJitterPx.coerceAtLeast(0)
        timeJitter = prefs.timingJitterPercent.coerceIn(0, 90)

        isRunning = true
        val now = SystemClock.uptimeMillis()
        targets.forEachIndexed { i, t ->
            // Let injected taps pass through our own overlay to the game below.
            setTouchable(t, false)
            t.view.alpha = 0.45f
            t.nextDue = now + 300 + i * 60L
        }
        playButton?.text = "■"
        playButton?.setTextColor(0xFFFF5252.toInt())

        val minutes = prefs.autoStopMinutes
        if (minutes > 0) handler.postDelayed(autoStop, minutes * 60_000L)
        handler.post(tick)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(tick)
        handler.removeCallbacks(autoStop)
        targets.forEach {
            setTouchable(it, true)
            it.view.alpha = 1f
        }
        playButton?.text = "▶"
        playButton?.setTextColor(Color.WHITE)
    }

    private val autoStop = Runnable {
        stop()
        Toast.makeText(this, "หยุดอัตโนมัติตามเวลาที่ตั้งไว้", Toast.LENGTH_SHORT).show()
    }

    /**
     * Only one gesture can be in flight at a time (a new one cancels the old),
     * so targets are served one after another: earliest due first.
     */
    private val tick = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (!isRunning) return
            val t = targets.minByOrNull { it.nextDue }
            if (t == null) {
                stop()
                return
            }
            val now = SystemClock.uptimeMillis()
            if (t.nextDue > now) {
                handler.postDelayed(this, t.nextDue - now)
                return
            }
            t.nextDue = now + jittered(t.intervalMs)
            if (!tap(t) { handler.post(this) }) handler.postDelayed(this, 100)
        }
    }

    private fun jittered(ms: Long): Long {
        val varied = if (timeJitter == 0) ms else ms + ms * Random.nextInt(-timeJitter, timeJitter + 1) / 100
        return varied.coerceAtLeast(MIN_INTERVAL_MS)
    }

    private fun tap(t: Target, done: () -> Unit): Boolean {
        val loc = IntArray(2)
        t.view.getLocationOnScreen(loc)
        fun jitter() = if (posJitter == 0) 0 else Random.nextInt(-posJitter, posJitter + 1)
        val x = (loc[0] + t.view.width / 2f + jitter()).coerceAtLeast(0f)
        val y = (loc[1] + t.view.height / 2f + jitter()).coerceAtLeast(0f)

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, tapDurationMs))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done()
            override fun onCancelled(gestureDescription: GestureDescription?) = done()
        }, handler)
    }

    // ---------------------------------------------------------------- view helpers

    private fun overlayParams(
        x: Int,
        y: Int,
        w: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        h: Int = WindowManager.LayoutParams.WRAP_CONTENT,
    ) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    /** Drag [handle] to move [window]; a touch without movement counts as a tap. */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(
        handle: View,
        window: View,
        params: WindowManager.LayoutParams,
        onTap: (() -> Unit)? = null,
        onMoved: (() -> Unit)? = null,
    ) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var origX = 0
        var origY = 0
        var moved = false
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY
                    origX = params.x; origY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    if (!moved && hypot(dx, dy) > slop) moved = true
                    if (moved) {
                        val (sw, sh) = screenBounds()
                        params.x = (origX + dx.roundToInt()).coerceIn(0, (sw - window.width).coerceAtLeast(0))
                        params.y = (origY + dy.roundToInt()).coerceIn(0, (sh - window.height).coerceAtLeast(0))
                        wm.updateViewLayout(window, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (moved) onMoved?.invoke() else onTap?.invoke()
            }
            true
        }
    }

    private fun screenBounds(): Pair<Int, Int> {
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }

    private fun iconButton(text: String, onClick: (() -> Unit)? = null) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 20f
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        if (onClick != null) setOnClickListener { onClick() }
    }

    private fun textButton(text: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(color, dp(10))
        setPadding(dp(16), dp(10), dp(16), dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(dp(6), dp(10), dp(6), 0) }
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun removeView(v: View?) {
        if (v == null) return
        try {
            wm.removeView(v)
        } catch (_: IllegalArgumentException) {
            // already detached
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
