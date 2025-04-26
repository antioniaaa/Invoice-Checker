package de.anton.invoice.checker.invoice_checker.model;

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


import java.util.Comparator;

/**
 * Verwaltet das Laden und Speichern von {@link ExtractionConfiguration}-Objekten
 * als JSON-Dateien im Unterverzeichnis 'configs/area'.
 */
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);
    private final Path configDir; // Verzeichnis: ./configs/area/
    private final ObjectMapper objectMapper;

    private static final String AREA_CONFIG_SUBDIR = "area"; // Unterverzeichnis

    public ConfigurationService() {
        Path resolvedConfigDir = null;
        try {
            Path baseDir = Paths.get("").toAbsolutePath();
            resolvedConfigDir = baseDir.resolve("configs").resolve(AREA_CONFIG_SUBDIR);
            
            
            if (!Files.exists(resolvedConfigDir)) { 
            	Files.createDirectories(resolvedConfigDir); 
            	log.info("Bereichs-Konfigurationsverzeichnis erstellt: {}", resolvedConfigDir.toAbsolutePath()); 
            	} else if (!Files.isDirectory(resolvedConfigDir)) { 
            		log.error("'{}' existiert, ist aber kein Verzeichnis!", resolvedConfigDir.toAbsolutePath()); 
            		resolvedConfigDir = null; 
            		} else { 
            			log.info("Verwende Bereichs-Konfigurationsverzeichnis: {}", resolvedConfigDir.toAbsolutePath()); 
            			}
        } catch (Exception e) { 
        	log.error("Fehler Initialisieren Bereichs-Konfig-Verzeichnis.", e); 
        	resolvedConfigDir = null; 
        	}
        this.configDir = resolvedConfigDir;
        this.objectMapper = new ObjectMapper(); this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Speichert Konfiguration. */
    public void saveConfiguration(ExtractionConfiguration config) throws IOException, IllegalArgumentException {
        if (configDir == null) throw new IOException("Bereichs-Konfigurationsverzeichnis nicht verfügbar.");
        if (config == null || config.getName() == null || config.getName().isBlank()) throw new IllegalArgumentException("Konfiguration/Name darf nicht leer sein.");
        String safeName = config.getName().replaceAll("[^a-zA-Z0-9\\-_\\.]", "_"); String fileName = safeName + ".json"; Path filePath = configDir.resolve(fileName);
        log.info("Speichere Bereichs-Konfiguration '{}' nach: {}", config.getName(), filePath.toAbsolutePath());
        try { objectMapper.writeValue(filePath.toFile(), config); }
        catch (IOException e) { log.error("Fehler beim Speichern der Bereichs-Konfiguration '{}': {}", config.getName(), e.getMessage()); throw e; }
    }

    /** Lädt Konfiguration. */
    public ExtractionConfiguration loadConfiguration(String name) {
        if (configDir == null) { log.error("Bereichs-Konfigurationsverzeichnis nicht initialisiert."); return null; }
        if (name == null || name.isBlank()) return null;
        String safeName = name.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_"); String fileName = safeName + ".json"; Path filePath = configDir.resolve(fileName);
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) { log.warn("Bereichs-Konfigurationsdatei nicht gefunden oder lesbar: {}", filePath.toAbsolutePath()); return null; }
        log.info("Lade Bereichs-Konfiguration '{}' von: {}", name, filePath.toAbsolutePath());
        try {
            ExtractionConfiguration config = objectMapper.readValue(filePath.toFile(), ExtractionConfiguration.class);
            if (config != null && !name.equals(config.getName())) { log.warn("Name in Bereichs-Konfigdatei ('{}') weicht ab von '{}'. Setze Namen auf '{}'.", config.getName(), name, name); config.setName(name); }
            return config;
        } catch (IOException e) { log.error("Fehler beim Laden/Parsen der Bereichs-Konfiguration '{}': {}", name, e.getMessage(), e); return null; }
    }

    /** Gibt Namen aller verfügbaren Bereichs-Konfigurationen zurück. */
    public List<String> getAvailableConfigurationNames() {
        if (configDir == null || !Files.isDirectory(configDir)) { return Collections.emptyList(); }
        try (Stream<Path> files = Files.list(configDir)) {
            return files.filter(Files::isRegularFile).map(Path::getFileName).map(Path::toString)
                    .filter(name -> name.toLowerCase().endsWith(".json")).map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
        } catch (IOException e) { log.error("Fehler Auflisten Bereichs-Konfigdateien in {}: {}", configDir.toAbsolutePath(), e.getMessage()); return Collections.emptyList(); }
    }

    /** Lädt alle verfügbaren Bereichs-Konfigurationen. */
     public List<ExtractionConfiguration> loadAllConfigurations() {
         List<String> names = getAvailableConfigurationNames(); List<ExtractionConfiguration> configs = new ArrayList<>();
         for (String name : names) { ExtractionConfiguration config = loadConfiguration(name); if (config != null) configs.add(config); }
         configs.sort(Comparator.comparing(ExtractionConfiguration::getName, String.CASE_INSENSITIVE_ORDER));
         log.info("{} Bereichs-Konfiguration(en) erfolgreich geladen.", configs.size());
         return configs;
     }
}