# Yerel patch'ler

Bu klasör, upstream Open Headunit'e eklediğimiz değişikliği **tek bir patch** olarak tutar.
Upstream güncellenince patch'i yeni sürümün üstüne tekrar uygulayıp APK'yı yeniden derleyin.

| Dosya | Ne işe yarar |
|---|---|
| `0001-Headunit-Server-join-WiFi-before-the-WiFi-button-s-s.patch` | Değişikliğin kendisi (aşağıya bakın). Upstream'e katkı (PR) için de aynı commit kullanılır. |
| `commit-message.txt` | Patch'in commit mesajı. `regenerate.sh` bunu kullanır. |
| `apply.sh` | Klasördeki patch'leri sırayla `git am -3` ile uygular. |
| `regenerate.sh` | Daldaki tüm değişiklikleri (bu klasör hariç) upstream'e göre tek patch'e çevirir. |

## Patch ne yapar

Ayarlar → **Kablosuz bağlantı** → Headunit Server → **Otomatik** altında iki yeni satır:

- **Aramadan önce WiFi'ye bağlan** (varsayılan: açık)
- **WiFi bağlanma zaman aşımı** (10–120 sn, varsayılan 30 sn)

Açıkken, ünite WiFi'de değilse **arka plandaki telefon araması** boşa
aramak yerine durur, Android'den WiFi taraması ister ve WiFi bağlandığı an
aramaya devam eder. Bu açılışta da çalışır; tuşa basmak gerekmez.

**WiFi ile bağlan** tuşu ayrıca şunları yapar:

1. Ünite WiFi'ye zaten bağlıysa eskisi gibi hemen arar.
2. Bağlı değilse Android'den WiFi taraması ister. Taramalar, Android'in iki dakikada dört tarama sınırına göre zamanlanır.
3. Beklerken ana ekran kartında "Ağ hazırlanıyor" adımı görünür. Tam ekran kaplama açılmaz.
4. WiFi kapalıysa Android 10 altında kendisi açar, Android 10 ve üstünde WiFi panelini açar.
5. WiFi bağlanınca Headunit Server'ı arar. Süre dolarsa uyarı verir ve yine de bir kez arar.
6. Beklerken tuşa tekrar basmak beklemeyi bozmaz. Durum göstergesindeki X beklemeyi bitirir.

## Upstream güncellenince

```bash
git remote add upstream https://github.com/andreknieriem/open-headunit.git   # bir kez
```

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

```bash
./gradlew :app:assembleGithubDebug
```

APK: `app/build/outputs/apk/github/debug/`.

Upstream aynı satırları değiştirdiyse `git am` çakışmayı gösterir. Düzeltip
`git add <dosya> && git am --continue` çalıştırın, sonra patch'i yeniden üretin.

## Patch'i yeniden üretmek

Kodu değiştirip commit ettikten sonra:

```bash
./patches/regenerate.sh upstream/main
```

## Değişen dosyalar

- `connection/wifi/WifiJoinWaitPolicy.kt`: yeni dosya. Bekleme kuralları ve tarama zamanlaması burada, birim testi de var.
- `connection/wifi/WifiJoinWaiter.kt`: yeni dosya. Tarama, ağ dinleme ve zaman aşımı burada.
- `aap/AapService.kt`: WiFi tuşunun tek seferlik aramasını bu bekleyiciden geçirir.
- `main/HomeFragment.kt`: beklenecekse kaplama yerine durum göstergesini kullanır.
- `main/SettingsFragment.kt`, `SettingsPendingCopy.kt`, `utils/Settings.kt`, `utils/SettingsBackupManager.kt`: ayar satırları, kayıt ve yedekleme.
- `res/values*/strings.xml`: 25 dilin hepsinde metinler.
- `CHANGELOG.md`, `README.md`
