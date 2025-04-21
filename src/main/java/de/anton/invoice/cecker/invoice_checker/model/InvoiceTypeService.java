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

public class InvoiceTypeService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTypeService.class);
    private final Path configDir;
    private final Path csvPath;
    private List<InvoiceTypeConfig> invoiceTypes = new ArrayList<>();
    private InvoiceTypeConfig defaultConfig = null;

    private static final String CSV_FILENAME = "invoice-config.csv";
    private static final String CONFIG_SUBDIR = "invoice-type";
    private static final String DEFAULT_IDENTIFYING_KEYWORD = "Others"; // Verwende KeywordIncl1 für Default

    // --- Spaltenindizes (0-basiert) ---
    private static final int COL_TYPE = 0;
    private static final int COL_KEY_INC1 = 1;
    private static final int COL_KEY_INC2 = 2;
    private static final int COL_KEY_INC3 = 3;
    private static final int COL_KEY_EXC1 = 4;
    private static final int COL_KEY_EXC2 = 5;
    private static final int COL_AREA_TYPE = 6;
    private static final int COL_FLAVOR = 7;
    private static final int COL_ROW_TOL = 8;
    private static final int EXPECTED_COLUMNS = 9; // Anzahl erwarteter Spalten


    public InvoiceTypeService() {
        Path baseDir = Paths.get("").toAbsolutePath();
        this.configDir = baseDir.resolve("configs").resolve(CONFIG_SUBDIR);
        this.csvPath = this.configDir.resolve(CSV_FILENAME);
        ensureConfigFileExists();
        loadConfigsFromCsv();
    }

    private void ensureConfigFileExists() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(csvPath)) {
                log.info("Konfigurationsdatei {} nicht gefunden, erstelle Standarddatei.", csvPath.toAbsolutePath());
                createDefaultCsv();
            } else if (!Files.isReadable(csvPath)) {
                 log.error("Keine Leseberechtigung für Konfigurationsdatei: {}", csvPath.toAbsolutePath());
            } else {
                 log.info("Verwende Invoice-Type-Konfigurationsdatei: {}", csvPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Fehler beim Sicherstellen/Erstellen der Invoice-Type-Konfigurationsdatei {}: {}", csvPath.toAbsolutePath(), e.getMessage());
        }
    }

    /** Erstellt eine Standard-CSV-Datei mit der neuen Spaltenstruktur. */
    private void createDefaultCsv() {
        String defaultContent =
                "Type;Keyword_incl_1;Keyword_incl_2;Keyword_incl_3;Keyword_excl_1;Keyword_excl_2;Area-Type;Flavor;Row Tol\n" + // Header angepasst
                "Netzbetreiber;E\\.DIS.*;Marktprämie;;Messstelle;;EDis_small;lattice;2\n" + // Keyword 1 + 2 müssen da sein, Messstelle nicht
                "Netzbetreiber;Avacon.* AG;Marktprämie;;Messstelle;;Konfig*;Flavor*;Row Tol*\n" + // Keyword 1 + 2 müssen da sein, Messstelle nicht
                "Netzbetreiber;WEMAG;Marktprämie;;;;Konfig*;Flavor*;Row Tol*\n" + // Keyword 1 + 2 müssen da sein
                "Direktvermarkter;Interconnector;;;;;Konfig*;Flavor*;Row Tol*\n" + // Nur Keyword 1
                "Direktvermarkter;Next;;;;;Konfig*;Flavor*;Row Tol*\n" +
                "Direktvermarkter;Quadra;;;;;Konfig*;Flavor*;Row Tol*\n" +
                "Others;" + DEFAULT_IDENTIFYING_KEYWORD + ";;;;;Konfig*;stream;5\n"; // Default-Keyword in Spalte 1
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(defaultContent);
            log.info("Standard-Invoice-Type-Datei {} erstellt.", csvPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Konnte Standard-Invoice-Type-Datei nicht schreiben: {}", csvPath.toAbsolutePath(), e);
        }
    }

    /** Lädt die Konfigurationen aus der CSV-Datei mit neuer Struktur. */
    private synchronized void loadConfigsFromCsv() {
        List<InvoiceTypeConfig> tempConfigs = new ArrayList<>();
        InvoiceTypeConfig tempDefaultConfig = null;

        if (!Files.exists(csvPath) || !Files.isReadable(csvPath)) {
            log.error("Kann Invoice-Typen nicht laden: {} nicht lesbar/existent.", csvPath.toAbsolutePath());
            tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig);
        } else {
            log.info("Lade Rechnungstypen aus CSV: {}", csvPath.toAbsolutePath());
            try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
                String line; boolean isHeader = true; int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (isHeader || line.trim().isEmpty() || line.trim().startsWith("#")) { isHeader = false; continue; }

                    String[] parts = line.split(";", -1);
                    if (parts.length >= EXPECTED_COLUMNS) { // Prüfe auf neue Spaltenanzahl
                        InvoiceTypeConfig config = new InvoiceTypeConfig(
                            parts[COL_TYPE], parts[COL_KEY_INC1], parts[COL_KEY_INC2], parts[COL_KEY_INC3],
                            parts[COL_KEY_EXC1], parts[COL_KEY_EXC2], parts[COL_AREA_TYPE],
                            parts[COL_FLAVOR], parts[COL_ROW_TOL]
                        );
                        tempConfigs.add(config);
                        if (DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(config.getIdentifyingKeyword())) {
                            tempDefaultConfig = config;
                        }
                        log.trace("CSV-Zeile {} geladen: {}", lineNumber, config);
                    } else { log.warn("Überspringe ungültige CSV-Zeile {} ({} Spalten statt {}): {}", lineNumber, parts.length, EXPECTED_COLUMNS, line); }
                }
                if (tempDefaultConfig == null && !tempConfigs.isEmpty()) { log.warn("Kein '{}' Keyword in CSV gefunden.", DEFAULT_IDENTIFYING_KEYWORD); tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig); }
                else if (tempConfigs.isEmpty()) { log.warn("CSV-Datei {} war leer/ungültig.", csvPath.toAbsolutePath()); tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig); }
                log.info("{} Rechnungstypen erfolgreich aus {} geladen.", tempConfigs.size(), csvPath.toAbsolutePath());
            } catch (IOException e) { log.error("Fehler beim Lesen der Invoice-Type-Datei {}: {}", csvPath.toAbsolutePath(), e); tempDefaultConfig = createTempDefault(); tempConfigs.add(tempDefaultConfig); }
        }
        // Atomares Update
        synchronized(this.invoiceTypes) { this.invoiceTypes.clear(); this.invoiceTypes.addAll(tempConfigs); this.defaultConfig = tempDefaultConfig;}
    }

    /** Erstellt temporären Default. */
    private InvoiceTypeConfig createTempDefault() {
        return new InvoiceTypeConfig("Others", DEFAULT_IDENTIFYING_KEYWORD, "", "", "", "", InvoiceTypeConfig.USE_MANUAL_CONFIG, InvoiceTypeConfig.USE_MANUAL_FLAVOR, InvoiceTypeConfig.USE_MANUAL_ROW_TOL);
    }

    /**
     * Findet Konfig für PDF basierend auf neuer Keyword-Logik (AND für Include, NOT für Exclude).
     */
    public synchronized InvoiceTypeConfig findConfigForPdf(PDDocument pdfDocument) {
        InvoiceTypeConfig fallbackConfig = (this.defaultConfig != null) ? this.defaultConfig : createTempDefault();
        if (pdfDocument == null) return fallbackConfig;

        String text = "";
        try { // Textextraktion (nur erste Seite)
            if (pdfDocument.getNumberOfPages() > 0) { PDFTextStripper s=new PDFTextStripper(); s.setStartPage(1); s.setEndPage(1); text=s.getText(pdfDocument); }
        } catch (Exception e) { log.error("Fehler Textextraktion: {}", e.getMessage()); }

        if (text.isEmpty()) return fallbackConfig;

        List<InvoiceTypeConfig> currentConfigs;
        synchronized(this.invoiceTypes) { currentConfigs = new ArrayList<>(this.invoiceTypes); }

        for (InvoiceTypeConfig config : currentConfigs) {
            if (DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(config.getIdentifyingKeyword())) continue; // Überspringe Default

            // --- NEUE LOGIK ---
            boolean allIncludesMatch = true;
            // Prüfe Inklusions-Keywords (AND-Logik)
            if (!isPatternFound(config.getKeywordIncl1(), text)) { allIncludesMatch = false; }
            if (allIncludesMatch && !config.getKeywordIncl2().isEmpty() && !isPatternFound(config.getKeywordIncl2(), text)) { allIncludesMatch = false; }
            if (allIncludesMatch && !config.getKeywordIncl3().isEmpty() && !isPatternFound(config.getKeywordIncl3(), text)) { allIncludesMatch = false; }

            // Wenn alle Includes passen, prüfe Excludes (NOT-Logik)
            boolean anyExcludesMatch = false;
            if (allIncludesMatch) {
                if (!config.getKeywordExcl1().isEmpty() && isPatternFound(config.getKeywordExcl1(), text)) { anyExcludesMatch = true; }
                if (!anyExcludesMatch && !config.getKeywordExcl2().isEmpty() && isPatternFound(config.getKeywordExcl2(), text)) { anyExcludesMatch = true; }
            }

            // Wenn alle Includes matchen UND KEIN Exclude matched -> Treffer!
            if (allIncludesMatch && !anyExcludesMatch) {
                log.info("Keyword-Regel für '{}' in PDF gefunden. Verwende Konfig: {}", config.getIdentifyingKeyword(), config.getType());
                return config;
            }
            // --- ENDE NEUE LOGIK ---
        }

        log.info("Kein spezifisches Keyword-Pattern im PDF gefunden, verwende Default '{}'.", fallbackConfig.getIdentifyingKeyword());
        return fallbackConfig;
    }

    /** Hilfsmethode für Regex-Suche. */
    private boolean isPatternFound(String patternString, String text) {
        if (patternString == null || patternString.isBlank() || text == null || text.isEmpty()) return false;
        try {
            Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);
            return matcher.find();
        } catch (PatternSyntaxException e) { log.error("Ungültiges Regex '{}': {}", patternString, e.getMessage()); return false; }
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
    public synchronized List<InvoiceTypeConfig> getInvoiceTypes() { return new ArrayList<>(this.invoiceTypes); }

    /** Gibt Default-Konfig zurück. */
    public synchronized InvoiceTypeConfig getDefaultConfig() { return defaultConfig != null ? defaultConfig : createTempDefault(); }


    /**
     * Aktualisiert die Konfiguration für ein bestimmtes primäres Keyword (Keyword_incl_1) in der CSV-Datei.
     */
    public synchronized boolean updateConfigInCsv(String keywordToUpdate, String newAreaType, String newFlavor, String newRowTol) {
        if (keywordToUpdate == null || keywordToUpdate.isBlank() || DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(keywordToUpdate)) {
            log.error("Aktualisierung für ungültiges oder Default-Keyword '{}' nicht erlaubt.", keywordToUpdate);
            return false;
        }
        if (configDir == null || !Files.exists(csvPath) || !Files.isReadable(csvPath) || !Files.isWritable(csvPath)) {
            log.error("CSV-Datei {} nicht vorhanden/lesbar/schreibbar. Update nicht möglich.", csvPath.toAbsolutePath());
            return false;
        }

        log.info("Aktualisiere CSV für Keyword '{}': Area='{}', Flavor='{}', RowTol='{}'", keywordToUpdate, newAreaType, newFlavor, newRowTol);
        List<String> originalLines;
        List<String> modifiedLines = new ArrayList<>();
        boolean lineUpdated = false;
        boolean isHeader = true; // Um Header zu identifizieren

        try {
            originalLines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);

            for (String line : originalLines) {
                // Behalte Header und leere Zeilen/Kommentare
                if (isHeader || line.trim().isEmpty() || line.trim().startsWith("#")) {
                    modifiedLines.add(line);
                    if (!line.trim().isEmpty() && !line.trim().startsWith("#")) isHeader = false; // Header wurde hinzugefügt
                    continue;
                }

                String[] parts = line.split(";", -1);
                // Prüfe auf korrekte Spaltenzahl UND ob das erste Keyword übereinstimmt
                if (parts.length >= EXPECTED_COLUMNS && keywordToUpdate.equals(parts[COL_KEY_INC1].trim())) {
                    // Gefunden! Aktualisiere die spezifischen Spalten
                    parts[COL_AREA_TYPE] = newAreaType != null ? newAreaType.trim() : ""; // Name der Bereichs-Konfig
                    parts[COL_FLAVOR] = (newFlavor != null && !newFlavor.isBlank()) ? newFlavor.trim() : "lattice"; // Flavor
                    parts[COL_ROW_TOL] = (newRowTol != null && !newRowTol.isBlank()) ? newRowTol.trim() : "2";   // Row Tol

                    modifiedLines.add(String.join(";", parts)); // Füge modifizierte Zeile hinzu
                    lineUpdated = true;
                    log.debug("Zeile für Keyword '{}' aktualisiert: {}", keywordToUpdate, modifiedLines.get(modifiedLines.size()-1));
                } else {
                    // Nicht die gesuchte Zeile, füge sie unverändert hinzu
                    modifiedLines.add(line);
                }
            }

            // Schreibe modifizierte Daten zurück, wenn eine Zeile aktualisiert wurde
            if (lineUpdated) {
                try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (int i = 0; i < modifiedLines.size(); i++) {
                        writer.write(modifiedLines.get(i));
                        if (i < modifiedLines.size() - 1) writer.newLine(); // Füge Zeilenumbruch hinzu, außer für die letzte Zeile
                    }
                }
                log.info("CSV-Datei {} erfolgreich aktualisiert.", csvPath.toAbsolutePath());
                loadConfigsFromCsv(); // Lade interne Liste neu
                return true;
            } else {
                log.warn("Keyword '{}' wurde nicht in der CSV-Datei gefunden. Keine Aktualisierung.", keywordToUpdate);
                return false;
            }

        } catch (IOException e) {
            log.error("Fehler beim Lesen/Schreiben der CSV-Datei {}: {}", csvPath.toAbsolutePath(), e.getMessage(), e);
            return false;
        }
    }
}