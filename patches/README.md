# Yerel patch'ler

Bu klasör, upstream Open Headunit'e eklediğimiz değişiklikleri tutar. Upstream güncellenince
patch'leri yeni sürümün üstüne tekrar uygulayıp APK'yı yeniden derleyebilirsiniz.

| Patch | Ne yapar |
|---|---|
| `0001-WiFi-button-wait-for-a-WiFi-join-before-the-Headunit.patch` | "WiFi ile bağlan" (Headunit Server modu): ünite WiFi'ye bağlı değilse WiFi taraması başlatır, en fazla 60 sn bağlantıyı bekler, bağlanınca Headunit Server aramasını başlatır. Beklerken tuşa tekrar basmak beklemeyi iptal eder. |

## Upstream güncellenince

```bash
git remote add upstream https://github.com/andreknieriem/open-headunit.git   # bir kez
git fetch upstream
git checkout -B my-build upstream/main
git checkout claude/focused-heisenberg-4d9ev6 -- patches   # patch klasörünü getir
./patches/apply.sh
./gradlew :app:assembleGithubDebug        # APK: app/build/outputs/apk/github/debug/
```

`apply.sh`, `git am -3` kullanır. Upstream aynı satırları değiştirdiyse git çakışmayı gösterir.
Çakışmayı düzeltip `git add <dosya> && git am --continue` çalıştırın.

## Patch'i değiştirdikten sonra yeniden üretmek

Kod değişikliğini commit edin, ardından:

```bash
./patches/regenerate.sh upstream/main
```

Bu komut, `upstream/main` üstündeki her commit için bir `.patch` dosyası yazar (dosya adı commit başlığından gelir). Yalnızca
`patches/` klasörüne dokunan commit'ler bu dosyalara girmez.

## Değişen dosyalar

- `connection/wifi/WifiJoinWaiter.kt` (yeni dosya; mantığın tamamı burada)
- `main/HomeFragment.kt` (tuşun Headunit Server dalında bu sınıfı çağıran küçük bir bölüm, `[PATCH wifi-wait]` ile işaretli)
- `res/values/strings.xml`, `res/values-tr/strings.xml` (4 yeni metin)

Bekleme süresi `HomeFragment` içindeki `WIFI_JOIN_TIMEOUT_MS` ile ayarlanır (varsayılan 60 sn).
