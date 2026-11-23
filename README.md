# Auto Clicker

แอป auto click สำหรับ Android แบบ **ฟรี ไม่มีโฆษณา ไม่มี subscription ไม่เก็บข้อมูล** เหมาะกับเกม idle ที่ต้องกดรับรางวัลซ้ำ ๆ

A free, ad-free, open-source auto clicker for Android (11+). No root, no tracking.

## ฟีเจอร์

- แผงควบคุมลอยบนหน้าจอ ลากย้ายได้
- วางจุดคลิกได้หลายจุด แต่ละจุดตั้งความถี่เองได้ (50ms – 10 นาที)
- สุ่มตำแหน่ง/จังหวะเล็กน้อยได้ (ตั้งค่าในแอป)
- หยุดอัตโนมัติตามเวลาที่ตั้งไว้
- จำตำแหน่งจุดคลิกไว้แม้ปิดแอป
- ใช้สิทธิ์ Accessibility อย่างเดียว ไม่มีสิทธิ์อินเทอร์เน็ต

## ติดตั้ง

1. ดาวน์โหลด `AutoClicker-vX.Y.Z.apk` จากหน้า [Releases](../../releases/latest)
2. เปิดไฟล์บนมือถือ แล้วอนุญาต "ติดตั้งแอปที่ไม่รู้จัก" ให้แอปที่ใช้เปิดไฟล์
   - Samsung: ถ้าติดตั้งไม่ได้ ให้ปิด **Auto Blocker** ชั่วคราว (Settings → Security and privacy)
3. เปิดแอป Auto Clicker → กด **"1. เปิดสิทธิ์ Accessibility"** → Installed apps → Auto Clicker → เปิด
   - ถ้าขึ้นว่า **Restricted setting**: Settings → Apps → Auto Clicker → ⋮ → **Allow restricted settings** แล้วเปิดใหม่

## วิธีใช้

| ปุ่ม | หน้าที่ |
|---|---|
| ⠿ | ลากเพื่อย้ายแผงควบคุม |
| ▶ / ■ | เริ่ม / หยุด |
| + | เพิ่มจุดคลิก แล้วลากวงกลมแดงไปวางบนปุ่มในเกม |
| − | ลบจุดล่าสุด |
| ✕ | ซ่อนแผง (เปิดใหม่ได้จากในแอป) |

- แตะวงกลมเพื่อตั้งความถี่ของจุดนั้นหรือลบจุดนั้น
- ระหว่างคลิก วงกลมจะจางและแตะไม่ได้ ต้องกด ■ ก่อนถึงจะแก้ไขได้
- อย่าวางจุดคลิกทับแผงควบคุม
- ถ้าแอปถูกปิดเองตอนเล่นนาน ๆ: Settings → Apps → Auto Clicker → Battery → **Unrestricted**

> ⚠️ เกมออนไลน์บางเกมห้ามใช้ auto clicker ในเงื่อนไขการใช้งาน ใช้อย่างรับผิดชอบ

## Build เอง

ต้องมี JDK 17 + Android SDK (API 36) เช่นจาก Android Studio หรือ Unity Android Build Support

```bash
./build.sh            # debug APK
./build.sh install    # ติดตั้ง debug ลงมือถือที่ต่อ USB
./build.sh release    # release APK (ต้องมี keystore.properties)
```

`build.sh` หา Gradle/JDK/SDK จาก Unity โดยอัตโนมัติ ถ้าใช้ Android Studio ให้รัน `gradle assembleDebug` ตามปกติและตั้ง `sdk.dir` ใน `local.properties`

## License

[MIT](LICENSE)
