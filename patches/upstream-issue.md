# Proje sahibine bildirim taslağı

GitHub: https://github.com/andreknieriem/open-headunit/issues/new
Göndermeden önce cihaz bilgilerini kontrol edin.

---

**Title:** Headunit Server: discovery does not help a unit that has not joined WiFi yet; Auto-Enable Hotspot looks inert in this mode

1. On slow-joining units (Android 10 here), the Headunit Server discovery loop sweeps every 10 s
   without checking whether the unit is on WiFi at all, and the WiFi button's one-shot sweep ends
   at once in that state. The join itself is the bottleneck: Android 10 backs off its own scans
   while disconnected. Parking the loop while not on WiFi, requesting WiFi scans (paced to the
   4-per-2-min foreground budget), and searching on join fixed it for me. I have a working patch
   if useful.
2. In Wireless Connection -> Headunit Server -> Auto, the "Auto-Enable Hotspot" toggle is shown,
   but I could not find any code path in this mode that calls `HotspotManager.setHotspotEnabled()`
   (`WifiLauncherAuto.start()` only reports a stage; `UserExitHotspotPolicy.usesHeadUnitHotspot()`
   excludes AUTO).

Device: aftermarket 10.25" Android 10 unit (Audi A3), phone Galaxy S24 Ultra, app 4.0.0.
