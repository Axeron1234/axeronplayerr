# Mój Odtwarzacz (Android)

Odtwarzacz audio: wybór plików z urządzenia, playlisty, kolejka, edycja nazwy/wykonawcy/obrazka
oraz tekst na żywo (jak w Mi Music).

## Jak dostać plik APK

**Opcja A – GitHub (bez instalowania czegokolwiek):**
1. Utwórz puste repozytorium na github.com i wgraj do niego całą zawartość tego folderu.
2. Wejdź w zakładkę *Actions* → *Build APK* (uruchomi się samo po wgraniu; można też kliknąć *Run workflow*).
3. Po ok. 5 min pobierz artefakt `MojOdtwarzacz-apk` → w środku jest `app-release.apk`.

**Opcja B – Android Studio:** otwórz folder, poczekaj na synchronizację Gradle,
*Build → Build APK(s)*. APK będzie w `app/build/outputs/apk/`.

**Instalacja:** skopiuj APK na telefon i otwórz go (zezwól na instalację z nieznanych źródeł).

## Format tekstu
Każda linijka: `[minuty:sekundy.setne] tekst`, np.

    [00:12.50] Pierwsza linijka
    [01.21.56] Linijka z minuty 1:21,56

Zapis z kropkami `[01.21.56]` też działa. Tekst wkleja się w edycji utworu (ikona ołówka).

## Uwagi
- Edycja nazwy, wykonawcy, obrazka i tekstu zapisuje się w aplikacji; sam plik audio nie jest zmieniany.
- Utwory w kolejce nie przełączają się same – po końcu utworu odtwarzanie się zatrzymuje,
  następny/poprzedni tylko przyciskami.
- minSdk 26 (Android 8.0+).
