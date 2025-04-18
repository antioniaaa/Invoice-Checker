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
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);
    private final Path configDir; // Verzeichnis für Konfigurationsdateien
    private final ObjectMapper objectMapper; // Zum Lesen/Schreiben von JSON

    public ConfigurationService() {
        // Standard-Konfigurationsverzeichnis im Benutzer-Home-Verzeichnis
        // TODO: Pfad konfigurierbar machen
        String userHome = System.getProperty("user.home");
        this.configDir = Paths.get(userHome, ".PdfTabellenExtraktor", "configs");

        // Sicherstellen, dass das Verzeichnis existiert
        try {
            Files.createDirectories(configDir);
            log.info("Konfigurationsverzeichnis: {}", configDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("Konnte Konfigurationsverzeichnis nicht erstellen: {}", configDir, e);
            // Im Fehlerfall könnte man ein temporäres Verzeichnis nutzen oder die Funktion deaktivieren
        }

        // ObjectMapper für JSON konfigurieren
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT); // Schön formatierte JSON-Dateien
        // Fehlertoleranz beim Deserialisieren (optional)
        // this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Speichert eine Konfiguration als JSON-Datei.
     * Der Dateiname wird aus dem Konfigurationsnamen abgeleitet.
     *
     * @param config Die zu speichernde Konfiguration.
     * @throws IOException Wenn ein Fehler beim Schreiben auftritt.
     */
    public void saveConfiguration(ExtractionConfiguration config) throws IOException {
        if (config == null || config.getName() == null || config.getName().isBlank()) {
            throw new IllegalArgumentException("Konfiguration oder Name darf nicht leer sein.");
        }
        // Erzeuge einen sicheren Dateinamen
        String fileName = config.getName().replaceAll("[^a-zA-Z0-9\\-_\\.]", "_") + ".json";
        Path filePath = configDir.resolve(fileName);
        log.info("Speichere Konfiguration '{}' nach: {}", config.getName(), filePath);
        try {
            objectMapper.writeValue(filePath.toFile(), config);
        } catch (IOException e) {
            log.error("Fehler beim Speichern der Konfiguration '{}': {}", config.getName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Lädt eine Konfiguration aus einer JSON-Datei.
     *
     * @param name Der Name der Konfiguration (entspricht dem Dateinamen ohne .json).
     * @return Die geladene ExtractionConfiguration oder null bei Fehlern.
     */
    public ExtractionConfiguration loadConfiguration(String name) {
        if (name == null || name.isBlank()) return null;
        String fileName = name.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_") + ".json";
        Path filePath = configDir.resolve(fileName);

        if (!Files.exists(filePath)) {
            log.warn("Konfigurationsdatei nicht gefunden: {}", filePath);
            return null;
        }

        log.info("Lade Konfiguration '{}' von: {}", name, filePath);
        try {
            ExtractionConfiguration config = objectMapper.readValue(filePath.toFile(), ExtractionConfiguration.class);
            // Stelle sicher, dass der Name im Objekt dem Dateinamen entspricht
            if (config != null && !name.equals(config.getName())) {
                 log.warn("Name in Konfigurationsdatei ('{}') weicht vom Dateinamen ('{}') ab. Verwende Dateinamen.", config.getName(), name);
                 config.setName(name); // Korrigiere den Namen im geladenen Objekt
            }
            return config;
        } catch (IOException e) {
            log.error("Fehler beim Laden der Konfiguration '{}': {}", name, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gibt eine Liste der Namen aller verfügbaren Konfigurationsdateien zurück.
     *
     * @return Liste der Konfigurationsnamen (ohne .json Endung).
     */
    public List<String> getAvailableConfigurationNames() {
        if (!Files.isDirectory(configDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> files = Files.list(configDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.toLowerCase().endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length())) // Endung entfernen
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Fehler beim Auflisten der Konfigurationsdateien in {}: {}", configDir, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Lädt alle verfügbaren Konfigurationen.
     * @return Eine Liste aller erfolgreich geladenen ExtractionConfiguration-Objekte.
     */
     public List<ExtractionConfiguration> loadAllConfigurations() {
         List<String> names = getAvailableConfigurationNames();
         List<ExtractionConfiguration> configs = new ArrayList<>();
         for (String name : names) {
             ExtractionConfiguration config = loadConfiguration(name);
             if (config != null) {
                 configs.add(config);
             }
         }
         return configs;
     }
}
