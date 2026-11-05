# Lokalny MCP — CapriCorn

Adapter przekazuje teraz definicje narzędzi Capri (`tools`) do Codex i zwraca wybrane akcje jako standardowe `tool_calls`, również w SSE. Narzędzia wykonuje Capri według własnych uprawnień; adapter sam nie uruchamia tych działań. Obsługiwane są także wiadomości z wynikami narzędzi. Test rzeczywisty: „Włącz jakiś hit Night City na Spotify” zwrócił play_music z samodzielnie wybranym utworem. Test potwierdza wybór akcji, nie odtwarzanie dźwięku. Odpowiedzi z narzędziami są buforowane do końca generowania, aby nie czytać JSON na głos.

Model backendu: `gpt-6-luna`, poziom rozumowania: `low` (Lekki). Ustawienia są zapisane w config.json i przekazywane przy każdej turze. W Capri pozostaje alias `codex-capricorn`.

## Podłączenie Capri przez ekran „Mózg”

Serwer ma dodatkowy adapter HTTP dla formatu oczekiwanego przez Capri:

- Źródło modelu: Zewnętrzne API.
- Dostawca: Własny endpoint (OpenAI-compatible).
- Model z listy: Inny model / wpisz ręcznie.
- Endpoint rozmowy: http://127.0.0.1:8787/v1/chat/completions
- Własny model: codex-capricorn (alias lokalnego wątku, nie nazwa modelu OpenAI).
- Klucz rozmowy: pozostaw pusty. Capri dopuszcza brak klucza dla localhost.

Kliknij „ZAPISZ API ROZMOWY I KLUCZ”, a następnie „TESTUJ POŁĄCZENIE API”. Nie wpisuj tutaj klucza OpenAI. Adapter nie używa zewnętrznego API OpenAI; korzysta z zalogowanego Codex. Format HTTP jest zgodny z potrzebnym przez Capri podzbiorem Chat Completions, stream:false. Parametry temperature/max_tokens nie zmieniają konfiguracji wątku Codex.

Uruchom serwer skryptem Start-Capri-Gateway.ps1; działa w tle. Po restarcie Windows uruchom skrypt ponownie. Nie dodano automatycznego startu systemowego. Logi: gateway.log i gateway-error.log. Endpoint diagnostyczny: http://127.0.0.1:8787/health.

Adapter nasłuchuje wyłącznie na 127.0.0.1, dla Capri na tym samym komputerze. Nie jest udostępniony w sieci LAN ani publicznie. Odrzuca żądania z nagłówkiem Origin. Obsługuje jeden stały wątek i jedną odpowiedź naraz; wiele rozmów Capri będzie współdzielić pamięć tego wątku. Pełny kontekst tekstowy przesłany przez Capri trafia do wątku Codex przy każdym żądaniu. Nie uruchamiaj równocześnie drugiego klienta MCP zapisującego ten sam wątek.

Test rzeczywisty adaptera: HTTP 200, choices[0].message.content = CAPRI_OK. Testy: node --test test.mjs gateway.test.mjs.

Wątek Codex: 01a1a618-d7ab-7263-ba3e-14e83a2a068c.
Folder roboczy: C:/Users/53645/Documents/ChatGPT/CapriCorn.
Docelowy projekt aplikacji: CapriCorn (e5a65ed5-244a-4a66-91a8-f2c932a162da).

Potwierdzono rzeczywistą wysyłkę i odpowiedź MCP_OK oraz odczyt po ponownym uruchomieniu połączenia. Wątek korzysta z folderu projektu. Formalne przypisanie w pasku bocznym nie powiodło się: aplikacja zwróciła „The task is not available in the sidebar”. projectId zapisany w config.json oznacza cel, nie potwierdzone członkostwo; backend zwraca obecnie projectId null.

Dodaj zawartość mcp-client.json w kliencie obsługującym lokalne MCP stdio. Klient sam uruchamia serwer. Nie trzeba instalować pakietów npm. Serwer korzysta z lokalnego Codex i obecnego logowania, bez klucza API OpenAI. Generowanie wymaga internetu i podlega limitom konta.

Narzędzia:
- chat_status {} — metadane wątku.
- chat_send {"message":"Treść"} — wysyłka, zwraca ID tury.
- chat_history {"limit":10} — treść i statusy tur. Dopasuj ID tury i odczytuj np. co sekundę do completed/failed/interrupted. Do starszych tur użyj nextCursor jako cursor.

Po timeout wysyłki sprawdź historię przed ponowieniem. Tuż po rozpoczęciu pierwszej tury historia może nie być jeszcze zapisana; ponów odczyt po chwili.

Wątek powinien mieć jeden proces zapisujący. Równoczesna praca w aplikacji może spowodować active writer. Nie usuwaj blokad. Nie dodawaj mostu do obsługiwanego wątku jako narzędzia do wywoływania samego siebie. Żądania zgód nie są automatycznie zatwierdzane; czynności wymagające interakcji mogą nie działać przez ten transport.

Po aktualizacji Codex może być konieczna zmiana codexPath w config.json. Testy: node --test test.mjs.

Dokumentacja: https://learn.chatgpt.com/docs/app-server
