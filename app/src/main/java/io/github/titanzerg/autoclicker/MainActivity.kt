package io.github.titanzerg.autoclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var interval: EditText
    private lateinit var duration: EditText
    private lateinit var posJitter: EditText
    private lateinit var timeJitter: EditText
    private lateinit var autoStop: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(32))
        }

        root.addView(TextView(this).apply { text = "Auto Clicker"; textSize = 26f })
        status = TextView(this).apply { textSize = 15f; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(status)

        root.addView(button("1. เปิดสิทธิ์ Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("2. แสดงแผงควบคุม") {
            val service = ClickService.instance
            if (service == null) {
                toast("ยังไม่ได้เปิดสิทธิ์ Accessibility")
            } else {
                service.showPanel()
                moveTaskToBack(true)
            }
        })

        root.addView(header("ตั้งค่า"))
        interval = field(root, "ระยะคลิกเริ่มต้นของจุดใหม่ (ms)", prefs.defaultIntervalMs.toString())
        duration = field(root, "กดค้างต่อครั้ง (ms)", prefs.tapDurationMs.toString())
        posJitter = field(root, "สุ่มตำแหน่ง ± (px, 0 = ไม่สุ่ม)", prefs.positionJitterPx.toString())
        timeJitter = field(root, "สุ่มจังหวะ ± (%, 0 = ไม่สุ่ม)", prefs.timingJitterPercent.toString())
        autoStop = field(root, "หยุดอัตโนมัติหลัง (นาที, 0 = ไม่หยุด)", prefs.autoStopMinutes.toString())
        root.addView(button("บันทึกการตั้งค่า") { save() })

        root.addView(header("วิธีใช้"))
        root.addView(TextView(this).apply {
            textSize = 14f
            text = """
                • ⠿ ลากเพื่อย้ายแผงควบคุม
                • + เพิ่มจุดคลิก แล้วลากวงกลมสีแดงไปวางบนปุ่มในเกม
                • แตะวงกลมเพื่อตั้งระยะเวลาของจุดนั้น หรือลบจุดนั้น
                • ▶ เริ่ม / ■ หยุด  (− ลบจุดล่าสุด, ✕ ซ่อนแผง)
                • ตอนกำลังคลิก วงกลมจะจางลงและแตะไม่ได้ ให้กด ■ ก่อนจึงจะแก้ไขได้
                • อย่าวางจุดคลิกทับแผงควบคุม
                • การตั้งค่าในหน้านี้จะมีผลตอนกด ▶ ครั้งถัดไป
            """.trimIndent()
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (ClickService.instance != null) {
            "สถานะ: ✅ เปิดสิทธิ์แล้ว"
        } else {
            "สถานะ: ⚠️ ยังไม่ได้เปิดสิทธิ์ Accessibility\n" +
                "ถ้าเปิดไม่ได้ (Restricted setting) ให้ไปที่ App info → ⋮ → Allow restricted settings ก่อน"
        }
    }

    private fun save() {
        prefs.defaultIntervalMs = interval.long(200).coerceIn(40, 600_000)
        prefs.tapDurationMs = duration.long(20).coerceIn(1, 5_000)
        prefs.positionJitterPx = posJitter.long(0).coerceIn(0, 200).toInt()
        prefs.timingJitterPercent = timeJitter.long(0).coerceIn(0, 90).toInt()
        prefs.autoStopMinutes = autoStop.long(0).coerceIn(0, 24 * 60).toInt()
        toast("บันทึกแล้ว")
    }

    private fun EditText.long(default: Long) = text.toString().trim().toLongOrNull() ?: default

    private fun field(parent: LinearLayout, title: String, value: String): EditText {
        parent.addView(TextView(this).apply { text = title; setPadding(0, dp(10), 0, 0) })
        return EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value)
            parent.addView(this)
        }
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        setPadding(0, dp(24), 0, dp(4))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setOnClickListener { onClick() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
