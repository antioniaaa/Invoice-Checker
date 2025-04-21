package de.anton.invoice.cecker.invoice_checker.model;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper; // Für Textextraktion
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JOptionPane; // Für Fehlermeldungen an den User
import java.awt.Desktop; // Zum Öffnen der CSV im Editor
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets; // Wichtig für korrekte CSV-Verarbeitung
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher; // Für Regex-Suche
import java.util.regex.Pattern; // Für Regex-Suche
import java.util.regex.PatternSyntaxException; // Für Regex-Fehlerbehandlung

import java.util.Optional; // Für findByKeyword

public class InvoiceTypeService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTypeService.class);
    private final Path configDir;
    private final Path csvPath;
    private List<InvoiceTypeConfig> invoiceTypes = new ArrayList<>();
    private InvoiceTypeConfig defaultConfig = null;

    private static final String CSV_FILENAME = "invoice-config.csv";
    private static final String CONFIG_SUBDIR = "invoice-type";
    public static final String DEFAULT_IDENTIFYING_KEYWORD = "Others"; // Identifiziert den Default Eintrag
    private static final String CSV_SEPARATOR = ";"; // Trennzeichen für CSV

    // --- Spaltenindizes (0-basiert) ---
    private static final int COL_TYPE = 0;
    private static final int COL_KEY_INC1 = 1; // Primärer Identifikator
    private static final int COL_KEY_INC2 = 2;
    private static final int COL_KEY_INC3 = 3;
    private static final int COL_KEY_EXC1 = 4;
    private static final int COL_KEY_EXC2 = 5;
    private static final int COL_AREA_TYPE = 6;
    private static final int COL_FLAVOR = 7;
    private static final int COL_ROW_TOL = 8;
    private static final int EXPECTED_COLUMNS = 9;


    public InvoiceTypeService() {
        Path baseDir = Paths.get("").toAbsolutePath();
        this.configDir = baseDir.resolve("configs").resolve(CONFIG_SUBDIR);
        this.csvPath = this.configDir.resolve(CSV_FILENAME);
        ensureConfigFileExists();
        loadConfigsFromCsv(); // Beim Start laden
    }

    private void ensureConfigFileExists() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(csvPath)) {
                log.info("Datei {} nicht gefunden, erstelle Standard.", csvPath.toAbsolutePath());
                createDefaultCsv();
            } else if (!Files.isReadable(csvPath)) { log.error("Keine Leseberechtigung: {}", csvPath.toAbsolutePath()); }
              else { log.info("Verwende Invoice-Type-Datei: {}", csvPath.toAbsolutePath()); }
        } catch (Exception e) { log.error("Fehler Sicherstellen/Erstellen Invoice-Type-Datei {}", csvPath.toAbsolutePath(), e); }
    }

    /** Erstellt eine Standard-CSV-Datei mit der neuen Spaltenstruktur. */
    private void createDefaultCsv() {
        String header = "Type;Keyword_incl_1;Keyword_incl_2;Keyword_incl_3;Keyword_excl_1;Keyword_excl_2;Area-Type;Flavor;Row Tol";
        String defaultContent = header + "\n" +
                "Netzbetreiber;E\\.DIS.*;Marktprämie;;Messstelle;;EDis_small;lattice;2\n" +
                "Netzbetreiber;Avacon.* AG;Marktprämie;;Messstelle;;Konfig*;Flavor*;Row Tol*\n" +
                "Netzbetreiber;WEMAG;Marktprämie;;;;Konfig*;Flavor*;Row Tol*\n" +
                "Direktvermarkter;Interconnector;;;;;Konfig*;Flavor*;Row Tol*\n" +
                "Direktvermarkter;Next Kraftwerke;;;;;Konfig;lattice;2\n" +
                "Direktvermarkter;Quadra;;;;;Konfig*;Flavor*;Row Tol*\n" +
                "Others;" + DEFAULT_IDENTIFYING_KEYWORD + ";;;;;Konfig*;stream;5\n";
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(defaultContent);
            log.info("Standard-Invoice-Type-Datei {} erstellt.", csvPath.toAbsolutePath());
        } catch (IOException e) { log.error("Konnte Standard-Invoice-Type-Datei nicht schreiben: {}", csvPath.toAbsolutePath(), e); }
    }

    /** Lädt die Konfigurationen aus der CSV-Datei. */
    private synchronized void loadConfigsFromCsv() {
        List<InvoiceTypeConfig> tempConfigs = new ArrayList<>();
        InvoiceTypeConfig tempDefaultConfig = null;
        if (!Files.exists(csvPath) || !Files.isReadable(csvPath)) { log.error("Kann Invoice-Typen nicht laden.", csvPath.toAbsolutePath()); tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig); }
        else {
            log.info("Lade Rechnungstypen aus CSV: {}", csvPath.toAbsolutePath());
            try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
                String line; boolean isHeader = true; int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (isHeader || line.trim().isEmpty() || line.trim().startsWith("#")) { isHeader = false; continue; }
                    String[] parts = line.split(CSV_SEPARATOR, -1); // Verwende Konstante
                    if (parts.length >= EXPECTED_COLUMNS) {
                        InvoiceTypeConfig config = new InvoiceTypeConfig( parts[COL_TYPE], parts[COL_KEY_INC1], parts[COL_KEY_INC2], parts[COL_KEY_INC3], parts[COL_KEY_EXC1], parts[COL_KEY_EXC2], parts[COL_AREA_TYPE], parts[COL_FLAVOR], parts[COL_ROW_TOL] );
                        tempConfigs.add(config);
                        if (DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(config.getIdentifyingKeyword())) { tempDefaultConfig = config; }
                        log.trace("Zeile {} geladen: {}", lineNumber, config);
                    } else { log.warn("Überspringe CSV-Zeile {} ({} Spalten statt {}): {}", lineNumber, parts.length, EXPECTED_COLUMNS, line); }
                }
                if (tempDefaultConfig == null && !tempConfigs.isEmpty()) { log.warn("Kein '{}' Keyword in CSV. Erstelle internen Fallback.", DEFAULT_IDENTIFYING_KEYWORD); tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig); }
                else if (tempConfigs.isEmpty()) { log.warn("CSV {} war leer/ungültig. Erstelle Fallback.", csvPath.toAbsolutePath()); tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig); }
                log.info("{} Rechnungstypen erfolgreich geladen.", tempConfigs.size());
            } catch (IOException e) { log.error("Fehler Lesen Invoice-Type-Datei {}: {}", csvPath.toAbsolutePath(), e); tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig); }
        }
        synchronized(this.invoiceTypes) { this.invoiceTypes.clear(); this.invoiceTypes.addAll(tempConfigs); this.defaultConfig = tempDefaultConfig; }
    }

    /** Erstellt temporären Default. */
    private InvoiceTypeConfig createTempDefault() { return new InvoiceTypeConfig("Others", DEFAULT_IDENTIFYING_KEYWORD, "", "", "", "", InvoiceTypeConfig.USE_MANUAL_CONFIG, InvoiceTypeConfig.USE_MANUAL_FLAVOR, InvoiceTypeConfig.USE_MANUAL_ROW_TOL); }

    /** Findet Konfig für PDF basierend auf Keywords. */
    public synchronized InvoiceTypeConfig findConfigForPdf(PDDocument pdfDocument) {
        InvoiceTypeConfig fallbackConfig = (this.defaultConfig != null) ? this.defaultConfig : createTempDefault();
        if (pdfDocument == null) return fallbackConfig;
        String text = "";
        try { if (pdfDocument.getNumberOfPages() > 0) { PDFTextStripper s=new PDFTextStripper(); s.setStartPage(1); s.setEndPage(1); text=s.getText(pdfDocument); }}
        catch (Exception e) { log.error("Fehler Textextraktion: {}", e.getMessage()); }
        if (text.isEmpty()) return fallbackConfig;
        List<InvoiceTypeConfig> currentConfigs; synchronized(this.invoiceTypes) { currentConfigs = new ArrayList<>(this.invoiceTypes); }
        for (InvoiceTypeConfig config : currentConfigs) {
            if (DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(config.getIdentifyingKeyword())) continue;
            boolean allIncludesMatch = true;
            if (!isPatternFound(config.getKeywordIncl1(), text)) allIncludesMatch = false;
            if (allIncludesMatch && !config.getKeywordIncl2().isEmpty() && !isPatternFound(config.getKeywordIncl2(), text)) allIncludesMatch = false;
            if (allIncludesMatch && !config.getKeywordIncl3().isEmpty() && !isPatternFound(config.getKeywordIncl3(), text)) allIncludesMatch = false;
            boolean anyExcludesMatch = false;
            if (allIncludesMatch) {
                if (!config.getKeywordExcl1().isEmpty() && isPatternFound(config.getKeywordExcl1(), text)) anyExcludesMatch = true;
                if (!anyExcludesMatch && !config.getKeywordExcl2().isEmpty() && isPatternFound(config.getKeywordExcl2(), text)) anyExcludesMatch = true;
            }
            if (allIncludesMatch && !anyExcludesMatch) { log.info("Keyword '{}' gefunden -> Typ: {}", config.getIdentifyingKeyword(), config.getType()); return config; }
        }
        log.info("Kein spezifisches Keyword gefunden, verwende Default '{}'.", fallbackConfig.getIdentifyingKeyword());
        return fallbackConfig;
    }

    /** Hilfsmethode für Regex-Suche. */
    private boolean isPatternFound(String patternString, String text) {
        if (patternString == null || patternString.isBlank() || text == null || text.isEmpty()) return false;
        try { Pattern p = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.DOTALL); Matcher m = p.matcher(text); return m.find(); }
        catch (PatternSyntaxException e) { log.error("Ungültiges Regex '{}': {}", patternString, e.getMessage()); return false; }
        catch (Exception e) { log.error("Fehler Regex-Suche '{}': {}", patternString, e.getMessage()); return false; }
    }

    /** Öffnet CSV im Editor. */
    public void openCsvInEditor() {
         if (configDir == null) { JOptionPane.showMessageDialog(null, "Konfig-Verzeichnis nicht initialisiert!", "Fehler", JOptionPane.ERROR_MESSAGE); return; }
         if (Files.exists(csvPath)) { try { if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) { Desktop.getDesktop().open(csvPath.toFile()); } else { JOptionPane.showMessageDialog(null, "Autom. Öffnen nicht unterstützt.\nPfad: " + csvPath.toAbsolutePath(), "Nicht unterstützt", JOptionPane.WARNING_MESSAGE); } } catch (Exception e) { JOptionPane.showMessageDialog(null, "Fehler beim Öffnen:\n" + e.getMessage() + "\nPfad: " + csvPath.toAbsolutePath(), "Fehler", JOptionPane.ERROR_MESSAGE); } }
         else { JOptionPane.showMessageDialog(null, "Datei nicht gefunden:\n" + csvPath.toAbsolutePath(), "Fehler", JOptionPane.ERROR_MESSAGE); }
    }

    /** Lädt Konfigs neu. */
    public synchronized void reloadConfigs() { log.info("Lade Invoice-Type-Konfigurationen neu aus CSV..."); loadConfigsFromCsv(); }

    /** Gibt Kopie der Konfigs zurück. */
    public synchronized List<InvoiceTypeConfig> getAllConfigs() { // Umbenannt für Klarheit
        return new ArrayList<>(this.invoiceTypes);
    }

    /** Gibt Default-Konfig zurück. */
    public synchronized InvoiceTypeConfig getDefaultConfig() { return defaultConfig != null ? defaultConfig : createTempDefault(); }

    /**
     * Findet eine Konfiguration anhand ihres primären Keywords.
     * @param keyword Das primäre Keyword (Keyword_incl_1).
     * @return Ein Optional mit der gefundenen Konfiguration oder ein leeres Optional.
     */
    public synchronized Optional<InvoiceTypeConfig> findConfigByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }
        return this.invoiceTypes.stream()
                .filter(cfg -> keyword.equals(cfg.getIdentifyingKeyword()))
                .findFirst();
    }

    // --- NEUE CRUD Methoden ---

    /**
     * Fügt eine neue Rechnungstyp-Konfiguration zur CSV-Datei hinzu.
     * Prüft vorher, ob das primäre Keyword bereits existiert.
     * @param newConfig Die hinzuzufügende Konfiguration.
     * @return true bei Erfolg, false wenn Keyword bereits existiert oder ein IO-Fehler auftritt.
     */
    public synchronized boolean addConfigToCsv(InvoiceTypeConfig newConfig) {
        if (newConfig == null || newConfig.getIdentifyingKeyword() == null || newConfig.getIdentifyingKeyword().isBlank()) {
            log.error("Ungültige neue Konfiguration zum Hinzufügen.");
            return false;
        }
        if (DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(newConfig.getIdentifyingKeyword())) {
             log.error("Das Default-Keyword '{}' kann nicht explizit hinzugefügt werden.", DEFAULT_IDENTIFYING_KEYWORD);
             return false;
        }
        // Prüfen, ob Keyword schon existiert
        if (findConfigByKeyword(newConfig.getIdentifyingKeyword()).isPresent()) {
            log.error("Keyword '{}' existiert bereits. Hinzufügen nicht möglich.", newConfig.getIdentifyingKeyword());
            return false;
        }
        if (!Files.exists(csvPath) || !Files.isReadable(csvPath) || !Files.isWritable(csvPath)) {
            log.error("CSV-Datei {} nicht vorhanden/lesbar/schreibbar. Hinzufügen nicht möglich.", csvPath.toAbsolutePath());
            return false;
        }

        log.info("Füge neue Konfiguration zur CSV hinzu: {}", newConfig);
        // Formatiere die neue Zeile
        String newLine = String.join(CSV_SEPARATOR,
            escapeCsvField(newConfig.getType()), escapeCsvField(newConfig.getKeywordIncl1()), escapeCsvField(newConfig.getKeywordIncl2()),
            escapeCsvField(newConfig.getKeywordIncl3()), escapeCsvField(newConfig.getKeywordExcl1()), escapeCsvField(newConfig.getKeywordExcl2()),
            escapeCsvField(newConfig.getAreaType()), escapeCsvField(newConfig.getDefaultFlavor()), escapeCsvField(newConfig.getDefaultRowTol())
        );

        try {
            // Füge die neue Zeile am Ende der Datei an (mit Zeilenumbruch davor)
            Files.writeString(csvPath, System.lineSeparator() + newLine, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            log.info("Neue Konfiguration für '{}' erfolgreich zur CSV hinzugefügt.", newConfig.getIdentifyingKeyword());
            loadConfigsFromCsv(); // Interne Liste neu laden
            return true;
        } catch (IOException e) {
            log.error("Fehler beim Anhängen an die CSV-Datei {}: {}", csvPath.toAbsolutePath(), e.getMessage(), e);
            return false;
        }
    }


    /**
     * Aktualisiert eine vorhandene Konfiguration in der CSV-Datei.
     * Identifiziert die Zeile anhand des primären Keywords.
     * @param updatedConfig Das Konfigurationsobjekt mit den neuen Werten. Das primäre Keyword darf nicht geändert werden.
     * @return true bei Erfolg, false wenn Keyword nicht gefunden oder IO-Fehler.
     */
    public synchronized boolean updateConfigInCsv(InvoiceTypeConfig updatedConfig) {
        if (updatedConfig == null || updatedConfig.getIdentifyingKeyword() == null || updatedConfig.getIdentifyingKeyword().isBlank()) {
            log.error("Ungültige Konfiguration zum Aktualisieren.");
            return false;
        }
        String keywordToUpdate = updatedConfig.getIdentifyingKeyword();
        if (DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(keywordToUpdate)) {
             log.error("Der Default-Eintrag '{}' kann nicht über diese Methode aktualisiert werden.", DEFAULT_IDENTIFYING_KEYWORD);
             return false;
        }
        if (!Files.exists(csvPath) || !Files.isReadable(csvPath) || !Files.isWritable(csvPath)) {
            log.error("CSV-Datei {} nicht vorhanden/lesbar/schreibbar. Update nicht möglich.", csvPath.toAbsolutePath());
            return false;
        }

        log.info("Aktualisiere CSV für Keyword '{}'", keywordToUpdate);
        List<String> originalLines;
        List<String> modifiedLines = new ArrayList<>();
        boolean lineUpdated = false;
        boolean isHeader = true;

        try {
            originalLines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            for (String line : originalLines) {
                if (isHeader || line.trim().isEmpty() || line.trim().startsWith("#")) {
                    modifiedLines.add(line);
                    if (!line.trim().isEmpty() && !line.trim().startsWith("#")) isHeader = false;
                    continue;
                }
                String[] parts = line.split(CSV_SEPARATOR, -1);
                if (parts.length > COL_KEY_INC1 && keywordToUpdate.equals(parts[COL_KEY_INC1].trim())) {
                    // Zeile gefunden, baue neue Zeile aus updatedConfig
                     String updatedLine = String.join(CSV_SEPARATOR,
                        escapeCsvField(updatedConfig.getType()), escapeCsvField(updatedConfig.getKeywordIncl1()), escapeCsvField(updatedConfig.getKeywordIncl2()),
                        escapeCsvField(updatedConfig.getKeywordIncl3()), escapeCsvField(updatedConfig.getKeywordExcl1()), escapeCsvField(updatedConfig.getKeywordExcl2()),
                        escapeCsvField(updatedConfig.getAreaType()), escapeCsvField(updatedConfig.getDefaultFlavor()), escapeCsvField(updatedConfig.getDefaultRowTol())
                    );
                    modifiedLines.add(updatedLine);
                    lineUpdated = true;
                    log.debug("Zeile für Keyword '{}' aktualisiert: {}", keywordToUpdate, updatedLine);
                } else {
                    modifiedLines.add(line); // Übernehme andere Zeilen unverändert
                }
            }

            if (lineUpdated) {
                // Schreibe modifizierte Daten zurück
                try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (int i = 0; i < modifiedLines.size(); i++) { writer.write(modifiedLines.get(i)); if (i < modifiedLines.size() - 1) writer.newLine(); }
                }
                log.info("CSV-Datei {} erfolgreich aktualisiert.", csvPath.toAbsolutePath());
                loadConfigsFromCsv(); // Lade interne Liste neu
                return true;
            } else { log.warn("Keyword '{}' für Update nicht in CSV gefunden.", keywordToUpdate); return false; }
        } catch (IOException e) { log.error("Fehler Lesen/Schreiben CSV {}: {}", csvPath.toAbsolutePath(), e.getMessage(), e); return false; }
    }

    /**
     * Löscht die Konfiguration mit dem angegebenen primären Keyword aus der CSV-Datei.
     * @param keywordToDelete Das primäre Keyword (Keyword_incl_1) des zu löschenden Eintrags.
     * @return true bei Erfolg, false wenn Keyword nicht gefunden, der Default gelöscht werden soll oder IO-Fehler.
     */
    public synchronized boolean deleteConfigFromCsv(String keywordToDelete) {
         if (keywordToDelete == null || keywordToDelete.isBlank() || DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(keywordToDelete)) {
            log.error("Löschen für ungültiges oder Default-Keyword '{}' nicht erlaubt.", keywordToDelete);
            return false;
        }
        if (!Files.exists(csvPath) || !Files.isReadable(csvPath) || !Files.isWritable(csvPath)) {
            log.error("CSV-Datei {} nicht vorhanden/lesbar/schreibbar. Löschen nicht möglich.", csvPath.toAbsolutePath());
            return false;
        }

        log.info("Lösche Konfiguration mit Keyword '{}' aus CSV.", keywordToDelete);
        List<String> originalLines;
        List<String> remainingLines = new ArrayList<>();
        boolean lineDeleted = false;
        boolean isHeader = true;

        try {
            originalLines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
             for (String line : originalLines) {
                 if (isHeader || line.trim().isEmpty() || line.trim().startsWith("#")) {
                     remainingLines.add(line); // Behalte Header, Kommentare, leere Zeilen
                     if (!line.trim().isEmpty() && !line.trim().startsWith("#")) isHeader = false;
                     continue;
                 }
                 String[] parts = line.split(CSV_SEPARATOR, -1);
                 // Füge Zeile nur hinzu, wenn Keyword NICHT übereinstimmt
                 if (parts.length <= COL_KEY_INC1 || !keywordToDelete.equals(parts[COL_KEY_INC1].trim())) {
                     remainingLines.add(line);
                 } else {
                     lineDeleted = true; // Markiere, dass die Zeile entfernt wird
                     log.debug("Entferne Zeile für Keyword '{}': {}", keywordToDelete, line);
                 }
             }

             if (lineDeleted) {
                  // Schreibe die verbleibenden Zeilen zurück
                 try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (int i = 0; i < remainingLines.size(); i++) { writer.write(remainingLines.get(i)); if (i < remainingLines.size() - 1) writer.newLine(); }
                 }
                 log.info("Konfiguration für Keyword '{}' erfolgreich aus CSV gelöscht.", keywordToDelete);
                 loadConfigsFromCsv(); // Lade interne Liste neu
                 return true;
             } else {
                 log.warn("Keyword '{}' zum Löschen nicht in CSV gefunden.", keywordToDelete);
                 return false;
             }
        } catch (IOException e) {
             log.error("Fehler beim Lesen/Schreiben der CSV-Datei {}: {}", csvPath.toAbsolutePath(), e.getMessage(), e);
             return false;
        }
    }

    /**
     * Hilfsmethode zum Escapen von CSV-Feldern (optional, falls Werte Semikolons oder Anführungszeichen enthalten könnten).
     * Aktuell einfache Implementierung.
     * @param field Der Feldinhalt.
     * @return Der potenziell escapte Feldinhalt.
     */
     private String escapeCsvField(String field) {
         if (field == null) return "";
         // Einfache Prüfung: Wenn Semikolon oder Anführungszeichen drin sind, in Anführungszeichen setzen
         // und interne Anführungszeichen verdoppeln.
         if (field.contains(CSV_SEPARATOR) || field.contains("\"")) {
             return "\"" + field.replace("\"", "\"\"") + "\"";
         }
         return field;
     }
}