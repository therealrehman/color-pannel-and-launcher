# FINAL report: Glassy Launcher 1.1.14 (Step 1 + Step 2, final)

## 1. Aapke 12 screenshots mein kya tha
Ye doosri chat ki investigation thi ("Read 11 files, read 12 pages, 27 more steps"). Usne ye kiya:
- Manifest aur Home activity ka code padha. Conclusion: manifest sahi hai (singleTask, clearTaskOnLaunch, stateNotNeeded). Hum bhi agree karte hain. Launcher activity par `directBootAware` nahi hai.
- Web research: third-party launcher ka restart ke baad stock par reset hona; vivo / iQOO (Funtouch OS), ColorOS / OxygenOS, Fairphone, Realme, Huawei, Android 16 reports; RoleManager ROLE_HOME; Nova aur Launcher3 ka behaviour.
- Research ka summary: aam wajah launcher process ka kill hona (battery / auto-start restriction) bataya gaya.
- Direct-boot aur locked-state wali theory: restart ke baad Home preference filter ho sakti hai.
- Accessibility / overlay se "bounce back" wala tareeqa consider hua.
- github.com (Nova / Launcher3 ka code) fail hua. Is sandbox mein bhi internet nahi hai, isliye Nova ka code main bhi nahi dekh paaya.

## 2. Kya liya, kya nahi liya
Liya:
- Process-kill theory ko test karne ka tareeqa: app ke process ke band hone ki wajah (exit reason) log mein aur report mein.
- Package events: jab koi Home-capable app install / update / on-off / remove hoti hai, log mein time ke saath.
- Poori copy-able report (Settings > Home app log > tap = copy).
- Battery aur auto-start ke liye Settings ka row.

Nahi liya (aur kyun):
- Accessibility / overlay se bounce-back: store policy ke khilaf aur rejection ka khatra. Step 86 mein isi type ka forced behaviour reject hua tha.
- directBootAware: launcher unlock se pehle chal sakta hai, lekin tab credential-protected data (SharedPreferences) available nahi hota. Crash ka khatra. Abhi nahi.
- Notification ya automatic dialog: store review (Step 86). Ye aapka decision hai (neeche).
- Package id badalna: store listing ka decision.

## 3. Final changes (1.1.14, code 214)
- Step 90 (pehle se): HOME button ka asli check. Chip, Settings aur Setup usi par chalte hain. Manifest `<queries>` mein HOME. Quiet log. Home app log row.
- Step 91 (ye final):
  - HomeEventReceiver (manifest): PACKAGE_ADDED / REPLACED / REMOVED / CHANGED. Sirf wo packages jo Home le sakte hain. Locked phone par crash nahi hota.
  - HomeKeeper.lastExits(): Android 11+ par app ke last stops ka reason (user / system ya phone maker / battery limit / crash).
  - HomeKeeper.report(): role, HOME button kisko khol raha hai, dusre Home candidates, last stops, phone model, Android version, log.
  - Settings > Home app log > tap = poori report copy.
  - Settings > "Battery and auto-start" row: app ke Settings page par le jaata hai.
  - Candidate memory (hk_candidates): removed app ka naam bhi log mein aata hai.

## 4. Verify kya hua, kya nahi
- Done: brace balance (sab changed Kotlin files), sab HomeKeeper functions ke references defined, receiver manifest mein, `</application>` ek hi, version 214.
- Not done: compile (sandbox mein kotlinc / Android SDK nahi), phone par test.
- CI build mein dhyan: `ApplicationExitInfo` (Android 11 API) ka use SDK check ke andar hai. Agar lint "NewApi" error de, to bata dena.

## 5. Phone par test (ek baar)
1. CI / Android Studio se build karein. Same package `com.oklabs.oneui9launcher` ke upar install karein. Agar `com.arapks...` wali alag copy bhi installed hai, use uninstall karein.
2. Glassy kholen, Settings mein "Battery and auto-start" par jaayein: Battery ko Unrestricted, Auto-start ko On karein. (Menu ke naam phone ke hisaab se alag hote hain. Vivo ke liye aapke STEPS.md mein likha hai: iManager > App manager > Autostart; Battery > Background power consumption: allow.) Recents mein Glassy ko lock karein.
3. Glassy ko Home app banayein (Home press > Glassy Launcher > Always).
4. Phone restart karein. Chrome kholen, Home press karein.
5. Agar stock khule: Glassy kholen (app drawer se) > Settings > "Home app log" row > tap (copy) > poora text bhejein.
6. Fix abhi bhi: chip "Set as default home app" > Home app page > pehle koi aur launcher, phir Glassy.

## 6. Report padhne ka guide
- "stopped by the system or the phone maker" ya "battery or CPU limit", Home change se just pehle: phone ne app ko band kiya. Sabse likely case hai. Battery aur auto-start settings se fark padega.
- "was switched on / installed / updated", uske baad "the Home button now opens...": us app ne Home le liya. Uska naam bhejein.
- Koi package line nahi, sirf "now opens..." aur last stops "none recorded": Android ya phone ne khud badla. Boot ka time dekhein (restart ke turant baad?).
- "role: not ours": Home role hi badal gaya.

## 7. Aapke decisions (main nahi le sakta)
1. Stock khulne par notification chahiye? Store review ka khatra hai. Haan bolein to default-off toggle ke saath add kar dunga.
2. Package id: `com.oklabs.oneui9launcher` (code, shortcuts, KeepAlive) ya `com.arapks.glassylauncher` (STEPS Step 75 ka note). Store par kaunsa hai?
3. Pehle wala Ui9 merge request: is zip mein status alag se check karna baaki hai.
4. Nova / Lawnchair ka code: internet chahiye. Browser mein dekh sakte hain, ya internet allow karein to next step mein dekh lenge.

## 8. Seedhi baat
Koi bhi normal (store wali) app stock launcher ko silently Home se nahi hata sakti. Android ne ye permission nahi di, aur store rules bhi ye allow nahi karte. Ye zip teen kaam karta hai: sahi detection, ek tap mein wapas lena, aur ye pakadna ki Home kab, kyun aur kis app se badla. Report aate hi trigger pakka hoga, aur fix us hisaab se hoga.
