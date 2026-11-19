# Pliki

Menedżer plików na Androida (11+) z **tagami**, czyli wirtualnymi zbiorami.
Jeden plik może należeć do wielu zbiorów i zostaje tam, gdzie jest.

## Co potrafi

- **Przeglądanie** pamięci wewnętrznej i karty SD: ścieżka u góry, sortowanie, widok listy lub siatki, ukryte pliki.
- **Operacje na plikach**: kopiowanie, przenoszenie (schowek + „Wklej tutaj”), zmiana nazwy, nowy folder,
  udostępnianie, „Otwórz za pomocą…”, pakowanie i rozpakowywanie ZIP.
- **Kosz**: usunięte pliki można przywrócić przez 30 dni.
- **Zbiory (tagi)**: kolorowe tagi, podpowiedzi po nazwie pliku (np. „faktura…” → „Faktury”), opcjonalne
  przeniesienie do folderu `Zbiory/<tag>`.
- **Reguły automatyczne**: zbiór może sam zbierać pasujące pliki (nazwa, typ, folder, zakres dat).
- **Wyszukiwanie** w całej pamięci z filtrami (typ, tag, data, rozmiar). Wynik można zapisać jako inteligentny zbiór.
- **Kategorie**: zdjęcia, wideo, audio, dokumenty, APK, archiwa (z całego telefonu naraz).
- **Analiza miejsca**: wykres zajętości, największe foldery, duplikaty, duże pliki, stare pobrane, zbędne APK.

## Instalacja na telefonie

1. Wejdź w zakładkę **Releases** tego repozytorium (lub **Actions** → ostatnie uruchomienie).
2. Pobierz plik `Pliki-1.0.N.apk` na telefon i otwórz go.
3. Zezwól przeglądarce/menedżerowi plików na „instalowanie nieznanych aplikacji”, jeśli Android o to zapyta.
4. Przy pierwszym uruchomieniu przyznaj „Dostęp do zarządzania wszystkimi plikami”.

Kolejne wersje instalują się jako aktualizacja: tagi i zbiory zostają.

## Ograniczenia Androida

- Foldery `Android/data` i `Android/obb` innych aplikacji są niedostępne od Androida 11 (bez roota).
- Tagi są zapisane w bazie aplikacji, nie w samych plikach. Jeśli przeniesiesz plik inną aplikacją,
  Pliki spróbują go odnaleźć po nazwie i rozmiarze przy następnym skanowaniu.

## Dla programisty

Kotlin, Jetpack Compose (Material 3), Room, Coil. Budowanie: `./gradlew assembleRelease`.
Test całej aplikacji (Robolectric + zrzuty ekranów do `app/screenshots/`): `./gradlew testDebugUnitTest`.

Klucz do podpisywania (`app/pliki-release.keystore`) jest w repozytorium celowo, żeby każda wersja
z GitHuba instalowała się jako aktualizacja poprzedniej. Nie jest to klucz do niczego innego.

Czcionki Inter i Space Grotesk na licencji SIL OFL 1.1 (`licenses/`).
