# CapriCorn

## Repozytorium i automatyczne aktualizacje

Repozytorium: https://github.com/Micki124592/Capri . Kod aplikacji znajduje się w `capri-desktop/`, a lokalny serwer MCP i adapter HTTP w `bridge/`. Zachowano licencję, informacje o autorach i licencje zasobów projektu źródłowego. Pliki ustawień, dane rozmów i logi nie należą do repozytorium.

Gałąź wdrożeniowa to `main`. Zmiany po commit/push do `main` są sprawdzane na skonfigurowanym komputerze co minutę, gdy użytkownik jest zalogowany. Aktualizator pobiera kod przez fast-forward, uruchamia testy Node, kompiluje aplikację i dopiero wtedy zastępuje działające pliki oraz restartuje Capri i most. Czas aktualizacji obejmuje oczekiwanie na kolejne sprawdzenie, pobranie i kompilację. Przy trwającej odpowiedzi mostu aktualizacja czeka do kolejnego sprawdzenia.

Lokalne zmiany w repozytorium zatrzymują aktualizację, dopóki nie zostaną zapisane i wysłane. Aktualizator nie wysyła zmian automatycznie. Nie wykonuje reset --hard ani nie usuwa lokalnych plików użytkownika. Ustawienia profilu `%LOCALAPPDATA%/CapriCorn` pozostają poza wdrożeniem. Przy błędzie uruchomienia przywracana jest kopia poprzedniej aplikacji i nadpisanych plików mostu.

Konfiguracja komputera: skopiuj `scripts/update.example.json` do ignorowanego `scripts/update.local.json` i uzupełnij ścieżki. Most wymaga lokalnych `bridge/config.json` i `bridge/mcp-client.json`; przykład konfiguracji wątku to `bridge/config.example.json`. Nie publikuj kluczy ani danych logowania Codex.

Ręczne sprawdzenie: `powershell -NoProfile -File scripts/Update-Capri.ps1 -CheckOnly`. Ręczne wdrożenie: `powershell -NoProfile -File scripts/Update-Capri.ps1 -Force`. Log aktualizatora: `.updates/update.log`; błędy: `.updates/errors.log`. Kopia poprzedniej aplikacji: `.updates/previous/`.

Zadanie Harmonogramu na skonfigurowanym komputerze nosi nazwę `Capri-GitHub-AutoUpdate`. Aby wyłączyć aktualizacje, wyłącz to zadanie w Harmonogramie zadań Windows.

Prywatny wariant Capri na Windows, oparty na projekcie Lachy'ego. Uruchom `Start-CapriCorn.cmd`.

Launcher włącza też chaos z trzema klonami (`--chaos`). Przytrzymanie Esc usuwa klony. Profil ma ustawione szybkie wędrówki, zaczepki w najwyższym tempie co około 45–105 sekund oraz oglądanie ekranu co około 30 sekund. Zajętość aplikacji, rozmowa i brak interesującej zmiany mogą opóźnić wypowiedź.

Chaos ma własny cykl animacji niezależny od modelu: ośmiosekundowy taniec bez muzyki, szybszy spacer, neonowy wybuch z pierścieniami i odłamkami oraz glitch. Kolejna akcja wypada co 12–20 sekund, gdy postać jest wolna. Udawany bluescreen jest planowany około 35 sekund po starcie, następnie nie częściej niż co 3 minuty; pełny ekran, przeciąganie postaci i zadania sterujące komputerem go opóźniają. Esc kończący klony wyłącza również ten cykl. Animacje nie zależą od limitu użycia Codex.

Profil korzysta z `http://127.0.0.1:8787/v1/chat/completions`, modelu `codex-capricorn` i języka polskiego. Serwer musi być uruchomiony. Most w `../Prywa/local-chat-mcp` obsługuje odpowiedzi strumieniowe SSE i obrazy ekranu przekazywane jako wejście obrazowe do Codex. Tekst technicznych komentarzy agenta jest pomijany. Własne narzędzia Capri korzystają z jej tekstowego protokołu; most nie implementuje natywnego OpenAI tool calling.

Ustawienia i rozmowy tej wersji są w `%LOCALAPPDATA%\CapriCorn`. Parametr `--capricorn` wybiera osobny profil i osobną instancję. Pierwsze uruchomienie ustawia mniejszą postać (180 px) oraz wędrówki. Późniejsze zmiany użytkownika są zachowywane.

## Zabawa

Jednoznaczne polecenia muzyczne, np. „włącz Never Fade Away”, „puść utwór na YouTube”, „pauza”, „następny utwór” i „wznów muzykę”, mają lokalną ścieżkę bez oczekiwania na model. Nadal obowiązują zgody ustawione w Capri. Odtwarzanie omija zadania klikania po interfejsie, a oczekiwanie na automatyczne rozpoczęcie Spotify ma krótszy limit. Czas znalezienia utworu i uruchomienia odtwarzacza nadal zależy od sieci oraz aplikacji. Pytania i złożone prośby pozostają przy modelu.

Zadania komentarzy w tle są oznaczane jako takie w moście. Nowe pytanie użytkownika przerywa trwającą odpowiedź w tle, zamiast czekać na jej zakończenie; nie przerywa wcześniejszego pytania użytkownika. Teksty animacji i lore nadal powstają przez MCP.

Capri otrzymuje również [bazę lore](LORE.md) z odniesieniami do historii Edgerunners i świata Cyberpunka. Most dobiera różne motywy do zaczepek i reakcji; tekst nadal generuje model przez MCP.

- Kliknij postać prawym przyciskiem, aby otworzyć menu; Mainframe zawiera ustawienia postaci, głosu i zachowania.
- Przeciągaj i rzucaj postacią, sadzaj ją na oknach, uruchom muzykę, aby zobaczyć mini odtwarzacz (aplikacja odtwarzająca musi udostępniać multimedia systemowi Windows).
- Napisz `Chaos mode`, aby wywołać istniejący tryb chaosu z klonami i psikusami. Może przestawiać okna i przechwytywać kursor. Przytrzymaj Esc, aby przerwać.
- Zaczepki i check-iny konfiguruje się w Mainframe. W profilu CapriCorn wypowiedzi podczas rzucania, lądowania, dąsania, głaskania, samodzielnych zaczepek, powitania, świąt i wołania „Hey Capri” generuje model przez most MCP. Gotowe kwestie są wyłącznie opisem nastroju dla modelu, nie zastępczą wypowiedzią. Przy niedostępnym modelu reakcja nie jest wypowiadana. Animacja nie czeka na odpowiedź.
- Wypowiedzi animacji korzystają z aktualnie wybranego głosu (w tym Fish Audio). Seria szybkich zdarzeń zachowuje najnowszą oczekującą reakcję; odpowiedź przeterminowana po 30 sekundach lub przerwana nowym pytaniem nie jest odczytywana. Wspólny most kolejkuje żądania i daje pytaniom użytkownika pierwszeństwo przed oczekującymi reakcjami.
- Interfejs, wizualne napisy psikusów i techniczne komunikaty konfiguracji nadal pochodzą z aplikacji. Komentarze oglądania ekranu i dialogi klonów były już generowane przez model. Własne przypomnienia użytkownika zachowują treść.

To pierwsza działająca baza: zachowuje animacje, odtwarzacz i psikusy oryginału. Nie zawiera jeszcze przeniesienia wszystkich efektów z repozytorium mochiiwuff ani nowych sekwencji wybuchów.

## Budowanie

`capri-desktop/ci/build-windows.ps1` buduje aplikację i instalator. Bez Windows SDK można przekazać `-WindowsMetadata ./sdk-contracts/ref/netstandard2.0/Windows.WinMD` (pakiet Microsoft.Windows.SDK.Contracts 10.0.22621.2428 z NuGet).

Instalator oryginału uruchamia standardowy profil Capri. Do CapriCorn używaj launchera powyżej.

## Autorzy i licencja

Required Notice: Copyright 2026 Lachy (https://github.com/lachydotmcg)

Required Notice: Anyone who uses Capri's code, art, animations or any of its other assets must credit the Capri project (https://github.com/lachydotmcg/capri-desktop). Capri's art and animations are by her artist, who prefers to stay private. Parts made by others keep their own licences, listed in its README.

Baza: https://github.com/lachydotmcg/capri-desktop (PolyForm Noncommercial 1.0.0; pełne warunki w `capri-desktop/LICENSE.md`). Repozytorium referencyjne: https://github.com/mochiiwuff/capri-desktop. Zmiany lokalne: osobny profil CapriCorn i możliwość budowania z metadanymi SDK z NuGet.
