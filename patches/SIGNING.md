# Kendi imzanızla APK (GitHub Actions)

`.github/workflows/personal-signed-apk.yml`, `main` dışındaki her dala push yapıldığında
uygulamayı **sizin anahtarınızla imzalı** release APK olarak derler. Her derleme bir öncekinin
üzerine kurulur ve ayarlar korunur. APK, deponun **Releases** sayfasına `personal-<numara>`
adıyla ön sürüm olarak eklenir; telefondan ya da üniteden oradan indirebilirsiniz.

Anahtar depoda **yoktur**. GitHub'daki iki gizli değişkenden (secret) okunur.

## Bir kerelik kurulum

1. GitHub'da depo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
2. İki secret ekleyin:

   | Name | Value |
   |---|---|
   | `HEADUNIT_KEYSTORE_BASE64` | `HEADUNIT_KEYSTORE_BASE64.txt` dosyasının tüm içeriği (tek satır) |
   | `HEADUNIT_KEYSTORE_PASSWORD` | `HEADUNIT_KEYSTORE_PASSWORD.txt` dosyasının içeriği |

3. Depo → **Actions** sekmesi kapalıysa açın ("I understand my workflows, go ahead and enable them").
4. **Actions** → **Personal signed APK** → **Run workflow** ile ilk derlemeyi başlatın
   (ya da dala bir push yapın). Secret'lar yoksa iş akışı bunu söyleyerek durur.

## İlk kurulumda bir kez

Şimdiye kadar kurduğunuz debug APK'lar başka bir anahtarla imzalı. İmzalı sürümü ilk kez
kurmadan önce mevcut uygulamayı **kaldırmanız** gerekir (ayarlar silinir; ayarlarda yedekleme
varsa önce dışa aktarın). Sonraki tüm sürümler üzerine kurulur.

## Anahtarı saklayın

`headunit-release-key.jks` ve şifresini güvenli bir yerde (şifre yöneticisi, kişisel bulut)
saklayın ve **kimseyle paylaşmayın**. Kaybederseniz yeni sürümler eskisinin üzerine kurulamaz;
yeni anahtarla bir kez kaldırıp kurmanız gerekir. Secret'ları ekledikten sonra
`.txt` dosyalarını cihazlarınızdan silebilirsiniz.

## Upstream güncellenince

`patches/apply.sh` iş akışı dosyasını da geri getirir. Secret'lar depoda kaldığı için yeni dala
push yapmanız yeterli.
