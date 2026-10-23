# Keybind Profiles — Minecraft 26.2 / Fabric

Ein clientseitiges Mod-Projekt für mehrere Keybind-Profile.

## Funktionen im aktuellen Prototyp
- Mehrere Profile lokal speichern
- Profil laden
- Aktuelle Tastenbelegungen in ein Profil speichern
- Profil umbenennen/löschen als nächste Erweiterung vorgesehen
- Export von Profilen als JSON
- Öffnen des Menüs über Optionen > Steuerung > Tastenkombination „Keybind-Profile öffnen“

## Voraussetzungen
- Java 25
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.152.0+26.2
- Gradle / Fabric Loom 1.17

## Bauen
Öffne das Projekt in IntelliJ IDEA als Gradle-Projekt und führe aus:

    ./gradlew build

Unter Windows:

    gradlew.bat build

Die fertige JAR liegt nach erfolgreichem Build in `build/libs/`.

## Speicherort
`<Minecraft-Ordner>/config/keybind-profiles/profiles.json`

Exportierte Profile:
`<Minecraft-Ordner>/config/keybind-profiles/exports/`

## Hinweis
Dies ist ein erster Quellcode-Prototyp und wurde in dieser Umgebung nicht gegen Minecraft 26.2 kompiliert oder im Spiel getestet. Die Minecraft-26.2-APIs können je nach konkreter Fabric-Mappings-/API-Version kleine Anpassungen erfordern. Import ist aktuell dateibasiert vorbereitet, die Auswahl im Menü muss noch fertig ergänzt werden.
