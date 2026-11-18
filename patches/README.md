# Yerel patch'ler

Bu klasör, orijinal Open Headunit'e eklediğimiz değişiklikleri **özellik başına bir patch** olarak
tutar. Upstream güncellenince patch'leri yeni sürümün üstüne yeniden uygularsınız.
`BASE` dosyası, patch'lerin en son hangi upstream commit'ine göre üretildiğini gösterir.

| Patch | Ne yapar |
|---|---|
| `0001-Add-a-workflow-that-builds-a-release-APK-signed-with.patch` | GitHub'da kendi anahtarınızla imzalı APK derleyen iş akışı ve `SIGNING.md` kurulum notları. |
| `0002-Home-WiFi-status-and-a-Scan-WiFi-button-for-Headunit.patch` | Ana ekran (Headunit Server): kartın simgesinin yanında bağlı ağ, bant etiketi ve sinyal çubukları; ana tuş tüm modlarda ikonlu hap şeklinde. (Bu patch'teki ayrı **WiFi tara** tuşunu 0003 kaldırır.) |
| `0003-Headunit-Server-a-Connection-Method-and-on-the-phone.patch` | Ayarlar → Headunit Server (Otomatik) → **Bağlantı yöntemi**: Telefon AP / Ünite AP / Ortak ağ. **Telefon AP** seçiliyken: ünite WiFi'ye bağlı değilse telefon araması ağ taramaz, bekler; **WiFi ile bağlan** önce WiFi taraması yapar (WiFi kapalıysa paneli açar), 60 sn bağlantıyı bekler (İptal ana tuşun yerinde), bağlanınca telefonu arar; isteğe bağlı **Erişim Noktasını Otomatik Tara** (ana ekran açıkken hemen, 2 dk boyunca 35 sn'de bir, sonra dakikada bir). Ortak ağ ve Ünite AP'de hiçbir şey değişmez. 0002 ile birlikte upstream'e `feat/home-wifi-scan` dalından önerildi. |

Taban: **4.1.0-beta1** (`BASE` dosyasındaki commit).

Bir özelliği istemiyorsanız ilgili `.patch` dosyasını silmeniz yeterli.

## Upstream güncellenince

İki yol var. **Birincisi daha kolaydır.**

### 1) Upstream'i bu dala birleştirmek

```bash
git remote add upstream https://github.com/andreknieriem/open-headunit.git   # bir kez
```

```bash
git fetch upstream
```

```bash
git merge upstream/main
```

```bash
git push
```

Çakışma çıkarsa git hangi dosyada olduğunu söyler. Düzeltip `git add <dosya> && git commit`
yaptıktan sonra push yapın. Push, imzalı APK'yı kendiliğinden derler.

### 2) Upstream'in temiz halinden yeniden kurmak

```bash
git fetch upstream
```

```bash
git checkout -B my-build upstream/main
```

```bash
git checkout claude/focused-heisenberg-4d9ev6 -- patches
```

```bash
./patches/apply.sh
```

`apply.sh` patch'leri sırayla `git am -3` ile uygular. Çakışma çıkarsa:
düzeltin, `git add <dosya> && git am --continue`.

## Patch'leri yeniden üretmek

Bir özelliği değiştirip commit ettikten sonra:

```bash
./patches/regenerate.sh upstream/main
```

Upstream üstündeki her commit için ayrı bir `.patch` yazar; yalnızca bu klasöre dokunan
commit'ler patch'e girmez. Yeni bir özellik ayrı bir commit olarak eklenirse kendi patch'ini alır.

## Değişen dosyalar

**0001 (imza)**
- `.github/workflows/personal-signed-apk.yml`
- `SIGNING.md`

**0002 (WiFi durumu ve ana tuş görünümü)**
- `main/HomeWifiStatus.kt`: yeni. WiFi durumunu okur, değişiklikleri dinler, tarama ister.
- `main/HomeWifiStatusPolicy.kt`: yeni. Ne zaman ne gösterileceği; birim testi var.
- `main/HomeFragment.kt`: tarama tuşunun bağlantı noktası.
- `utils/HomeUiHelper.kt`: durum alanı ve tuşun gündüz/gece boyaması.
- `res/layout/view_home_primary_card.xml`: durum alanı ve tarama tuşu.
- `res/drawable/`: WiFi arama, tarama halkası, WiFi kapalı, bağlantı, sinyal çubukları, uyarı noktası ikonları.
- `res/values/ids.xml`: sığdırma için etiket kimlikleri.
- `res/values*/dimens.xml`: ekran boyutuna göre ölçüler.
- `res/values*/strings.xml`: 25 dilde metinler.
- `test/.../HomeWifiStatusLayoutTest.kt`: yeni. Gerçek ana ekranı 7 ekran boyutunda yerleştirip hiçbir şeyin kesilmediğini denetler.
- `CHANGELOG.md`

**0003 (Bağlantı yöntemi ve erişim noktası beklemesi)**
- `connection/wifi/HeadunitServerNetwork.kt`: yeni. Ayarın seçenekleri ve "telefon araması WiFi'yi beklesin mi" kuralı; birim testi var.
- `connection/wifi/NetworkDiscovery.kt`: Telefon AP seçiliyken WiFi yoksa ağ taramasını atlar.
- `utils/Settings.kt`, `utils/SettingsBackupManager.kt`, `main/SettingsPendingCopy.kt`, `main/SettingsFragment.kt`: iki yeni ayar ve ayar ekranındaki satırları.
- `main/HomeWifiStatus.kt`, `main/HomeWifiStatusPolicy.kt`, `main/HomeFragment.kt`: ana tuşun bekleme akışı ve otomatik tarama.
- `utils/HomeUiHelper.kt`, `res/layout/view_home_primary_card.xml`: WiFi tara tuşu kaldırıldı; bekleme görünümü; dar ekranda ana tuş ikonsuz.
- `utils/HotspotManager.kt`: `isApUp` artık dışarıdan da çağrılabilir.
- `utils/LogExporter.kt`: günlük başlığında seçilen bağlantı yöntemi.
- `res/values*/strings.xml`: 25 dilde metinler.
- `test/...`: `HeadunitServerNetworkTest`, `HeadunitServerNetworkSettingRowTest` yeni; ana ekran testleri 26 dilde.
