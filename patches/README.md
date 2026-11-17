# Yerel patch'ler

Bu klasör, orijinal Open Headunit'e eklediğimiz değişiklikleri **özellik başına bir patch** olarak
tutar. Upstream güncellenince patch'leri yeni sürümün üstüne yeniden uygularsınız.
`BASE` dosyası, patch'lerin en son hangi upstream commit'ine göre üretildiğini gösterir.

| Patch | Ne yapar |
|---|---|
| `0001-Add-a-workflow-that-builds-a-release-APK-signed-with.patch` | GitHub'da kendi anahtarınızla imzalı APK derleyen iş akışı ve `SIGNING.md` kurulum notları. |
| `0002-Home-show-the-WiFi-network-beside-the-main-button-s-.patch` | Ana ekran: kartın WiFi simgesinin yanında bağlı ağ, bant ve sinyal (yalnızca Headunit Server). Simgeye dokunmak bir WiFi taraması ister, uzun basmak WiFi ayarlarını açar. |
| `0003-Home-let-the-WiFi-line-use-half-the-header-s-free-wi.patch` | 0002'nin düzeltmesi: WiFi yazısı sabit 320dp yerine satırın boş alanının yarısına kadar genişler, geniş ekranda kesilmez. 0002 ile birlikte kullanılır. |

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

**0002 (WiFi durumu)**
- `main/HomeWifiStatus.kt`: yeni. WiFi durumunu okur, değişiklikleri dinler, tarama ister.
- `main/HomeWifiStatusPolicy.kt`: yeni. Hangi durumda ne yazacağı; birim testi var.
- `main/HomeFragment.kt`: bağlantı noktası (dokunma, uzun basma, güncelleme).
- `utils/HomeUiHelper.kt`: yazının rengi.
- `res/layout/view_home_primary_card.xml`: simgenin yanındaki yazı alanı.
- `res/values/dimens.xml`, `res/values/strings.xml`, `res/values-tr/strings.xml`

**0003 (WiFi yazısının genişliği)**
- `res/layout/view_home_primary_card.xml`, `res/values/dimens.xml`
