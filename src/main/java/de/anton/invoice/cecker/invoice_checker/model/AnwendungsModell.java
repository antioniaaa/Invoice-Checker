package de.anton.invoice.cecker.invoice_checker.model;

// Logging Imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Swing Imports für EDT-Updates
import javax.swing.SwingUtilities;

// Java Beans für PropertyChange Support (MVC)
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

// IO und NIO für Dateipfade und Exceptions
import java.io.IOException;
import java.nio.file.Path;

// Java Util für Listen, Maps, Optional etc.
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap; // Import für HashMap
import java.util.List;
import java.util.Map;     // Import für Map
import java.util.Objects; // Import für Objects.equals
import java.util.Optional;

// Java Concurrency für Hintergrundverarbeitung
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit; // Für shutdown
import java.util.function.Consumer; // Für Callback

/**
 * Das Kernmodell der Anwendung. Verwaltet die Liste der verarbeiteten PDF-Dokumente,
 * die Auswahl des aktuell angezeigten Dokuments und der Tabelle, und delegiert
 * die Extraktion und den Export an entsprechende Service-Klassen.
 * Nutzt PropertyChangeSupport, um die View über Änderungen zu informieren.
 */
public class AnwendungsModell {
    private static final Logger log = LoggerFactory.getLogger(AnwendungsModell.class);

    // Konstanten für Property-Namen (Events für die View)
    public static final String DOCUMENTS_UPDATED_PROPERTY = "documentsUpdated"; // Liste der Dokumente geändert
    public static final String SELECTED_DOCUMENT_PROPERTY = "selectedDocument"; // Ausgewähltes PDF geändert
    public static final String SELECTED_TABLE_PROPERTY = "selectedTable";       // Ausgewählte Tabelle geändert

    // Zustand des Modells
    private final List<PdfDokument> dokumente = Collections.synchronizedList(new ArrayList<>()); // Thread-sichere Liste für Dokumente
    private PdfDokument ausgewaehltesDokument = null; // Das aktuell in der GUI ausgewählte PDF
    private ExtrahierteTabelle ausgewaehlteTabelle = null; // Die aktuell in der GUI ausgewählte Tabelle dieses PDFs

    // Service-Klassen für externe Aufgaben
    private final ExtraktionsService extraktionsService; // Für die PDF-Extraktion via Python
    private final ExcelExportService excelExportService; // Für den Excel-Export

    // MVC Unterstützung
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    // Thread-Pool für asynchrone Extraktion
    private final ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() // Nutze verfügbare Prozessorkerne
    );

    /**
     * Konstruktor: Initialisiert die Service-Klassen.
     */
    public AnwendungsModell() {
        this.extraktionsService = new ExtraktionsService();
        this.excelExportService = new ExcelExportService();
    }

    // --- PropertyChange Support Methoden (Standard MVC) ---

    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }

    // --- Getter für den Modellzustand ---

    /**
     * Gibt eine thread-sichere Kopie der Liste aller verarbeiteten Dokumente zurück.
     * Die Liste ist nach Abrechnungsdatum (falls vorhanden) und Dateiname sortiert.
     * @return Eine Kopie der Liste der PdfDokumente.
     */
    public List<PdfDokument> getDokumente() {
         synchronized (dokumente) { // Stelle sicher, dass während des Kopierens keine Änderung erfolgt
             return new ArrayList<>(dokumente); // Gib eine neue Liste zurück
         }
    }

    /**
     * Gibt das aktuell ausgewählte PdfDokument zurück.
     * @return Das ausgewählte PdfDokument oder null.
     */
    public PdfDokument getAusgewaehltesDokument() {
        return ausgewaehltesDokument;
    }

    /**
     * Gibt die aktuell ausgewählte ExtrahierteTabelle zurück.
     * @return Die ausgewählte ExtrahierteTabelle oder null.
     */
    public ExtrahierteTabelle getAusgewaehlteTabelle() {
        return ausgewaehlteTabelle;
    }

    /**
     * Gibt die Daten (Liste von Listen von Strings) der aktuell ausgewählten Tabelle zurück.
     * @return Ein Optional, das die Tabellendaten enthält, oder ein leeres Optional,
     *         wenn keine Tabelle ausgewählt ist oder die Tabelle keine Daten hat.
     */
    public Optional<List<List<String>>> getAusgewaehlteTabellenDaten() {
        log.debug("AnwendungsModell.getAusgewaehlteTabellenDaten aufgerufen.");
        if (this.ausgewaehlteTabelle != null && this.ausgewaehlteTabelle.getData() != null) {
            log.debug("--> Gebe Daten von Tabelle Index {} zurück.", this.ausgewaehlteTabelle.getIndex());
            return Optional.of(this.ausgewaehlteTabelle.getData());
        }
        log.debug("--> Keine ausgewählte Tabelle oder keine Daten vorhanden.");
        return Optional.empty();
    }

    /**
     * Gibt die Liste der verfügbaren (extrahierten) Tabellen für das
     * aktuell ausgewählte PdfDokument zurück.
     * @return Eine Kopie der Liste der ExtrahierteTabelle-Objekte oder eine leere Liste.
     */
    public List<ExtrahierteTabelle> getVerfuegbareTabellen() {
        if (ausgewaehltesDokument != null && ausgewaehltesDokument.getTables() != null) {
            // Gib eine Kopie zurück, um externe Modifikationen zu verhindern
            return new ArrayList<>(ausgewaehltesDokument.getTables());
        }
        return Collections.emptyList(); // Leere Liste, wenn nichts ausgewählt oder keine Tabellen
    }

    // --- Setter für den Modellzustand (lösen Events aus) ---

    /**
     * Setzt das aktuell ausgewählte PdfDokument. Wenn sich die Auswahl ändert,
     * wird ein PropertyChangeEvent ("selectedDocument") gefeuert und die
     * Auswahl der Tabelle wird auf die erste Tabelle des neuen Dokuments gesetzt (oder null).
     * @param selectedDocument Das neu auszuwählende PdfDokument oder null.
     */
    public void setAusgewaehltesDokument(PdfDokument selectedDocument) {
        log.info("AnwendungsModell.setAusgewaehltesDokument wird aufgerufen für: {}",
                 (selectedDocument != null ? selectedDocument.getSourcePdf() : "null"));
        PdfDokument oldSelection = this.ausgewaehltesDokument;
        // Nur fortfahren und Event feuern, wenn sich die Auswahl tatsächlich ändert
        if (!Objects.equals(oldSelection, selectedDocument)) {
            this.ausgewaehltesDokument = selectedDocument;
            log.info("--> PDF-Auswahl hat sich geändert. Feuere PropertyChangeEvent '{}'.", SELECTED_DOCUMENT_PROPERTY);
            // Feuere Event im EDT für die GUI
             SwingUtilities.invokeLater(() -> {
                support.firePropertyChange(SELECTED_DOCUMENT_PROPERTY, oldSelection, this.ausgewaehltesDokument);
             });

            // Setze auch die Tabellenauswahl zurück oder auf die erste Tabelle des neuen Dokuments
            List<ExtrahierteTabelle> verfuegbareTabellen = getVerfuegbareTabellen();
            if (!verfuegbareTabellen.isEmpty()) {
                // Wähle die erste verfügbare Tabelle aus
                setAusgewaehlteTabelle(verfuegbareTabellen.get(0));
            } else {
                // Kein Dokument oder keine Tabellen -> keine Tabelle auswählen
                setAusgewaehlteTabelle(null);
            }
        } else {
             log.debug("--> PDF-Auswahl hat sich NICHT geändert.");
        }
    }

    /**
     * Setzt die aktuell ausgewählte ExtrahierteTabelle. Wenn sich die Auswahl ändert,
     * wird ein PropertyChangeEvent ("selectedTable") gefeuert.
     * @param selectedTable Die neu auszuwählende ExtrahierteTabelle oder null.
     */
    public void setAusgewaehlteTabelle(ExtrahierteTabelle selectedTable) {
         log.info("AnwendungsModell.setAusgewaehlteTabelle wird aufgerufen für: {}", selectedTable);
         ExtrahierteTabelle oldSelection = this.ausgewaehlteTabelle;
         // Nur fortfahren und Event feuern, wenn sich die Auswahl tatsächlich ändert
         if (!Objects.equals(oldSelection, selectedTable)) {
             this.ausgewaehlteTabelle = selectedTable;
             log.info("--> Tabellen-Auswahl hat sich geändert. Feuere PropertyChangeEvent '{}'.", SELECTED_TABLE_PROPERTY);
             // Feuere Event im EDT für die GUI
             SwingUtilities.invokeLater(() -> {
                support.firePropertyChange(SELECTED_TABLE_PROPERTY, oldSelection, this.ausgewaehlteTabelle);
             });
         } else {
              log.debug("--> Tabellen-Auswahl hat sich NICHT geändert.");
         }
    }

    // --- Kernfunktionalität: Laden und Exportieren ---

    /**
     * Lädt und verarbeitet eine Liste von PDF-Dateien asynchron im Hintergrund.
     * Für jedes PDF wird der Extraktionsservice mit den übergebenen Parametern aufgerufen.
     * Die interne Dokumentenliste wird aktualisiert (alte Einträge für denselben Pfad werden ersetzt).
     * Nach jeder Änderung der Dokumentenliste wird ein PropertyChangeEvent ("documentsUpdated") gefeuert.
     *
     * @param pdfPfade Liste der zu verarbeitenden PDF-Pfade.
     * @param parameter Map mit Extraktionsparametern (z.B. "flavor", "row_tol") für Camelot.
     * @param onSingleDocumentProcessedForStatus Optionaler Callback, der nach der Verarbeitung *jedes einzelnen*
     *                                           Dokuments aufgerufen wird (nützlich für Status-Updates in der GUI).
     */
    public void ladeUndVerarbeitePdfs(List<Path> pdfPfade, Map<String, String> parameter, Consumer<PdfDokument> onSingleDocumentProcessedForStatus) {
        log.info("Starte Ladevorgang für {} PDFs mit Parametern: {}", pdfPfade.size(), parameter);
        for (Path pdfPfad : pdfPfade) {
            // Erstelle eine finale Kopie der Parameter für den Lambda-Ausdruck
            final Map<String, String> aktuelleParameter = (parameter != null) ? new HashMap<>(parameter) : Collections.emptyMap();
            final Path aktuellerPdfPfad = pdfPfad; // Finale Referenz für Lambda

            // *** KEINE Prüfung auf 'schonVorhanden' mehr, um Neuverarbeitung mit anderen Parametern zu ermöglichen ***
            log.info("Reiche PDF zur Verarbeitung ein: {} mit Parametern: {}", aktuellerPdfPfad, aktuelleParameter);

            executorService.submit(() -> { // Starte Verarbeitung im Thread-Pool
                PdfDokument verarbeitetesDoc = null;
                boolean listUpdated = false; // Flag, ob die Liste geändert wurde
                try {
                    // Rufe den Extraktionsservice mit den spezifischen Parametern auf
                    verarbeitetesDoc = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, aktuelleParameter);

                    // Synchronisiere den Zugriff auf die gemeinsame Dokumentenliste
                    synchronized (dokumente) {
                        // Entferne IMMER den alten Eintrag (falls vorhanden), um ihn durch den neuen zu ersetzen
                        final String pfadStr = aktuellerPdfPfad.toString(); // Finale Variable für Lambda
                        dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                        dokumente.add(verarbeitetesDoc); // Füge neues/aktualisiertes Dokument hinzu
                        Collections.sort(dokumente); // Halte die Liste sortiert
                        listUpdated = true; // Markiere, dass die Liste geändert wurde
                    }
                    // Rufe den optionalen Callback für Status-Updates auf
                    if (onSingleDocumentProcessedForStatus != null) {
                         onSingleDocumentProcessedForStatus.accept(verarbeitetesDoc);
                    }

                } catch (Exception e) {
                    // Fehlerbehandlung bei Extraktion
                    log.error("Fehler bei der Verarbeitung von PDF im Hintergrund-Thread: {}", aktuellerPdfPfad, e);
                    // Erstelle ein Fehler-Dokumentobjekt
                    verarbeitetesDoc = new PdfDokument();
                    verarbeitetesDoc.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                    verarbeitetesDoc.setFullPath(aktuellerPdfPfad.toString());
                    verarbeitetesDoc.setError("Fehler während der Verarbeitung: " + e.getMessage());
                    // Füge das Fehler-Dokument zur Liste hinzu (synchronisiert)
                    synchronized (dokumente) {
                        final String pfadStr = aktuellerPdfPfad.toString(); // Finale Variable für Lambda
                        dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                        dokumente.add(verarbeitetesDoc);
                        Collections.sort(dokumente);
                        listUpdated = true; // Liste wurde geändert (Fehlereintrag hinzugefügt)
                    }
                     // Rufe den optionalen Callback für Status-Updates auf
                    if (onSingleDocumentProcessedForStatus != null) {
                         onSingleDocumentProcessedForStatus.accept(verarbeitetesDoc);
                    }
                } finally {
                     // Feuere das Update-Event für die View, wenn die Liste geändert wurde
                     if (listUpdated) {
                          log.debug("Feuere PropertyChangeEvent '{}'", DOCUMENTS_UPDATED_PROPERTY);
                          // Feuere das Event im Event Dispatch Thread (EDT)
                          SwingUtilities.invokeLater(() -> {
                                // Sende eine (neue) Kopie der aktuellen Liste als neuen Wert
                                support.firePropertyChange(DOCUMENTS_UPDATED_PROPERTY, null, getDokumente());
                          });
                     }
                }
            }); // Ende des Runnables für den ExecutorService
        } // Ende der for-Schleife über pdfPfade
    }

     /**
      * Exportiert die Daten aller aktuell geladenen Dokumente und ihrer Tabellen
      * in eine Excel-Datei unter dem angegebenen Pfad.
      *
      * @param zielPfad Der Pfad zur zu erstellenden Excel-Datei.
      * @throws IOException Wenn ein Fehler beim Schreiben der Datei auftritt.
      */
    public void exportiereAlleNachExcel(Path zielPfad) throws IOException {
        // Delegiere den Export an den ExcelExportService
        excelExportService.exportiereNachExcel(getDokumente(), zielPfad);
    }


    /**
     * Fährt den internen ExecutorService herunter. Sollte beim Beenden der Anwendung aufgerufen werden.
     */
    public void shutdownExecutor() {
         log.info("Fahre Executor Service herunter.");
        executorService.shutdown(); // Initiiert das Herunterfahren, akzeptiert keine neuen Tasks
        try {
            // Warte eine kurze Zeit auf die Beendigung laufender Tasks
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                // Wenn Tasks nicht innerhalb des Timeouts beendet werden, erzwinge das Herunterfahren
                executorService.shutdownNow(); // Sendet Interrupts an laufende Threads
                 log.warn("Executor Service wurde zwangsweise heruntergefahren, da Tasks nicht rechtzeitig beendet wurden.");
            }
        } catch (InterruptedException e) {
            // Falls das Warten unterbrochen wird, erzwinge ebenfalls das Herunterfahren
            executorService.shutdownNow();
             log.error("Warten auf Executor Service Beendigung unterbrochen.", e);
            // Setze den Interrupt-Status des aktuellen Threads wieder
            Thread.currentThread().interrupt();
        }
    }
}