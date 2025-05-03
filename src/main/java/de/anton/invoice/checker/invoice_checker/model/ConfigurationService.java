package de.anton.invoice.checker.invoice_checker.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.opencsv.*;
import com.opencsv.exceptions.CsvException;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Verwaltet das Laden und Speichern von Konfigurationen.
 * - {@link ExtractionConfiguration}-Objekte als JSON-Dateien im Unterverzeichnis 'configs/area'.
 * - {@link InvoiceTypeConfig}-Objekte als CSV-Datei in 'configs/invoice-type'.
 */
public class ConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    // --- Konstanten für Verzeichnisse und Dateinamen ---
    private static final String baseDir = "";
    private static final String CONFIG_BASE_PATH = "configs";
    private static final String AREA_CONFIG_SUBDIR = "area";
    private static final String TYPE_CONFIG_SUBDIR = "invoice-type";
    private static final String TYPE_CONFIG_FILENAME = "invoice-config.csv";
    private static final String JSON_SUFFIX = ".json";
    private static final String CSV_SUFFIX = ".csv";

    private final Path configBaseDir;
    private final Path areaConfigDir;
    private final Path typeConfigDir;
    private final Path invoiceTypeCsvFile;
    private final Gson gson;

    public ConfigurationService() {
        this.configBaseDir = Paths.get(baseDir).toAbsolutePath();
        this.areaConfigDir = this.configBaseDir.resolve(CONFIG_BASE_PATH).resolve(AREA_CONFIG_SUBDIR);
        log.debug("areaConfigDir: {}", areaConfigDir);
        this.typeConfigDir = this.configBaseDir.resolve(CONFIG_BASE_PATH).resolve(TYPE_CONFIG_SUBDIR);
        log.debug("typeConfigDir: {}", typeConfigDir);
        this.invoiceTypeCsvFile = this.typeConfigDir.resolve(TYPE_CONFIG_FILENAME);

        // Sicherstellen, dass Verzeichnisse existieren
        createDirectoryIfNotExists(this.areaConfigDir);
        createDirectoryIfNotExists(this.typeConfigDir);

        // Gson initialisieren (ersetzt ObjectMapper)
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Hilfsmethode zum Erstellen von Verzeichnissen
     */
    private void createDirectoryIfNotExists(Path dir) {
        if (Files.notExists(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("Konfigurationsverzeichnis erstellt: {}", dir);
            } catch (IOException e) {
                log.error("Fehler beim Erstellen des Konfigurationsverzeichnis '{}': {}", dir, e.getMessage(), e);
                // Optional: Werfe hier eine RuntimeException, wenn das Verzeichnis kritisch ist
            }
        } else if (!Files.isDirectory(dir)) {
            log.error("'{}' existiert, ist aber kein Verzeichnis!", dir);
            // Optional: Werfe hier eine RuntimeException
        } else {
            log.info("Verwende Konfigurationsverzeichnis: {}", dir);
        }
    }

    // --- Bereichs-Konfigurationen (JSON mit Gson) ---

    /**
     * Speichert eine Extraktionskonfiguration als JSON-Datei.
     * Der Dateiname basiert auf config.getName().
     *
     * @param config Die zu speichernde Konfiguration.
     * @throws IOException              Wenn das Verzeichnis nicht verfügbar ist oder ein Schreibfehler auftritt.
     * @throws IllegalArgumentException Wenn Konfiguration oder Name ungültig ist.
     */
    public void saveConfiguration(ExtractionConfiguration config) throws IOException, IllegalArgumentException {
        if (areaConfigDir == null || !Files.isDirectory(areaConfigDir))
            throw new IOException("Bereichs-Konfigurationsverzeichnis nicht verfügbar.");
        if (config == null || config.getName() == null || config.getName().isBlank())
            throw new IllegalArgumentException("Konfiguration und Konfigurationsname dürfen nicht leer sein.");

        String safeName = sanitizeFilename(config.getName());
        String fileName = safeName + JSON_SUFFIX;
        Path filePath = areaConfigDir.resolve(fileName);

        log.info("Speichere Bereichs-Konfiguration '{}' nach: {}", config.getName(), filePath);
        // try-with-resources für den Writer
        try (Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            gson.toJson(config, writer); // Gson zum Schreiben verwenden
        } catch (JsonIOException | IOException e) { // Fange spezifischere Gson IO Fehler
            log.error("Fehler beim Speichern der Bereichs-Konfiguration '{}': {}", config.getName(), e.getMessage(), e);
            throw new IOException("Fehler beim Speichern der Konfiguration '" + config.getName() + "'", e);
        }
    }

    /**
     * Lädt eine Extraktionskonfiguration anhand ihres Namens.
     *
     * @param name Der logische Name der Konfiguration (wird zum Dateinamen).
     * @return Die geladene Konfiguration oder null, wenn nicht gefunden oder Fehler auftritt.
     */
    public ExtractionConfiguration loadConfiguration(String name) {
        if (areaConfigDir == null || !Files.isDirectory(areaConfigDir)) {
            log.error("Bereichs-Konfigurationsverzeichnis nicht initialisiert.");
            return null;
        }
        if (name == null || name.isBlank()) return null;

        String safeName = sanitizeFilename(name);
        String fileName = safeName + JSON_SUFFIX;
        Path filePath = areaConfigDir.resolve(fileName);

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            log.warn("Bereichs-Konfigurationsdatei nicht gefunden oder lesbar: {}", filePath);
            return null;
        }

        log.info("Lade Bereichs-Konfiguration '{}' von: {}", name, filePath);
        // try-with-resources für den Reader
        try (Reader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            ExtractionConfiguration config = gson.fromJson(reader, ExtractionConfiguration.class); // Gson zum Lesen

            // Sanity check: Name in Datei vs. angefragter Name
            if (config != null && !name.equals(config.getName())) {
                log.warn("Name in Bereichs-Konfigdatei ('{}') weicht ab von angefragtem Namen '{}'. Setze Namen auf '{}'.",
                        config.getName(), name, name);
                config.setName(name); // Korrigiere den Namen im Objekt
            }
            // Sicherstellen, dass Areas-Liste nicht null ist
            if (config != null && config.getGlobalAreasList() == null) {
                config.setGlobalAreasList(new ArrayList<>());
            }
            return config;
        } catch (JsonSyntaxException e) {
            log.error("Fehler beim Parsen der JSON-Konfiguration '{}': {}", name, e.getMessage());
            return null;
        } catch (JsonIOException | IOException e) {
            log.error("Fehler beim Laden der Bereichs-Konfiguration '{}': {}", name, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gibt die logischen Namen aller verfügbaren Bereichs-Konfigurationen zurück.
     * (Dateinamen ohne .json-Endung).
     *
     * @return Eine sortierte Liste der Konfigurationsnamen.
     */
    public List<String> getAvailableConfigurationNames() {
        if (areaConfigDir == null || !Files.isDirectory(areaConfigDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> files = Files.list(areaConfigDir)) {
            return files.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.toLowerCase().endsWith(JSON_SUFFIX))
                    .map(name -> name.substring(0, name.length() - JSON_SUFFIX.length())) // Endung entfernen
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Fehler beim Auflisten der Bereichs-Konfigdateien in {}: {}", areaConfigDir, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Lädt alle verfügbaren Bereichs-Konfigurationen.
     *
     * @return Eine sortierte Liste aller geladenen Konfigurationen.
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
        // Sortiere nach dem Namen innerhalb der Konfiguration
        configs.sort(Comparator.comparing(ExtractionConfiguration::getName, String.CASE_INSENSITIVE_ORDER));
        log.info("{} Bereichs-Konfiguration(en) erfolgreich geladen.", configs.size());
        return configs;
    }

    /**
     * Löscht eine Bereichs-Konfigurationsdatei anhand des logischen Namens.
     *
     * @param name Der Name der zu löschenden Konfiguration.
     * @throws IOException Wenn das Verzeichnis nicht verfügbar ist oder ein Löschfehler auftritt.
     */
    public void deleteConfiguration(String name) throws IOException {
        if (areaConfigDir == null || !Files.isDirectory(areaConfigDir))
            throw new IOException("Bereichs-Konfigurationsverzeichnis nicht verfügbar.");
        if (name == null || name.isBlank()) return;

        String safeName = sanitizeFilename(name);
        String fileName = safeName + JSON_SUFFIX;
        Path filePath = areaConfigDir.resolve(fileName);

        log.info("Lösche Bereichs-Konfiguration '{}' von: {}", name, filePath);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Fehler beim Löschen der Konfiguration '{}': {}", name, e.getMessage(), e);
            throw new IOException("Fehler beim Löschen der Konfiguration '" + name + "'", e);
        }
    }

    // --- Rechnungstypen (CSV mit OpenCSV) ---

    /**
     * Lädt die Rechnungstypen aus der CSV-Datei.
     * Überspringt die Header-Zeile und leere Zeilen.
     *
     * @return Eine sortierte Liste von InvoiceTypeConfig-Objekten.
     * @throws IOException Bei Fehlern beim Lesen der Datei.
     */
    public List<InvoiceTypeConfig> loadInvoiceTypes() throws IOException {
        List<InvoiceTypeConfig> types = new ArrayList<>();

        // Prüfen, ob die Datei existiert und lesbar ist
        if (!Files.exists(invoiceTypeCsvFile)) {
            log.info("Datei für Rechnungstypen '{}' nicht gefunden. Gebe leere Liste zurück.", invoiceTypeCsvFile);
            return types;
        }
        if (!Files.isReadable(invoiceTypeCsvFile)) {
            log.error("Keine Leseberechtigung für: {}", invoiceTypeCsvFile);
            throw new IOException("Keine Leseberechtigung für: " + invoiceTypeCsvFile);
        }

        log.debug("Lade Rechnungstypen aus: {}", invoiceTypeCsvFile);

        // Parser vor dem try-with-resources erstellen
        CSVParser parser = new CSVParserBuilder()
                // .withSeparator(',') // Standard ist Komma
                .build();

        // Try-with-resources nur für Reader und CSVReader
        try (Reader reader = Files.newBufferedReader(invoiceTypeCsvFile, StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReaderBuilder(reader)
                     .withSkipLines(1) // Header überspringen
                     .withCSVParser(parser)
                     .build()) {

            String[] line;
            long lineNum = csvReader.getLinesRead();

            // Lese Zeile für Zeile
            while ((line = csvReader.readNext()) != null) {
                lineNum = csvReader.getLinesRead();

                // Manuelle Prüfung auf leere Zeile
                if (isLineEffectivelyEmpty(line)) {
                    log.trace("Überspringe leere oder fast leere Zeile {}", lineNum);
                    continue; // Nächste Zeile lesen
                }

                // *** KORREKTUR: Prüfe auf 9 Spalten und verwende den neuen Konstruktor ***
                if (line.length >= 9) {
                    // Extrahiere alle 9 Werte, trimme und behandle potentielle nulls
                    String type = line[0] != null ? line[0].trim() : "";
                    String keywordIncl1 = line[1] != null ? line[1].trim() : "";
                    String keywordIncl2 = line[2] != null ? line[2].trim() : "";
                    String keywordIncl3 = line[3] != null ? line[3].trim() : "";
                    String keywordExcl1 = line[4] != null ? line[4].trim() : "";
                    String keywordExcl2 = line[5] != null ? line[5].trim() : "";
                    String areaType = line[6] != null ? line[6].trim() : "";
                    String defaultFlavor = line[7] != null ? line[7].trim() : "";
                    String defaultRowTol = line[8] != null ? line[8].trim() : ""; // Bleibt String für den Konstruktor

                    // Prüfe, ob der Typ (Hauptidentifikator) vorhanden ist
                    if (!type.isEmpty()) {
                        // Erstelle Objekt mit dem 9-Argument-Konstruktor
                        types.add(new InvoiceTypeConfig(
                                type,
                                keywordIncl1,
                                keywordIncl2,
                                keywordIncl3,
                                keywordExcl1,
                                keywordExcl2,
                                areaType,
                                defaultFlavor,
                                defaultRowTol
                        ));
                        log.trace("Gelesener Rechnungstyp Zeile {}: Type='{}', Flavor='{}', Tol='{}'", lineNum, type, defaultFlavor, defaultRowTol);
                    } else {
                        // Typ ist leer, obwohl Zeile nicht komplett leer war -> Warnung
                        log.warn("Leerer Typ-Name in Zeile {} von '{}' ignoriert.", lineNum, invoiceTypeCsvFile.getFileName());
                    }
                } else {
                    // Zeile hatte nicht genug Spalten -> Warnung
                    log.warn("Ungültige Zeile {} in '{}' ignoriert (weniger als 9 Spalten erwartet). Inhalt: {}", lineNum, invoiceTypeCsvFile.getFileName(), String.join(",", line));
                }
            }
            // Fehlerbehandlung bleibt gleich
        } catch (CsvValidationException e) {
            log.error("Fehler beim Validieren der CSV-Daten in Zeile {}: {}", e.getLineNumber(), e.getMessage(), e);
            throw new IOException("Fehler beim Parsen der CSV-Datei '" + invoiceTypeCsvFile.getFileName() + "' in Zeile " + e.getLineNumber(), e);
        } catch (CsvException e) {
            log.error("Fehler beim Verarbeiten der CSV-Datei '{}': {}", invoiceTypeCsvFile.getFileName(), e.getMessage(), e);
            throw new IOException("Fehler beim Verarbeiten der CSV-Datei '" + invoiceTypeCsvFile.getFileName() + "'", e);
        } catch (IOException e) {
            log.error("Fehler beim Lesen der CSV-Datei '{}': {}", invoiceTypeCsvFile.getFileName(), e.getMessage(), e);
            throw e;
        }

        log.info("{} Rechnungstyp(en) erfolgreich geladen aus '{}'.", types.size(), invoiceTypeCsvFile.getFileName());

        // Sortiere die Liste nach Namen
        types.sort(Comparator.comparing(InvoiceTypeConfig::getType, String.CASE_INSENSITIVE_ORDER)); // Annahme: getTypeName() existiert

        return types;
    }

    /**
     * Hilfsmethode zum Prüfen, ob ein aus CSV gelesenes String-Array effektiv leer ist
     * (d.h., alle Felder sind null oder leere/whitespace Strings).
     */
    private boolean isLineEffectivelyEmpty(String[] line) {
        if (line == null) {
            return true;
        }
        for (String s : line) {
            if (s != null && !s.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }


    /**
     * Speichert die übergebene Liste von Rechnungstypen in die CSV-Datei.
     * Überschreibt die bestehende Datei.
     *
     * @param types Die Liste der zu speichernden InvoiceTypeConfig-Objekte.
     * @throws IOException Wenn das Verzeichnis nicht verfügbar ist oder ein Schreibfehler auftritt.
     */
    public void saveInvoiceTypes(List<InvoiceTypeConfig> types) throws IOException {
        if (typeConfigDir == null || !Files.isDirectory(typeConfigDir))
            throw new IOException("Rechnungstyp-Konfigurationsverzeichnis nicht verfügbar.");
        if (types == null) types = new ArrayList<>();

        log.info("Speichere {} Rechnungstyp(en) nach: {}", types.size(), invoiceTypeCsvFile);

        try (Writer writer = Files.newBufferedWriter(invoiceTypeCsvFile, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(writer)) { // Standard-Einstellungen

            // *** KORREKTUR: Header für 9 Spalten schreiben ***
            String[] header = {"TypeName", "KeywordIncl1", "KeywordIncl2", "KeywordIncl3",
                    "KeywordExcl1", "KeywordExcl2", "AreaType",
                    "DefaultFlavor", "DefaultRowTol"};
            csvWriter.writeNext(header);

            // Daten schreiben
            for (InvoiceTypeConfig type : types) {
                // *** KORREKTUR: Alle 9 Felder aus dem Objekt holen und schreiben ***
                String[] line = {
                        type.getType() != null ? type.getType() : "",
                        type.getKeywordIncl1() != null ? type.getKeywordIncl1() : "", // Annahme: Getter existieren
                        type.getKeywordIncl2() != null ? type.getKeywordIncl2() : "",
                        type.getKeywordIncl3() != null ? type.getKeywordIncl3() : "",
                        type.getKeywordExcl1() != null ? type.getKeywordExcl1() : "",
                        type.getKeywordExcl2() != null ? type.getKeywordExcl2() : "",
                        type.getAreaType() != null ? type.getAreaType() : "",
                        type.getDefaultFlavor() != null ? type.getDefaultFlavor() : "",
                        type.getDefaultRowTol() != null ? type.getDefaultRowTol() : "" // Als String schreiben
                };
                csvWriter.writeNext(line);
            }
            csvWriter.flush();
        } catch (Exception e) {
            log.error("Fehler beim Schreiben der Rechnungstypen-CSV '{}': {}", invoiceTypeCsvFile.getFileName(), e.getMessage(), e);
            throw new IOException("Fehler beim Schreiben der Rechnungstypen-CSV", e);
        }
    }

    // --- Hilfsmethoden ---

    /**
     * Bereinigt einen Dateinamen, indem ungültige Zeichen ersetzt werden.
     *
     * @param name Der ursprüngliche Name.
     * @return Ein für Dateisysteme sichererer Name.
     */
    private String sanitizeFilename(String name) {
        if (name == null) return "_null_";
        // Ersetzt die meisten problematischen Zeichen durch Unterstrich
        return name.trim().replaceAll("[^a-zA-Z0-9\\-_\\.]+", "_");
    }

    // --- Getter für Pfade (falls von außen benötigt) ---
    public Path getAreaConfigDir() {
        return areaConfigDir;
    }

    public Path getTypeConfigDir() {
        return typeConfigDir;
    }
}