# YouTube Ads Blocker - Android App

## Kya Hai Ye App?
Ye ek Android app hai jo **local VPN** ke zariye YouTube ke ad servers ko block karta hai.  
**Bina root ke kaam karta hai!** 🎉

---

## App Kaise Kaam Karta Hai?

```
📱 Aapka Phone
    ↓
🔒 Local VPN (sirf aapke phone pe)
    ↓
🔍 DNS Packets Check karta hai
    ↓
❌ Ad domains block → NXDOMAIN response
✅ YouTube content pass → Normal internet
```

## Block Hone Wale Domains (50+)
- `googleadservices.com`
- `doubleclick.net`
- `ads.youtube.com`
- `pagead2.googlesyndication.com`
- aur bahut saare...

---

## Android Studio Mein Kaise Open Karein?

### Step 1: Android Studio Download karein
👉 https://developer.android.com/studio

### Step 2: Project Open karein
1. Android Studio kholo
2. `File → Open` click karo
3. `C:\Users\mohdk\Desktop\AdZero Privacy Filter` folder select karo
4. **OK** dabao

### Step 3: Build karein
1. Wait karo Gradle sync ke liye (~2-3 minutes)
2. Apna phone connect karo USB se (ya emulator use karo)
3. `Run → Run 'app'` click karo (ya green ▶️ button)

### Step 4: Phone pe Install
1. Phone pe `Developer Options` enable karein
2. `USB Debugging` on karein
3. App automatically install ho jayega

---

## App Use Karna
1. App kholo
2. **Big button** tap karo
3. VPN permission maange to **Allow** karo
4. Status `PROTECTED` ho jayega ✅
5. Ab YouTube kholo → **Ads nahi aayenge!** 🎉

---

## Files Structure
```
AdZero Privacy Filter/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml     ← Permissions
│   │   ├── java/com/youtubeadsblocker/
│   │   │   ├── MainActivity.kt     ← Main Screen
│   │   │   ├── AdBlockVpnService.kt ← Core Blocking
│   │   │   └── BootReceiver.kt     ← Auto-start
│   │   └── res/
│   │       ├── layout/activity_main.xml ← UI Design
│   │       └── drawable/           ← Icons & Shapes
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

---

> ⚠️ **Note:** Pehli baar app start karne pe phone VPN permission maangega. **Allow** karo — ye sirf local hai, koi data bahar nahi jata!
