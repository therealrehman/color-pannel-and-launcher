GLASSY LAUNCHER - TEST BUILD 1.1.15 (code 215)
==============================================

Ye FINAL fix nahi hai. Ye ek TEST build hai.

Kyun ye test kar rahe hain
--------------------------
1. Galaxy launcher (com.launcher.galaxys20.ultra) ek normal third-party app hai, system app nahi.
2. Uske Home activity mein HOME + LAUNCHER dono category hain.
3. Hamare app ke Home activity mein sirf HOME tha. Settings wala icon alag tha.
4. Ye test check karega ki kya ye fark wajah tha jiski wajah se restart ke baad ya Chrome ke baad Home stock launcher par chala jata tha.

Kya badla (sirf ek file)
------------------------
- app/src/main/AndroidManifest.xml
  LauncherActivity (Home screen) mein LAUNCHER category add ki gayi hai.
  Label "Glassy Home (test)" diya gaya hai.
- Koi aur code change nahi hai. Settings wala icon waisa hi hai.

Dhyan dein: is test mein app ke DO icon dikhenge
- "Glassy Home (test)"  -> Home screen (ye test icon hai)
- "Glassy Launcher"     -> Settings (purana icon)

Test kaise karein
-----------------
1. Is zip ko CI (ya Android Studio) se build karein aur purani app ke upar install karein.
   Package id same hai: com.oklabs.oneui9launcher
   Agar com.arapks... wali alag copy bhi installed hai, use uninstall karein.
2. Glassy ko Home app banayein (Home dabao > Glassy > Always), ya Settings > Default apps > Home app se.
3. Phone restart karein.
4. Restart ke baad Chrome (ya koi aur app) kholen, phir Home dabayein.
5. Check karein: Glassy khula ya stock launcher?
6. Ye 2 se 3 restart tak repeat karein.

Result ka matlab
----------------
- Agar Glassy stable raha: LAUNCHER wali kami hi wajah thi. Next step: ek hi icon wala final version banayenge.
- Agar phir bhi stock khula: LAUNCHER wajah nahi thi. Next test role aur background policy (autostart / battery) par hoga.

Report bhejein
--------------
- Problem ke waqt: Settings > Home app log > Copy karke poora text bhejein.
- Ye bhi batayein: phone brand + model, Android version, aur kaun sa icon use kiya.

Wapas purana setup
------------------
- AndroidManifest.xml mein LauncherActivity wali LAUNCHER category hata dein, ya purani zip use karein.

Limits
------
- Compile aur phone test is sandbox mein nahi hua. CI build mein error aaye to bata dein.
- Agar app icon gayab ho jaye (kuch phones par pehle aisa hota tha), to turant bata dein.
