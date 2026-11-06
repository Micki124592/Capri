# Capri Mobile 0.2.0 — S22 Ultra + Tailscale + Gemini

Aktualna wersja jest opisana w [MOBILE-0.2.md](MOBILE-0.2.md). APK do samodzielnej instalacji znajduje się lokalnie w artifacts/Capri-Mobile-0.2.0-S22Ultra.apk. Token telefonu jest prywatny i nie jest publikowany w repozytorium ani w APK.

Poniżej zachowano instrukcje wersji 0.1 jako opis starszej integracji Ollama/YouTube Music; do konfiguracji serwera używaj instrukcji 0.2 powyżej.

# Capri Mobile 0.1.0 — wersja testowa dla Androida

Capri na telefonie: animowany awatar, czat i polski mikrofon, model przez Ollamę na komputerze, głos przez oficjalne Fish Audio API oraz polecenia YouTube Music. Android 12 lub nowszy; projekt przygotowany z myślą o Samsungu S22 Ultra.

## Instalacja

1. Przenieś `artifacts/Capri-Mobile-0.1.0-debug.apk` na telefon i otwórz go w instalatorze Androida. Jest to podpisany kluczem deweloperskim APK do testów, nie aplikacja opublikowana w Google Play.
2. Otwórz Capri → koło zębate.
3. Wpisz adres Ollamy, np. `http://192.168.1.20:11434`, i nazwę już zainstalowanego modelu. Domyślnie: `qwen3.5:2b`. `localhost` oznacza telefon, a nie komputer.
4. Zapisz ustawienia i wybierz **Sprawdź połączenie**.
5. Wpisz klucz API Fish Audio. Voice ID `499318f2459e4dd8a3ef701086a0e117` i model `s2.1-pro-free` są już ustawione. Zapisz i wybierz **Posłuchaj Capri**. Dostępność głosu i limit zależą od konta Fish. Nie ma automatycznego przełączenia na głos Windows/Android.
6. Dotknij kropki mikrofonu i przyznaj uprawnienie do mikrofonu, gdy Android o nie poprosi. Rozpoznawanie mowy zapewnia usługa Androida/Google/Samsung; jej wymagania sieciowe zależą od telefonu.

Klucz Fish jest zaszyfrowany AES-GCM z kluczem przechowywanym w Android Keystore. Klucz nie jest przekazywany do JavaScript, Ollamy ani do narzędzi Shizuku. Kopie zapasowe danych aplikacji są wyłączone. Historia pozostaje lokalnie w aplikacji; ostatnie wiadomości są wysyłane do skonfigurowanej Ollamy, a wypowiadany tekst do Fish Audio.

## Udostępnienie Ollamy z komputera

Telefon musi mieć dostęp do komputera przez domowe Wi-Fi lub prywatny VPN. Ollama standardowo nasłuchuje tylko lokalnie. Zamknij aplikację Ollama w zasobniku, a następnie uruchom dołączony `Start-Ollama-LAN.ps1`, podając adres IPv4 komputera:

```powershell
.\Start-Ollama-LAN.ps1 -ListenIp 192.168.1.20
```

Skrypt ustawia adres tylko dla uruchamianego procesu. Nie zmienia zapory ani stałych ustawień systemu. Jeśli Windows zapyta o dostęp, wybierz wyłącznie zaufaną sieć prywatną. Ollama nie ma tu uwierzytelniania: nie przekierowuj portu 11434 do publicznego internetu. Dostęp poza domem konfiguruj przez prywatny VPN. W tej aplikacji endpoint jest natywny `/api/chat`; nie jest to endpoint `/v1` LM Studio.

## YouTube Music — pierwszy test

1. Zainstaluj YouTube Music i zaloguj się w nim samodzielnie. Sprawdź, czy możesz ręcznie odtworzyć utwór.
2. W Capri → Ustawienia → **YouTube Music: przyciski** przeczytaj opis i włącz usługę **Capri — YouTube Music** w ustawieniach dostępności Androida. Usługa działa tylko w tym pakiecie i tylko przez maksymalnie 30 sekund po zleceniu wyszukiwania.
3. Włącz **Stan odtwarzania** → **Capri — stan odtwarzania**. Pozwala to sprawdzić sesję multimediów YouTube Music. Kod nie odczytuje tekstów innych powiadomień.
4. Wpisz lub powiedz: **„Odtwórz SAMURAI Never Fade Away w YouTube Music”**.
5. Capri najpierw próbuje standardowego polecenia Androida `MEDIA_PLAY_FROM_SEARCH`. Jeśli nie otrzyma potwierdzenia, otwiera wyszukiwanie i szuka widocznego pasującego przycisku/wiersza utworu.
6. Dopiero gdy stan sesji to `PLAYING` i metadane pasują do zapytania, pojawia się **„Gra: tytuł — wykonawca”**. Jeśli tytułu nie da się potwierdzić, aplikacja podaje to wprost i prosi o wybranie wyniku.

Polecenia lokalne nie wymagają modelu: `pauza`, `wznów`, `następny utwór`, `poprzedni utwór`, `głośniej`, `ciszej`, `otwórz YouTube Music`. Złożone prośby przechodzą przez model; proponowane narzędzie wymaga potwierdzenia w telefonie.

Nie ma ślepego klikania we współrzędne ani automatycznego uruchamiania poleceń powłoki zwróconych przez model. Wersja 0.1.0 skupia się na muzyce; nie implementuje uniwersalnego sterowania każdą aplikacją.

## Shizuku

Zainstaluj i uruchom Shizuku według jego oficjalnej instrukcji. W Capri wybierz **Połącz** i samodzielnie zatwierdź dostęp. Przez `UserService` wykonywane są wyłącznie ustalone kody przycisków multimediów i głośności. Nie ma narzędzia do dowolnego `sh -c`.

Odtwarzanie konkretnego utworu opiera się na intencie Androida i usłudze dostępności. Shizuku jest dodatkową ścieżką sterowania mediami, gdy nie ma dostępnej sesji YouTube Music. Polecenia przycisków Shizuku trafiają do aktualnej aplikacji multimedialnej Androida.

## Żywy awatar

Użyty jest rzeczywisty renderer WebGL i arkusze animacji z `mochiiwuff/capri-desktop`, nie sam GIF. Włosy, ręce, przechylenia, skoki, obroty, echa i glitch są animowane. Dotknij awatara lub gwiazdki, by wywołać sekwencję. Przeciągaj postać w jej scenie.

Stany: bezczynność, słuchanie, myślenie, mowa, działanie, powodzenie i błąd. Animacja mowy jest stylizowaną reakcją na odtwarzanie; nie jest synchronizacją fonemów z ustami.

W ustawieniach wybierz **Pokaż nad aplikacjami**, przyznaj zezwolenie na nakładkę i naciśnij ponownie. Pływająca Capri działa w widocznej usłudze pierwszoplanowej z powiadomieniem. Przesuwaj za pasek CAPRI, kropka otwiera aplikację i mikrofon, × zamyka nakładkę. Aplikacja nie nasłuchuje stale i nie uruchamia się samoczynnie po restarcie telefonu.

## Stan weryfikacji i ograniczenia

- Zbudowano instalowalny APK; Android Lint: brak błędów.
- Testy jednostkowe: polskie komendy muzyczne, zachowanie tytułów, odrzucenie nieznanych narzędzi i nieprawidłowych argumentów.
- Interfejs i WebGL sprawdzone w przeglądarce przy wymiarach telefonu 412 × 915; podgląd znajduje się w `artifacts/capri-mobile-preview.png`.
- S22 Ultra nie był podłączony. Uruchomienie na fizycznym telefonie, integracja Shizuku i klikanie wyników konkretnej wersji YouTube Music pozostają do sprawdzenia na urządzeniu.
- Emulator na tym komputerze nie uruchomił się, również w trybie programowym; brak testu Android runtime.
- Nie wykonywano prawdziwego wywołania Fish: klucz użytkownika nie został przekazany. Test sieciowej rozmowy wymaga adresu Ollamy.
- Pełna odpowiedź modelu jest wyświetlana na bieżąco; dźwięk jest pobierany jako MP3 i następnie odtwarzany. Nie ma jeszcze strumieniowania audio od pierwszej paczki ani automatycznego przerzucania całej rozmowy w tle.
- Interfejs YouTube Music i ograniczenia konta mogą wpływać na odtwarzanie. Nie potwierdzamy sukcesu wyłącznie na podstawie otwarcia aplikacji.

## Budowanie

JDK 21, Android SDK platform 35 i Build Tools 35.0.0. Gradle Wrapper jest dołączony. Po zainstalowaniu Android Studio można otworzyć katalog projektu i wybrać Build APK. Alternatywnie:

```powershell
.\Build.ps1 -SdkRoot 'C:\Users\twoj-login\AppData\Local\Android\Sdk' -JavaHome 'C:\sciezka\do\jdk-21'
```

Wynik: `app/build/outputs/apk/debug/app-debug.apk`. Testy: `gradlew.bat :app:testDebugUnitTest :app:lintDebug`.

## Autorstwo

Required Notice: Copyright (c) 2026 mochiiwuff (https://github.com/mochiiwuff/capri-desktop)

Renderer `sprite.js` i sekwencje `acting.js` pochodzą z repozytorium wskazanego przez użytkownika, commit `cb33e87dfaee79cec8b4b0f737ca89e07b87ba4b`. Zmiany: nowy mobilny sterownik sceny, natywny Android, dotyk, integracje sieciowe i narzędzia telefonu. Warunki źródłowe: PolyForm Noncommercial 1.0.0, pełny tekst w `LICENSE-UPSTREAM.txt` oraz wewnątrz APK.

Grafiki w `assets/art` mają odrębnych właścicieli praw i nie są objęte licencją kodu. To prywatna niekomercyjna adaptacja fanowska; brak afiliacji z CD PROJEKT RED, Studio Trigger, Google i Fish Audio. Nie publikowano aplikacji w sklepie.
