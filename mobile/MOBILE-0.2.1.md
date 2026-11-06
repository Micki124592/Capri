# Capri Mobile 0.2.1 — przezroczysta, samodzielna postać

Zainstaluj artifacts/Capri-Mobile-0.2.1-S22Ultra.apk jako aktualizację obecnej Capri. Nie odinstalowuj aplikacji: aktualizacja z tym samym podpisem zachowuje adres Tailscale, klucz telefonu i Fish Audio.

Nakładka używa osobnej przezroczystej sceny, bez panelu, paska CAPRI, orbit, przycisków i obramowania. Na ekranie widać wyłącznie postać.

- Włącz nakładkę jak dotychczas: Ustawienia Capri → Pokaż nad aplikacjami.
- Capri sama wybiera cele na ekranie, spaceruje, zatrzymuje się, rozgląda, przeciąga, siada i wykonuje krótkie animacje. Zachowanie jest lokalne i nie zużywa limitu Gemini.
- Krótkie dotknięcie wywołuje reakcję.
- Przeciągaj bezpośrednio postać. Po puszczeniu może zostać rzucona, opaść i wylądować przy dolnej krawędzi.
- Przytrzymaj postać przez około pół sekundy: otworzy się menu rozmowy, mikrofonu i chowania Capri.
- Możesz ją też schować przyciskiem w stałym powiadomieniu.
- Po wygaszeniu ekranu animacja i ruch są wstrzymane.

Po aktualizacji wyłącz starą nakładkę i włącz ją ponownie, aby uruchomić nowy wygląd. Jeśli Samsung usuwa usługę w tle, w ustawieniach baterii aplikacji Capri wybierz Bez ograniczeń.

Weryfikacja: kompilacja APK, 11 testów jednostkowych Androida i Android Lint (0 błędów). Test renderera w Chromium: przezroczyste narożniki, ponad 80% pikseli całkowicie przezroczystych, brak błędów JavaScript. Nakładka nie była testowana na fizycznym telefonie; po aktualizacji sprawdź ją na S22 Ultra.

Nie zmieniono sposobu logowania, serwera, uprawnień do mikrofonu ani sposobu włączania nakładki. Animacje są zbliżone charakterem do pulpitu, ale mechanika okien i interakcje z aplikacjami Windows nie zostały przeniesione na Androida.
