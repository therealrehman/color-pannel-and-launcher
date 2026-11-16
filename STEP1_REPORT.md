# Step 1 report: Home button wali problem (Glassy Launcher 1.1.13)

## 1. Problem
- Phone restart hone ke baad Glassy launcher khulta hai.
- Chrome ya koi aur app kholne ke baad Home press karne par phone ka apna (stock) launcher khulta hai.
- App ka "Home app: yes" check sirf Android ka Role dekhta tha, isliye app ko kuch pata hi nahi chalta tha.

## 2. Pichle chat ki research: check ka result
Sources ke pages mere paas nahi aaye. Mere paas sirf summary (screenshots) thi. Har claim ko code aur STEPS.md se mila kar dekha.

**Sahi, code se confirm:**
- "Home app: yes" check sirf Role dekhta tha. Ye teen jagah tha: `HomeKeeper.isDefault`, `LauncherActivity.isDefaultHome`, `HomeSettingsActivity.isDefaultHome`. Ab HOME button ka asli jawab dekha jata hai.
- Role "yes" hona aur Home press par stock khulna, dono ek saath possible hain. Ye mismatch aapke symptom se match karta hai.

**Confirm nahi hua (sirf summary mein):**
- "Android chupke se preferred activity drop kar deta hai jab naya Home-type component aaye." Iska koi source-level proof mere paas nahi hai. Wellbeing aur OEM kids/game mode wali example bhi verify nahi hui. Ye phone-specific ho sakta hai. Code ise "fix" nahi karta, sirf pakadta hai (log). Trigger log se pakdna hai.
- Auto-launch wali theory (STEPS Step 76): aapke case mein restart ke baad launcher khulta hai, isliye ye main cause nahi lagta.
- Launcher3, Lawnchair, Olauncher, Nova ka actual code: summary mein unka result nahi tha, aur is sandbox se unka code nahi dekh paaya. Step 2 mein.
- STEPS Step 84 ki "known causes" list (vivo, ColorOS, Fairphone, Google Dialer...) user reports ki list hai. Verify nahi kiya.

**Research mein nahi tha, lekin code mein mila (zyada zaroori):**
- Manifest ke `<queries>` mein HOME intent nahi tha. Android 11+ par dusra launcher hidden ho sakta hai, to check galat "yes" de sakta tha. (Add kar diya.)
- v5 mein 8-second Home watch aur Home notification pehle hi hata diye gaye the (Step 86, store review). Isliye koi Home change pakda hi nahi ja raha tha. (Quiet log wapas, koi prompt nahi.)
- Package id mismatch: STEPS Step 75 kehta hai `com.arapks.glassylauncher`, lekin build.gradle, shortcuts.xml aur KeepAlive mein `com.oklabs.oneui9launcher` hai. Id maine nahi badli. (Step 2 mein aapka decision.)

## 3. Kya change kiya
- **HomeKeeper.kt:** `isDefault()` ab wahi dekhta hai jo HOME button khol raha hai. Role sirf fallback hai. Naye helpers: `roleHeld()`, `holderLabel()`. Log line batati hai: "the Home button now opens <app> (role: ours / not ours)".
- **HomeRoleActivity:** Role already hamara ho par HOME button kisi aur app ko khol raha ho, to Toast kehta hai "Pick another Home app first, then pick Glassy Launcher again" aur Home app page khulta hai.
- **LauncherActivity.kt:** chip "Set as default home app" ab sahi state dikhata hai. Chip aur ek-baar wala question dono HomeRoleActivity use karte hain. Launcher khulne par quiet log line likhi jati hai.
- **HomeSettingsActivity.kt:** "Set as default" row sahi state dikhata hai. Default na ho to HomeRoleActivity khulta hai, default ho to Home app page. Naya row "Home app log" (tap = copy).
- **OverlayPanel.kt:** panel service chalne tak har 30 second quiet log check. Ye sirf log likhta hai, kuch dikhata ya kholta nahi.
- **KeepAlive.kt:** boot / update wali line hamesha; 15-minute wali line sirf tab jab HOME button kisi aur app ko khol raha ho.
- **AndroidManifest.xml:** `<queries>` mein MAIN + HOME intent.
- **build.gradle.kts:** versionName 1.1.13, versionCode 213.
- **STEPS.md:** Step 90 entry.

Ye jaan-boojh kar NAHI badla: notification, automatic Home dialog (store review), applicationId / store listing, Ui9 merge.

## 4. Phone par test (zip build karne ke baad)
1. Same package wali app ke upar update install karein. Agar `com.arapks...` wali alag app bhi installed hai, use uninstall karein, warna Home galat app par ja sakta hai.
2. Glassy ko Home app banayein (Home press > Glassy Launcher > Always).
3. Phone restart karein. Chrome kholen, Home press karein.
4. Agar stock khule: Glassy ko app drawer se kholen. Home screen par "Set as default home app" chip dikhni chahiye.
5. Settings mein "Home app log" row tap karke copy karein aur mujhe bhejein.
6. Chip tap karein: Home app page par pehle koi aur launcher chuniye, phir Glassy Launcher chuniye. Phir Home press karke check karein.

Note: stock khulne par notification nahi aayega (store review ki wajah se). Glassy kholne par hi chip dikhegi.

## 5. Kya reh gaya (Step 2)
- Log se trigger pakdna: kaun sa app Home le raha hai aur kab.
- Decision: stock khulne par notification chahiye? Store review ka risk hai. Agar haan, to ek baar per episode, bina auto-open ke, quiet notification add karenge.
- Store package id: `com.oklabs.oneui9launcher` (code) ya `com.arapks.glassylauncher` (docs/store). Upload se pehle decide karna hai.
- Auto-launch (vivo / Huawei / Xiaomi) setting: sirf tab check karein agar log mein "boot" line na aaye.
- Nova, Lawnchair, Olauncher ka code check.
- Pehle wale Ui9 merge request ka status (is zip mein kitna hua hai).

## 6. Honest limits
- Ye code compile nahi hua (sandbox mein Android SDK nahi). Build CI ya Android Studio se karein.
- Phone par test nahi hua.
- Koi bhi normal app launcher ko silently Home nahi rakh sakti; ye Android ka rule hai. Ye fix detect karke ek tap mein wapas lene ka hai.
