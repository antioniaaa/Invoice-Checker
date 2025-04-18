package de.anton.invoice.cecker.invoice_checker.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Verwaltet das Laden und Speichern von Extraktionskonfigurationen.
 */

import java.util.Comparator; // Import für Sortierung

/**
 * Verwaltet das Laden und Speichern von {@link ExtractionConfiguration}-Objekten
 * als JSON-Dateien in einem 'configs'-Unterverzeichnis im Anwendungsverzeichnis.
 */
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);
    private final Path configDir; // Das Verzeichnis für Konfigurationsdateien
    private final ObjectMapper objectMapper; // Zum Lesen/Schreiben von JSON

    /**
     * Konstruktor. Ermittelt das Konfigurationsverzeichnis ('configs' im Startverzeichnis),
     * erstellt es bei Bedarf und initialisiert den JSON ObjectMapper.
     */
    public ConfigurationService() {
        Path resolvedConfigDir = null; // Temporäre Variable
        try {
            // Ermittle das aktuelle Arbeitsverzeichnis (Startverzeichnis der Anwendung)
            Path baseDir = Paths.get("").toAbsolutePath();
            // Definiere den Pfad zum 'configs'-Unterverzeichnis
            resolvedConfigDir = baseDir.resolve("configs");

            // Erstelle das Verzeichnis, wenn es nicht existiert
            if (!Files.exists(resolvedConfigDir)) {
                Files.createDirectories(resolvedConfigDir);
                log.info("Konfigurationsverzeichnis erstellt: {}", resolvedConfigDir.toAbsolutePath());
            } else if (!Files.isDirectory(resolvedConfigDir)) {
                // Fehlerfall: Ein Eintrag 'configs' existiert, ist aber keine Directory
                log.error("Ein Eintrag namens 'configs' existiert bereits, ist aber kein Verzeichnis: {}", resolvedConfigDir.toAbsolutePath());
                // Setze Pfad auf null, um weitere Operationen zu verhindern
                resolvedConfigDir = null;
            } else {
                // Verzeichnis existiert bereits
                log.info("Verwende Konfigurationsverzeichnis: {}", resolvedConfigDir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Konnte Konfigurationsverzeichnis nicht erstellen oder darauf zugreifen: {}", resolvedConfigDir, e);
            resolvedConfigDir = null; // Setze auf null im Fehlerfall
        } catch (Exception e) {
             log.error("Unerwarteter Fehler beim Ermitteln/Erstellen des Konfigurationsverzeichnisses.", e);
             resolvedConfigDir = null; // Setze auf null im Fehlerfall
        }
        // Weise den finalen (möglicherweise null) Pfad dem Feld zu
        this.configDir = resolvedConfigDir;

        // Initialisiere und konfiguriere den ObjectMapper für JSON
        this.objectMapper = new ObjectMapper();
        // Aktiviere Einrückung für lesbare JSON-Dateien
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Optional: Fehlertoleranz beim Lesen, falls zusätzliche Felder im JSON sind
        // this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Speichert eine {@link ExtractionConfiguration} als JSON-Datei im Konfigurationsverzeichnis.
     * Der Dateiname wird aus dem Namen der Konfiguration abgeleitet (Sonderzeichen ersetzt).
     * Überschreibt eine vorhandene Datei mit demselben Namen.
     *
     * @param config Die zu speichernde Konfiguration. Darf nicht null sein und muss einen Namen haben.
     * @throws IOException Wenn das Konfigurationsverzeichnis nicht verfügbar ist oder ein Fehler beim Schreiben auftritt.
     * @throws IllegalArgumentException Wenn die Konfiguration oder ihr Name ungültig ist.
     */
    public void saveConfiguration(ExtractionConfiguration config) throws IOException, IllegalArgumentException {
        // Prüfe, ob das Konfig-Verzeichnis gültig initialisiert wurde
        if (configDir == null) {
            throw new IOException("Konfigurationsverzeichnis ist nicht verfügbar.");
        }
        // Prüfe, ob die übergebene Konfiguration gültig ist
        if (config == null || config.getName() == null || config.getName().isBlank()) {
            throw new IllegalArgumentException("Konfiguration oder deren Name darf nicht leer sein.");
        }

        // Erzeuge einen sicheren Dateinamen aus dem Konfigurationsnamen
        // Ersetzt alle Zeichen außer Buchstaben, Zahlen, Bindestrich, Unterstrich, Punkt durch "_"
        String safeName = config.getName().replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
        String fileName = safeName + ".json";
        Path filePath = configDir.resolve(fileName); // Kombiniere Verzeichnis und Dateiname

        log.info("Speichere Konfiguration '{}' nach: {}", config.getName(), filePath.toAbsolutePath());
        try {
            // Schreibe das Konfigurationsobjekt als JSON in die Datei
            objectMapper.writeValue(filePath.toFile(), config);
        } catch (IOException e) {
            log.error("Fehler beim Speichern der Konfiguration '{}' nach {}: {}", config.getName(), filePath, e.getMessage());
            // Leite die Exception weiter
            throw e;
        }
    }

    /**
     * Lädt eine {@link ExtractionConfiguration} aus einer JSON-Datei im Konfigurationsverzeichnis.
     *
     * @param name Der Name der zu ladenden Konfiguration (entspricht dem Dateinamen ohne .json).
     * @return Die geladene ExtractionConfiguration oder null, wenn die Datei nicht existiert,
     *         nicht lesbar ist, ein Fehler beim Parsen auftritt oder der Name ungültig ist.
     */
    public ExtractionConfiguration loadConfiguration(String name) {
        // Prüfe auf gültigen Namen und initialisiertes Verzeichnis
        if (configDir == null) { log.error("Konfigurationsverzeichnis nicht initialisiert, Laden nicht möglich."); return null; }
        if (name == null || name.isBlank()) { log.warn("Ungültiger Name für Ladeversuch angegeben: {}", name); return null; }

        // Erzeuge Dateinamen und vollständigen Pfad
        String safeName = name.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
        String fileName = safeName + ".json";
        Path filePath = configDir.resolve(fileName);

        // Prüfe Existenz und Lesbarkeit der Datei
        if (!Files.exists(filePath)) {
            log.warn("Konfigurationsdatei nicht gefunden: {}", filePath.toAbsolutePath());
            return null;
        }
        if (!Files.isReadable(filePath)) {
             log.error("Keine Leseberechtigung für Konfigurationsdatei: {}", filePath.toAbsolutePath());
             return null;
        }

        log.info("Lade Konfiguration '{}' von: {}", name, filePath.toAbsolutePath());
        try {
            // Lese JSON-Datei und mappe sie auf das Konfigurationsobjekt
            ExtractionConfiguration config = objectMapper.readValue(filePath.toFile(), ExtractionConfiguration.class);

            // Sicherheitscheck: Stelle sicher, dass der Name im Objekt dem Dateinamen entspricht
            // (falls die Datei manuell bearbeitet wurde)
            if (config != null && !name.equals(config.getName())) {
                 log.warn("Name in Konfigurationsdatei ('{}') weicht vom erwarteten Namen ('{}') ab. Setze Namen auf '{}'.", config.getName(), name, name);
                 config.setName(name); // Korrigiere den Namen im geladenen Objekt
            }
            return config;
        } catch (IOException e) {
            // Fange Fehler beim Lesen oder Parsen der JSON-Datei ab
            log.error("Fehler beim Laden/Parsen der Konfiguration '{}' von {}: {}", name, filePath, e.getMessage(), e);
            return null; // Gib null zurück im Fehlerfall
        }
    }

    /**
     * Gibt eine Liste der Namen aller verfügbaren (gespeicherten) Konfigurationen zurück.
     * Liest alle .json-Dateien im Konfigurationsverzeichnis und extrahiert die Namen.
     *
     * @return Eine sortierte Liste der Konfigurationsnamen (ohne .json Endung). Gibt eine leere Liste zurück,
     *         wenn das Verzeichnis nicht existiert oder ein Fehler auftritt.
     */
    public List<String> getAvailableConfigurationNames() {
        // Prüfe, ob das Konfigurationsverzeichnis gültig ist
        if (configDir == null || !Files.isDirectory(configDir)) {
             if (configDir != null) log.warn("Konfigurationsverzeichnis '{}' ist kein gültiges Verzeichnis.", configDir.toAbsolutePath());
             else log.warn("Konfigurationsverzeichnis wurde nicht initialisiert.");
            return Collections.emptyList(); // Leere Liste zurückgeben
        }

        // Lese alle Dateien im Verzeichnis auf
        try (Stream<Path> files = Files.list(configDir)) {
            return files
                    .filter(Files::isRegularFile) // Nur reguläre Dateien berücksichtigen
                    .map(Path::getFileName)       // Nur den Dateinamen extrahieren
                    .map(Path::toString)          // In einen String umwandeln
                    .filter(name -> name.toLowerCase().endsWith(".json")) // Nur .json Dateien filtern
                    .map(name -> name.substring(0, name.length() - ".json".length())) // ".json" Endung entfernen
                    .sorted(String.CASE_INSENSITIVE_ORDER) // Namen sortieren (unabhängig von Groß/Kleinschreibung)
                    .collect(Collectors.toList()); // Als Liste sammeln
        } catch (IOException e) {
            // Fehler beim Auflisten der Dateien loggen
            log.error("Fehler beim Auflisten der Konfigurationsdateien in {}: {}", configDir.toAbsolutePath(), e.getMessage());
            return Collections.emptyList(); // Leere Liste im Fehlerfall
        }
    }

    /**
     * Lädt alle verfügbaren Konfigurationen aus dem Konfigurationsverzeichnis.
     *
     * @return Eine Liste aller erfolgreich geladenen ExtractionConfiguration-Objekte.
     *         Die Liste kann leer sein, wenn keine Konfigurationen gefunden wurden oder Fehler auftraten.
     */
     public List<ExtractionConfiguration> loadAllConfigurations() {
         List<String> names = getAvailableConfigurationNames(); // Hole Namen aller .json Dateien
         List<ExtractionConfiguration> configs = new ArrayList<>();
         // Lade jede Konfiguration einzeln anhand ihres Namens
         for (String name : names) {
             ExtractionConfiguration config = loadConfiguration(name);
             if (config != null) { // Füge nur erfolgreich geladene hinzu
                 configs.add(config);
             }
         }
         // Optional: Sortiere die geladenen Konfigurationen nach Namen
         configs.sort(Comparator.comparing(ExtractionConfiguration::getName, String.CASE_INSENSITIVE_ORDER));
         log.info("{} Konfiguration(en) erfolgreich geladen.", configs.size());
         return configs;
     }
}