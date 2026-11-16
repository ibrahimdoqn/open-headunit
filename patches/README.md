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

Ayarlar → **Kablosuz bağlantı** → Headunit Server → **Otomatik** altında **Otomatik bağlantı** grubu:

| Ayar | Varsayılan | Ne yapar |
|---|---|---|
| Otomatik bağlan | açık | Uygulama açılınca telefonu kendisi arar. Kapalıyken yalnızca WiFi tuşu bağlanır. |
| Başlangıç gecikmesi | 15 sn (0–60) | Aramadan önce ana ekran kartında geri sayar (Şimdi bağlan / İptal). Ayarlar açılınca durur, çıkınca baştan başlar. |
| Aramadan önce WiFi'ye bağlan | açık | Ünite WiFi'de değilken boşa aramaz; WiFi taraması ister, bağlanır bağlanmaz arar. |
| WiFi bağlanma zaman aşımı | 30 sn | Yalnızca WiFi tuşunun bekleme süresi. |
| Telefonun ağı | kendiliğinden öğrenilir | Son bağlantının WiFi ağı; dokununca unutulur. |

Kartta görünen adımlar:

- **Yeniden bağlanıyor (geri sayım)**: başlangıç gecikmesi
- **Telefonun WiFi'si bekleniyor**: ünite WiFi'de değil, tarama yapılıyor
- **Telefonun erişim noktası görünmüyor**: öğrenilen ağ 20 sn boyunca taramada yok (konum açık olmalı)
- **Başka bir WiFi ağına bağlı**: ünite telefonun ağı yerine başka bir ağa bağlandı
- **WiFi bağlandı, telefona bağlanılıyor**: 1–3 sn aralıklarla hızlı deneme
- **Telefonda Headunit Server kapalı**: telefon cevap veriyor ama 5277 reddediliyor; bir kez nasıl açılacağı da söylenir
- **Bağlanıyor → Android Auto**

Ayrıca:

- "Son oturuma bağlan" artık son telefon adresini aramayla aynı anda aramıyor (çift bağlantı telefonun sunucusunu kilitleyebiliyordu).
- Logda her bağlantı için tek satır: `HuAutoConnect: Timeline: start->search 15.0s -> WiFi 8.2s -> phone 1.1s -> handshake 0.4s -> projection 2.1s (network=..., 5180 MHz)`

**WiFi ile bağlan** tuşu: geri sayımı bitirip hemen arar; WiFi yoksa kartta "Ağ hazırlanıyor" gösterip en fazla 30 sn bekler, WiFi kapalıysa açmanızı ister.

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

- `connection/wifi/autoconnect/`: yeni klasör. Otomatik bağlantı (`HeadunitServerAutoConnect`) ve kuralları (`HeadunitServerAutoConnectPolicy`), testleriyle.
- `connection/ConnectionStage*.kt`, `main/StatusPillPolicy.kt`, `main/HomePrimaryPolicy.kt`: yeni kart adımları.
- `connection/wifi/NetworkDiscovery.kt`, `WifiLauncherSharedServices.kt`: "sunucu kapalı" ayrımı ve hızlı denemeler.
- `connection/wifi/WifiJoinWaitPolicy.kt`: yeni dosya. Bekleme kuralları ve tarama zamanlaması burada, birim testi de var.
- `connection/wifi/WifiJoinWaiter.kt`: yeni dosya. Tarama, ağ dinleme ve zaman aşımı burada.
- `aap/AapService.kt`: WiFi tuşunun tek seferlik aramasını bu bekleyiciden geçirir.
- `main/HomeFragment.kt`: beklenecekse kaplama yerine durum göstergesini kullanır.
- `main/SettingsFragment.kt`, `SettingsPendingCopy.kt`, `utils/Settings.kt`, `utils/SettingsBackupManager.kt`: ayar satırları, kayıt ve yedekleme.
- `res/values*/strings.xml`: 25 dilin hepsinde metinler.
- `CHANGELOG.md`, `README.md`
