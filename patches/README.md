# Yerel patch'ler

Bu klasör, orijinal Open Headunit'e eklediğimiz değişiklikleri **özellik başına bir patch** olarak
tutar. Upstream güncellenince patch'leri yeni sürümün üstüne yeniden uygularsınız.
`BASE` dosyası, patch'lerin en son hangi upstream commit'ine göre üretildiğini gösterir.

| Patch | Ne yapar |
|---|---|
| `0001-Add-a-workflow-that-builds-a-release-APK-signed-with.patch` | GitHub'da kendi anahtarınızla imzalı APK derleyen iş akışı ve `SIGNING.md` kurulum notları. |
| `0002-Input-choose-which-key-groups-reach-Android-Auto.patch` | Ayarlar → Giriş → **Tuşları Android Auto'ya gönder**: OK / Seç, yön tuşları ve döner düğme, Geri, arama, sesli asistan ve diğer tuşlar için ayrı ayrı onay kutuları. Direksiyondaki OK'nin Android Auto'da bir şey seçmesini engeller. Upstream'e `feat/aa-key-groups` dalından önerilecek. |

Taban: upstream `main` (`BASE` dosyasındaki commit).

Daha önce burada duran WiFi özellikleri (0002, 0003) orijinal projeye kabul edildi ([#1298](https://github.com/andreknieriem/open-headunit/pull/1298)); artık upstream'in kendi kodunda oldukları için patch olarak tutulmuyorlar.

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

**0002 (Android Auto'ya gönderilen tuşlar)**
- `input/AaKeyGroupPolicy.kt`: yeni. Tuşu gruba ayıran ve gönderilip gönderilmeyeceğine karar veren kural; birim testi var.
- `connection/CommManager.kt`: atanmamış tuşlar için grup kontrolü.
- `utils/Settings.kt`, `utils/SettingsBackupManager.kt`, `main/SettingsPendingCopy.kt`, `main/SettingsFragment.kt`: yeni ayar ve seçim penceresi.
- `res/values*/strings.xml`: 26 dilde metinler.
- `test/...`: `AaKeyGroupPolicyTest`, `AaKeyGroupsDialogTest` yeni.
- `CHANGELOG.md`
