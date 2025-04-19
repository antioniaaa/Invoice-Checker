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

/**
 * Service-Klasse zum Verwalten der Rechnungstyp-Konfigurationen aus einer CSV-Datei.
 * Liest die Konfigurationen, identifiziert den passenden Typ für ein PDF
 * und ermöglicht das Bearbeiten der Konfigurationsdatei.
 */
public class InvoiceTypeService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceTypeService.class);
    private final Path configDir; // Verzeichnis: ./configs/invoice-type/
    private final Path csvPath;   // Pfad zur invoice-config.csv
    private final List<InvoiceTypeConfig> invoiceTypes = new ArrayList<>(); // Geladene Konfigurationen
    private InvoiceTypeConfig defaultConfig = null; // Der "Others"-Eintrag als Fallback

    // Konstanten für Dateiname und Unterverzeichnis
    private static final String CSV_FILENAME = "invoice-config.csv";
    private static final String CONFIG_SUBDIR = "invoice-type";
    private static final String DEFAULT_KEYWORD = "Others"; // Spezielles Keyword für den Default

    /**
     * Konstruktor. Legt die Pfade fest, stellt sicher, dass die CSV-Datei existiert
     * (erstellt sie ggf. mit Defaults) und lädt die Konfigurationen.
     */
    public InvoiceTypeService() {
        // Pfad zum Unterverzeichnis im Root-Verzeichnis des Programms
        Path baseDir = Paths.get("").toAbsolutePath(); // Aktuelles Arbeitsverzeichnis
        this.configDir = baseDir.resolve("configs").resolve(CONFIG_SUBDIR); // ./configs/invoice-type/
        this.csvPath = this.configDir.resolve(CSV_FILENAME); // ./configs/invoice-type/invoice-config.csv

        ensureConfigFileExists(); // Stellt sicher, dass Ordner und Default-Datei existieren
        loadConfigsFromCsv(); // Lädt Konfigurationen beim Start
    }

    /**
     * Stellt sicher, dass das Konfig-Verzeichnis und die CSV-Datei existieren.
     * Erstellt die CSV-Datei mit Standardinhalt, falls sie nicht vorhanden ist.
     */
    private void ensureConfigFileExists() {
        try {
            // Erstelle das Verzeichnis (und übergeordnete), falls nicht vorhanden
            Files.createDirectories(configDir);
            // Prüfe, ob die CSV-Datei existiert
            if (!Files.exists(csvPath)) {
                log.info("Konfigurationsdatei {} nicht gefunden, erstelle Standarddatei.", csvPath.toAbsolutePath());
                createDefaultCsv(); // Erstelle Datei mit Defaults
            } else if (!Files.isReadable(csvPath)) {
                 log.error("Keine Leseberechtigung für Konfigurationsdatei: {}", csvPath.toAbsolutePath());
                 // Programm wird versuchen weiterzulaufen, aber ohne geladene Konfigs
            } else {
                 log.info("Verwende Konfigurationsdatei: {}", csvPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Fehler beim Sicherstellen/Erstellen der Konfigurationsdatei {}: {}", csvPath.toAbsolutePath(), e.getMessage());
        } catch (Exception e) {
             log.error("Unerwarteter Fehler beim Initialisieren des InvoiceTypeService-Pfades.", e);
        }
    }

    /**
     * Erstellt eine Standard-CSV-Datei mit Beispieldaten, falls keine existiert.
     */
    private void createDefaultCsv() {
        // Definiere Header und Standardinhalt
        String defaultContent =
                "Keyword;Keyword-Alternate;Type;Area-Type;Flavor;Row Tol\n" +
                "E\\.DIS.*;;Netzbetreiber;Konfig;lattice;2\n" + // Beispiel mit Regex: E.DIS gefolgt von irgendwas
                "Avacon.* AG;;Netzbetreiber;Konfig*;lattice;2\n" + // Beispiel mit Regex
                "WEMAG;;Netzbetreiber;Konfig*;lattice;2\n" +
                "Interconnector;;Direktvermarkter;Konfig*;lattice;2\n" +
                "Next Kraftwerke;;Direktvermarkter;Konfig;lattice;2\n" + // Beispiel mit Leerzeichen
                "Quadra;;Direktvermarkter;Konfig;lattice;2\n" +
                DEFAULT_KEYWORD + ";;Others;Konfig;stream;5\n"; // Default mit Stream und anderer Toleranz

        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8,
                                                            StandardOpenOption.CREATE,      // Erstellen, falls nicht da
                                                            StandardOpenOption.WRITE,       // Zum Schreiben öffnen
                                                            StandardOpenOption.TRUNCATE_EXISTING)) { // Überschreiben, falls schon da
            writer.write(defaultContent);
            log.info("Standard-Konfigurationsdatei {} erfolgreich erstellt.", csvPath.toAbsolutePath());
        } catch (IOException e) {
            log.error("Konnte Standard-Konfigurationsdatei nicht schreiben: {}", csvPath.toAbsolutePath(), e);
        }
    }

    /**
     * Lädt die Konfigurationen aus der CSV-Datei in die `invoiceTypes`-Liste.
     * Setzt den `defaultConfig` basierend auf dem Keyword "Others".
     */
    private void loadConfigsFromCsv() {
        invoiceTypes.clear(); // Alte Liste leeren vor dem Neuladen
        defaultConfig = null; // Default zurücksetzen

        // Prüfe erneut, ob Datei existiert und lesbar ist
        if (!Files.exists(csvPath) || !Files.isReadable(csvPath)) {
            log.error("Kann Konfigurationen nicht laden, da {} nicht existiert oder nicht lesbar ist.", csvPath.toAbsolutePath());
            createDefaultConfigFallback(); // Erstelle internen Notfall-Default
            return;
        }

        log.info("Lade Rechnungstypen aus CSV: {}", csvPath.toAbsolutePath());
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            boolean isHeader = true;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (isHeader || line.trim().isEmpty() || line.trim().startsWith("#")) {
                    // Erste Zeile ist Header, überspringe leere Zeilen und Kommentare (#)
                    isHeader = false;
                    continue;
                }

                // Teile Zeile am Semikolon, erwarte genau 6 Spalten
                String[] parts = line.split(";", -1); // -1, um leere Spalten am Ende zu behalten
                if (parts.length >= 6) {
                    // Erstelle Konfigurationsobjekt, trimme Werte
                    InvoiceTypeConfig config = new InvoiceTypeConfig(
                        parts[0], // Keyword
                        parts[1], // Keyword-Alternate
                        parts[2], // Type
                        parts[3], // Area-Type
                        parts[4], // Flavor
                        parts[5]  // Row Tol
                    );
                    invoiceTypes.add(config); // Füge zur Liste hinzu

                    // Speichere den "Others"-Eintrag als Default-Konfiguration
                    if (DEFAULT_KEYWORD.equalsIgnoreCase(config.getKeyword())) {
                        defaultConfig = config;
                    }
                    log.trace("CSV-Zeile {} geladen: {}", lineNumber, config);
                } else {
                    log.warn("Überspringe ungültige CSV-Zeile {} ({} Spalten statt erwarteten 6): {}", lineNumber, parts.length, line);
                }
            }
            log.info("{} Rechnungstypen erfolgreich aus {} geladen.", invoiceTypes.size(), csvPath.toAbsolutePath());

            // Wenn nach dem Lesen kein "Others" gefunden wurde, erstelle einen Fallback
            if (defaultConfig == null && !invoiceTypes.isEmpty()) {
                 log.warn("Kein '{}' Keyword in CSV gefunden. Erstelle internen Fallback.", DEFAULT_KEYWORD);
                 createDefaultConfigFallback();
            } else if (invoiceTypes.isEmpty()) {
                 log.warn("CSV-Datei {} war leer oder enthielt nur ungültige Zeilen. Erstelle internen Fallback.", csvPath.toAbsolutePath());
                 createDefaultConfigFallback();
            }

        } catch (IOException e) {
            log.error("Fehler beim Lesen der Konfigurationsdatei {}: {}", csvPath.toAbsolutePath(), e);
            createDefaultConfigFallback(); // Erstelle internen Fallback bei Lesefehler
        }
    }

     /**
      * Erstellt eine interne Fallback-Default-Konfiguration, falls das Laden
      * aus der CSV fehlschlägt oder der 'Others'-Eintrag fehlt.
      */
    private void createDefaultConfigFallback() {
         log.warn("Erstelle internen Fallback-Default ('{}', stream, 5).", DEFAULT_KEYWORD);
         // Definiere einen sicheren Standardwert
         this.defaultConfig = new InvoiceTypeConfig(DEFAULT_KEYWORD, "", "Others", "Konfig", "stream", "5");
         // Füge zur Liste hinzu, falls die Liste leer ist, um sicherzustellen, dass immer ein Default existiert
         if (invoiceTypes.isEmpty()) {
             invoiceTypes.add(this.defaultConfig);
         }
    }

    /**
     * Versucht, die passende {@link InvoiceTypeConfig} für ein gegebenes PDF-Dokument zu finden.
     * Extrahiert Text von der ersten Seite des PDFs und sucht nach den definierten Keywords
     * (interpretiert als reguläre Ausdrücke, ignoriert Groß-/Kleinschreibung).
     * Sucht zuerst nach dem primären Keyword, dann nach dem alternativen Keyword.
     *
     * @param pdfDocument Das geladene {@link PDDocument}. Kann null sein.
     * @return Die passende {@link InvoiceTypeConfig} oder die Default-Konfiguration ("Others"),
     *         wenn kein spezifisches Keyword gefunden wird oder ein Fehler auftritt. Gibt nie null zurück.
     */
    public InvoiceTypeConfig findConfigForPdf(PDDocument pdfDocument) {
        // Fallback-Wert sicherstellen
        InvoiceTypeConfig fallbackConfig = (this.defaultConfig != null) ? this.defaultConfig : createTempDefault();

        if (pdfDocument == null) {
            log.warn("findConfigForPdf erhielt null Dokument, gebe Default zurück.");
            return fallbackConfig;
        }

        String text = "";
        try {
            // Extrahiere Text nur von der ersten Seite (effizienter für Keyword-Suche)
            if (pdfDocument.getNumberOfPages() > 0) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1); // 1-basiert für PDFBox
                stripper.setEndPage(1);   // Nur Seite 1
                text = stripper.getText(pdfDocument);
                 // Optional: Wenn Keywords auch auf anderen Seiten stehen könnten:
                 // stripper.setStartPage(1);
                 // stripper.setEndPage(Math.min(3, pdfDocument.getNumberOfPages())); // z.B. die ersten 3 Seiten
                 // text = stripper.getText(pdfDocument);
            } else {
                 log.warn("PDF hat keine Seiten zum Scannen für Keywords.");
            }
        } catch (IOException e) {
            log.error("Fehler beim Extrahieren von Text aus PDF für Keyword-Suche: {}", e.getMessage());
            // Fahre fort und gib Default zurück
        }

        if (text.isEmpty()) {
             log.warn("Kein Text aus PDF extrahiert, gebe Default zurück.");
             return fallbackConfig;
        }

        // --- Suche nach Keywords mittels Regex ---
        // Iteriere durch alle geladenen Konfigurationen (außer "Others")
        for (InvoiceTypeConfig config : invoiceTypes) {
            // Überspringe den Default-Eintrag selbst bei der Suche
            if (DEFAULT_KEYWORD.equalsIgnoreCase(config.getKeyword())) {
                continue;
            }

            // 1. Prüfe primäres Keyword (als Regex)
            if (isPatternFound(config.getKeyword(), text)) {
                log.info("Keyword-Pattern '{}' in PDF gefunden. Verwende Konfig: {}", config.getKeyword(), config.getType());
                return config; // Gib die gefundene Konfiguration zurück
            }

            // 2. Prüfe alternatives Keyword (als Regex), falls vorhanden
            if (isPatternFound(config.getKeywordAlternate(), text)) {
                 log.info("Alternatives Keyword-Pattern '{}' für '{}' in PDF gefunden. Verwende Konfig: {}", config.getKeywordAlternate(), config.getKeyword(), config.getType());
                 return config; // Gib die gefundene Konfiguration zurück
            }
        } // Ende der Schleife über Konfigurationen

        // Kein spezifisches Keyword gefunden, gib Default ("Others") zurück
        log.info("Kein spezifisches Keyword-Pattern im PDF gefunden, verwende Default '{}'.", fallbackConfig.getKeyword());
        return fallbackConfig;
    }

    /**
     * Hilfsmethode, die prüft, ob ein Regex-Pattern im gegebenen Text vorkommt.
     * Ignoriert Groß-/Kleinschreibung und behandelt ungültige Patterns oder leere Eingaben.
     *
     * @param patternString Das zu suchende Regex-Pattern (kann null oder leer sein).
     * @param text Der Text, in dem gesucht werden soll (kann null sein).
     * @return true, wenn das Pattern gefunden wurde, sonst false.
     */
    private boolean isPatternFound(String patternString, String text) {
        // Prüfe auf ungültige oder leere Eingaben
        if (patternString == null || patternString.isBlank() || text == null || text.isEmpty()) {
            return false;
        }
        try {
            // Erstelle das Pattern:
            // - CASE_INSENSITIVE: Ignoriert Groß-/Kleinschreibung
            // - MULTILINE: Lässt '^' und '$' auch Zeilenanfang/-ende matchen (optional)
            // - DOTALL: Lässt den Punkt '.' auch Zeilenumbrüche matchen (kann nützlich sein)
            Pattern pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text);
            // find() sucht nach dem Pattern *irgendwo* im Text
            return matcher.find();
        } catch (PatternSyntaxException e) {
            // Logge Fehler, wenn das Pattern in der CSV ungültig ist
            log.error("Ungültiges Regex-Pattern in CSV-Konfiguration: '{}' - Fehler: {}", patternString, e.getMessage());
            return false; // Ungültiges Pattern kann nicht gefunden werden
        } catch (Exception e) {
             // Fange andere unerwartete Regex-Fehler ab
             log.error("Unerwarteter Fehler bei Regex-Suche nach '{}': {}", patternString, e.getMessage());
             return false;
        }
    }

     /**
      * Erstellt eine temporäre Default-Konfiguration, falls beim Laden
      * der CSV-Datei etwas schiefgeht oder der 'Others'-Eintrag fehlt.
      * @return Eine Standard-InvoiceTypeConfig.
      */
     private InvoiceTypeConfig createTempDefault() {
         // Gibt einen sicheren Standardwert zurück
         return new InvoiceTypeConfig(DEFAULT_KEYWORD, "", "Others", "Konfig", "stream", "5");
     }

    /**
     * Versucht, die CSV-Konfigurationsdatei im Standard-Systemeditor zu öffnen.
     * Zeigt Fehlermeldungen als Dialog an, falls etwas schiefgeht.
     */
    public void openCsvInEditor() {
        if (configDir == null) {
             JOptionPane.showMessageDialog(null, "Konfigurationsverzeichnis nicht initialisiert!", "Fehler", JOptionPane.ERROR_MESSAGE);
             return;
        }
        if (Files.exists(csvPath)) {
            try {
                // Prüfe, ob Desktop-Aktionen unterstützt werden
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(csvPath.toFile()); // Öffne Datei mit Standardanwendung
                    log.info("Versuche, {} im Standardeditor zu öffnen.", csvPath.toAbsolutePath());
                } else {
                    log.warn("Desktop.open wird auf diesem System nicht unterstützt.");
                    JOptionPane.showMessageDialog(null, "Das automatische Öffnen der Datei wird nicht unterstützt.\nBitte öffnen Sie die Datei manuell:\n" + csvPath.toAbsolutePath(), "Nicht unterstützt", JOptionPane.WARNING_MESSAGE);
                }
            } catch (IOException e) {
                log.error("Fehler beim Öffnen von {}: {}", csvPath.toAbsolutePath(), e.getMessage());
                 JOptionPane.showMessageDialog(null, "Fehler beim Öffnen der Datei:\n" + e.getMessage() + "\nPfad: " + csvPath.toAbsolutePath(), "Öffnen fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
            } catch (UnsupportedOperationException e) {
                 log.warn("Desktop.open nicht unterstützt: {}", e.getMessage());
                 JOptionPane.showMessageDialog(null, "Das automatische Öffnen der Datei wird nicht unterstützt.\nBitte öffnen Sie die Datei manuell:\n" + csvPath.toAbsolutePath(), "Nicht unterstützt", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            log.error("Konfigurationsdatei {} existiert nicht und kann nicht geöffnet werden.", csvPath.toAbsolutePath());
            JOptionPane.showMessageDialog(null, "Konfigurationsdatei nicht gefunden:\n" + csvPath.toAbsolutePath() + "\nDie Datei wird beim nächsten Programmstart mit Defaults erstellt.", "Datei nicht gefunden", JOptionPane.ERROR_MESSAGE);
            // Optional: Versuche, die Datei hier direkt zu erstellen?
            // ensureConfigFileExists(); // Könnte hier aufgerufen werden
        }
    }

    /**
     * Lädt die Konfigurationen neu aus der CSV-Datei.
     * Nützlich nach externen Änderungen an der Datei.
     */
    public void reloadConfigs() {
         log.info("Lade Invoice-Type-Konfigurationen neu aus CSV...");
         loadConfigsFromCsv();
         // Optional: Event feuern, damit die GUI sich aktualisiert, falls nötig
         // support.firePropertyChange("invoiceTypesReloaded", null, getInvoiceTypes()); // Eigene Event-Logik nötig
    }

    /**
     * Gibt eine Kopie der Liste der geladenen Rechnungstyp-Konfigurationen zurück.
     * @return Eine Liste von InvoiceTypeConfig-Objekten.
     */
     public List<InvoiceTypeConfig> getInvoiceTypes() {
         // Gib eine Kopie zurück, um externe Änderungen zu verhindern
         return new ArrayList<>(this.invoiceTypes);
     }

     /**
      * Gibt die Default-Konfiguration ("Others") zurück.
      * @return Die Default-InvoiceTypeConfig.
      */
     public InvoiceTypeConfig getDefaultConfig() {
         return defaultConfig != null ? defaultConfig : createTempDefault();
     }
}