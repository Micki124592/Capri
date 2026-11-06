# Capri Mobile 0.2.0 — Samsung S22 Ultra

Przygotowano aktualizację istniejącej aplikacji Android (Android 12+). Kod obsługuje lokalną animowaną nakładkę chodzącą po ekranie, dotyk, przeciąganie za pasek CAPRI, otwarcie mikrofonu przyciskiem ● i zatrzymanie nakładki przez × lub powiadomienie. Po wygaszeniu ekranu ruch jest wstrzymywany. Animacje nie wywołują modelu i działają offline.

## Instalacja na telefonie

1. Zainstaluj Tailscale na S22 Ultra i zaloguj się do tej samej prywatnej sieci co komputer.
2. Przenieś `artifacts/Capri-Mobile-0.2.0-S22Ultra.apk` na telefon. Zezwól wybranej aplikacji Pliki na instalację tego APK. Jest to wersja testowa podpisana kluczem Android Debug; nie sklep Google Play.
3. Otwórz Capri → ⚙. Ustaw adres `https://home.tail6f0542.ts.net:8443`, model `codex-capricorn` oraz osobny **Klucz dostępu telefonu** z prywatnego pliku `artifacts/Capri-Mobile-configuration.txt`. Nie wpisuj go w polu Fish Audio. Zapisz i wybierz **Sprawdź połączenie**.
4. Napisz „Cześć, Capri”. Komputer musi być włączony, a Tailscale aktywny na obu urządzeniach.
5. W ustawieniach wybierz **Pokaż nad aplikacjami**, przyznaj systemową zgodę i naciśnij przycisk ponownie. Przeciągaj pasek CAPRI; postać sama spaceruje w poziomie. Dotknij postaci, aby wywołać animację, ● otwiera rozmowę i mikrofon, × kończy nakładkę.
6. Na Samsungu, jeśli system zamyka nakładkę: Ustawienia telefonu → Aplikacje → Capri → Bateria → Bez ograniczeń. Powiadomienie pozwala zawsze zatrzymać usługę. Nie ma stałego nasłuchu mikrofonu ani automatycznego startu po restarcie telefonu.
7. Głos Fish jest opcjonalny i wymaga własnego klucza Fish Audio wpisanego na telefonie. Bez niego wyłącz „Mów odpowiedzi na głos”; czat i awatar działają. Model AI nie wymaga klucza Google — komputer korzysta z zalogowanego Antigravity CLI.

## Komputer i Tailscale

Lokalna Capri nadal korzysta z portu 8787. Telefon korzysta z dodatkowego proxy na 127.0.0.1:8788 wymagającego osobnego tokenu. Tailscale Serve przekazuje prywatny HTTPS 8443 do tego proxy. Nie używamy publicznego Tailscale Funnel ani przekierowania portów routera.

Tailscale Serve/HTTPS jest już włączone i sprawdzone dla tego komputera. Po restarcie komputera uruchom:

`C:\Users\53645\Documents\ChatGPT\Prywa\local-chat-mcp\Start-Capri-Mobile.ps1`

Skrypt uruchamia ukryte procesy serwera. Po restarcie komputera uruchom go ponownie. Nie włączono wyłączonych wcześniej automatycznych aktualizacji ani cyklicznych okien konsoli.

W razie braku odpowiedzi sprawdź Tailscale, włączenie komputera, działanie Start-Capri-Mobile.ps1 i klucz telefonu. `localhost` na telefonie oznacza telefon. Klucze są szyfrowane w Android Keystore i nie są ujawniane stronie WebView.

## Muzyka

Zachowano obsługę YouTube Music. Polecenia z modelu trafiają do narzędzia `control_phone` i są wykonywane na telefonie po potwierdzeniu; nie wykonują działań na Windows. Usługi dostępności i powiadomień są opcjonalne — konfiguracja opisana w starszym README. Nie dodano dowolnych poleceń powłoki ani sterowania wszystkimi aplikacjami.

## Weryfikacja

- APK 0.2.0 (versionCode 2) został zbudowany i podpis zweryfikowany.
- 9 testów Androida: parser SSE/narzędzi i polskie komendy muzyczne — wszystkie przeszły.
- Android Lint: 0 błędów (ostrzeżenia pozostają w raporcie).
- 15 testów mostu Node przeszło, w tym wymaganie tokenu i odrzucanie nieznanych tras.
- Prawdziwy test proxy → Gemini: HTTP 200 i poprawne control_phone z wybranym utworem Night City.
- Telefon nie był podłączony: działanie na fizycznym S22 Ultra, usypianie Samsunga, mikrofon, głos Fish i odtwarzanie muzyki wymagają testu po instalacji.
- Pełny test prywatnego HTTPS przez Tailscale przeszedł: brak klucza → HTTP 401; poprawny klucz → HTTP 200, Gemini odpowiedziało MOBILE_OK.
