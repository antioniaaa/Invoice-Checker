package de.anton.invoice.cecker.invoice_checker.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.SwingUtilities;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

//Java Util für Listen, Maps, Optional etc.
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap; // Import für HashMap
import java.util.List;
import java.util.Map;     // Import für Map
import java.util.Objects; // Import für Objects.equals
import java.util.Optional;


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
 public static final String ACTIVE_CONFIG_PROPERTY = "activeConfig"; // Event für Konfig-Änderung

 // Zustand des Modells
 private final List<PdfDokument> dokumente = Collections.synchronizedList(new ArrayList<>()); // Thread-sichere Liste für Dokumente
 private PdfDokument ausgewaehltesDokument = null; // Das aktuell in der GUI ausgewählte PDF
 private ExtrahierteTabelle ausgewaehlteTabelle = null; // Die aktuell in der GUI ausgewählte Tabelle dieses PDFs
 private ExtractionConfiguration aktiveKonfiguration = null; // NEU: Aktive Konfiguration

 // Service-Klassen für externe Aufgaben
 private final ExtraktionsService extraktionsService; // Für die PDF-Extraktion via Python
 private final ExcelExportService excelExportService; // Für den Excel-Export
 private final ConfigurationService configurationService; // NEU

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
     this.configurationService = new ConfigurationService(); // NEU: Initialisieren
     // Lade Standard-/Default-Konfiguration beim Start? (optional)
     // this.aktiveKonfiguration = configurationService.loadConfiguration("Standard");
 }

 // --- PropertyChange Support Methoden (Standard MVC) ---

 public void addPropertyChangeListener(PropertyChangeListener pcl) {
     support.addPropertyChangeListener(pcl);
 }

 public void removePropertyChangeListener(PropertyChangeListener pcl) {
     support.removePropertyChangeListener(pcl);
 }

 // --- Getter für den Modellzustand ---

 public List<PdfDokument> getDokumente() {
      synchronized (dokumente) {
          return new ArrayList<>(dokumente);
      }
 }

 public PdfDokument getAusgewaehltesDokument() {
     return ausgewaehltesDokument;
 }

 public ExtrahierteTabelle getAusgewaehlteTabelle() {
     return ausgewaehlteTabelle;
 }

 public Optional<List<List<String>>> getAusgewaehlteTabellenDaten() {
     log.debug("AnwendungsModell.getAusgewaehlteTabellenDaten aufgerufen.");
     if (this.ausgewaehlteTabelle != null && this.ausgewaehlteTabelle.getData() != null) {
         log.debug("--> Gebe Daten von Tabelle Index {} zurück.", this.ausgewaehlteTabelle.getIndex());
         return Optional.of(this.ausgewaehlteTabelle.getData());
     }
     log.debug("--> Keine ausgewählte Tabelle oder keine Daten vorhanden.");
     return Optional.empty();
 }

 public List<ExtrahierteTabelle> getVerfuegbareTabellen() {
     if (ausgewaehltesDokument != null && ausgewaehltesDokument.getTables() != null) {
         return new ArrayList<>(ausgewaehltesDokument.getTables());
     }
     return Collections.emptyList();
 }

 public ExtractionConfiguration getAktiveKonfiguration() {
     return aktiveKonfiguration;
 }

 public ConfigurationService getConfigurationService() {
     return configurationService;
 }


 // --- Setter für den Modellzustand (lösen Events aus) ---

 public void setAusgewaehltesDokument(PdfDokument selectedDocument) {
     log.info("Setze ausgew. Dok: {}", (selectedDocument != null ? selectedDocument.getSourcePdf() : "null"));
     PdfDokument oldSelection = this.ausgewaehltesDokument;
     if (!Objects.equals(oldSelection, selectedDocument)) {
         this.ausgewaehltesDokument = selectedDocument;
         log.info("--> PDF geändert. Event '{}'", SELECTED_DOCUMENT_PROPERTY);
         SwingUtilities.invokeLater(() -> support.firePropertyChange(SELECTED_DOCUMENT_PROPERTY, oldSelection, this.ausgewaehltesDokument));

         // Setze Tabellenauswahl auf erste Tabelle des neuen Dokuments oder null
         List<ExtrahierteTabelle> tables = getVerfuegbareTabellen();
         setAusgewaehlteTabelle(!tables.isEmpty() ? tables.get(0) : null);
     } else {
          log.debug("--> PDF nicht geändert.");
     }
 }

 public void setAusgewaehlteTabelle(ExtrahierteTabelle selectedTable) {
      log.info("Setze ausgew. Tabelle: {}", selectedTable);
      ExtrahierteTabelle oldSelection = this.ausgewaehlteTabelle;
      if (!Objects.equals(oldSelection, selectedTable)) {
          this.ausgewaehlteTabelle = selectedTable;
          log.info("--> Tabelle geändert. Event '{}'", SELECTED_TABLE_PROPERTY);
          SwingUtilities.invokeLater(() -> support.firePropertyChange(SELECTED_TABLE_PROPERTY, oldSelection, this.ausgewaehlteTabelle));
      } else {
           log.debug("--> Tabelle nicht geändert.");
      }
 }

 public void setAktiveKonfiguration(ExtractionConfiguration aktiveKonfiguration) {
     log.info("Setze aktive Konfig: {}", (aktiveKonfiguration != null ? aktiveKonfiguration.getName() : "null"));
     ExtractionConfiguration oldConfig = this.aktiveKonfiguration;
     if (!Objects.equals(oldConfig, aktiveKonfiguration)) {
         this.aktiveKonfiguration = aktiveKonfiguration;
         log.info("--> Aktive Konfig geändert. Event '{}'", ACTIVE_CONFIG_PROPERTY);
         SwingUtilities.invokeLater(() -> support.firePropertyChange(ACTIVE_CONFIG_PROPERTY, oldConfig, this.aktiveKonfiguration));
         // Optional: Automatische Neuverarbeitung auslösen
         // triggerReprocessingForCurrentPdf("Konfiguration geändert"); // Eigene Methode dafür nötig
     } else {
         log.debug("--> Aktive Konfig nicht geändert.");
     }
 }

 // --- Kernfunktionalität: Laden und Exportieren ---

 /**
  * Überladene Methode für Kompatibilität und einfache Aufrufe ohne explizite Bereichsangabe.
  * Verwendet die aktuell im Modell gesetzte aktive Konfiguration.
  */
 public void ladeUndVerarbeitePdfs(List<Path> pdfPfade, Map<String, String> parameter, Consumer<PdfDokument> onSingleDocumentProcessedForStatus) {
     ladeUndVerarbeitePdfsMitKonfiguration(pdfPfade, parameter, this.aktiveKonfiguration, onSingleDocumentProcessedForStatus);
 }

 /**
  * Lädt und verarbeitet PDFs asynchron mit einer spezifischen Konfiguration (oder ohne).
  * Diese Methode wird jetzt intern aufgerufen und kann auch vom Controller für Neuverarbeitung genutzt werden.
  *
  * @param pdfPfade Liste der PDF-Pfade.
  * @param parameter Extraktionsparameter (Flavor, RowTol etc.).
  * @param config Die zu verwendende ExtractionConfiguration (kann null sein für keine Bereichseinschränkung).
  * @param onSingleDocumentProcessedForStatus Callback für Status.
  */
 public void ladeUndVerarbeitePdfsMitKonfiguration(List<Path> pdfPfade, Map<String, String> parameter, ExtractionConfiguration config, Consumer<PdfDokument> onSingleDocumentProcessedForStatus) {
     log.info("Starte Ladevorgang für {} PDFs mit Parametern: {} und Konfiguration: {}",
              pdfPfade.size(), parameter, (config != null ? config.getName() : "Keine"));

     for (Path pdfPfad : pdfPfade) {
         final Map<String, String> aktuelleParameter = (parameter != null) ? new HashMap<>(parameter) : Collections.emptyMap();
         final ExtractionConfiguration aktuelleConfig = config; // Finale Referenz für Lambda
         final Path aktuellerPdfPfad = pdfPfad; // Finale Referenz für Lambda

         log.info("Reiche PDF zur Verarbeitung ein: {}", aktuellerPdfPfad);

         executorService.submit(() -> { // Starte Verarbeitung im Thread-Pool
             PdfDokument verarbeitetesDoc = null;
             boolean listUpdated = false; // Flag, ob die Liste geändert wurde
             try {
                 // --- Bereiche und Seite(n) basierend auf Konfiguration ermitteln ---
                 String pageStringToProcess = "all"; // Default: Alle Seiten
                 List<String> areasForPython = null; // Default: Keine Bereiche

                 if (aktuelleConfig != null) {
                     if (!aktuelleConfig.isUsePageSpecificAreas()) { // Globale Bereiche verwenden
                         List<AreaDefinition> globalAreas = aktuelleConfig.getGlobalAreasList();
                         if (globalAreas != null && !globalAreas.isEmpty()) {
                             areasForPython = globalAreas.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList());
                             pageStringToProcess = "all"; // Globale gelten für alle Seiten
                             log.debug("Verwende globale Bereiche für alle Seiten.");
                         }
                     } else { // Seitenspezifische Bereiche verwenden
                          // Vereinfachung: Verarbeite nur Seite 1 mit ihren Bereichen
                          // TODO: Dies erweitern, um alle Seiten mit Bereichen zu verarbeiten
                          areasForPython = aktuelleConfig.getAreasForCamelot(1); // Bereiche für Seite 1 (1-basiert) holen
                          pageStringToProcess = "1"; // Nur Seite 1 verarbeiten
                          if (areasForPython != null && !areasForPython.isEmpty()) {
                              log.debug("Verwende seitenspezifische Bereiche nur für Seite 1.");
                          } else {
                              log.debug("Seitenspezifischer Modus, aber keine Bereiche für Seite 1 definiert. Extrahiere Seite 1 ohne Bereiche.");
                              areasForPython = null; // Keine Bereiche senden
                          }
                     }
                 } else {
                      log.debug("Keine aktive Konfiguration, extrahiere alle Seiten ohne Bereichseinschränkung.");
                 }
                 // --- Ende Bereichsermittlung ---


                 // Rufe Extraktionsservice mit allen 4 Parametern auf
                 verarbeitetesDoc = extraktionsService.extrahiereTabellenAusPdf(
                     aktuellerPdfPfad,     // 1. PDF Pfad
                     aktuelleParameter,    // 2. Parameter (Flavor, RowTol)
                     areasForPython,       // 3. Bereiche (Liste von Strings oder null)
                     pageStringToProcess   // 4. Seite(n) (String "all", "1", etc.)
                 );

                 // --- Liste aktualisieren und Event feuern ---
                  synchronized (dokumente) {
                      final String pfadStr = aktuellerPdfPfad.toString(); // Finale Variable für Lambda
                      // Entferne alten Eintrag, falls vorhanden
                      dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                      // Füge neues/aktualisiertes Dokument hinzu
                      dokumente.add(verarbeitetesDoc);
                      // Halte die Liste sortiert
                      Collections.sort(dokumente);
                      listUpdated = true; // Markiere, dass die Liste geändert wurde
                  }
                  // Rufe den optionalen Callback für Status-Updates auf
                  if (onSingleDocumentProcessedForStatus != null) {
                      onSingleDocumentProcessedForStatus.accept(verarbeitetesDoc);
                  }

             } catch (Exception e) {
                 // --- Fehlerbehandlung bei Extraktion ---
                 log.error("Fehler bei der Verarbeitung von PDF im Hintergrund-Thread: {}", aktuellerPdfPfad, e);
                 // Erstelle ein Fehler-Dokumentobjekt
                 verarbeitetesDoc = new PdfDokument();
                 verarbeitetesDoc.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                 verarbeitetesDoc.setFullPath(aktuellerPdfPfad.toString());
                 verarbeitetesDoc.setError("Fehler während der Verarbeitung: " + e.getMessage());
                 // Füge das Fehler-Dokument zur Liste hinzu (synchronisiert)
                 synchronized (dokumente) {
                     final String pfadStr = aktuellerPdfPfad.toString(); // Finale Variable für Lambda
                     // Entferne alten Eintrag, falls vorhanden
                     dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                     // Füge Fehlerdokument hinzu
                     dokumente.add(verarbeitetesDoc);
                     // Halte die Liste sortiert
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


 // --- Excel Export und Shutdown ---
 public void exportiereAlleNachExcel(Path zielPfad) throws IOException {
     excelExportService.exportiereNachExcel(getDokumente(), zielPfad);
 }

 public void shutdownExecutor() {
      log.info("Fahre Executor Service herunter.");
     executorService.shutdown();
     try {
         if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
             executorService.shutdownNow();
              log.warn("Executor Service wurde zwangsweise heruntergefahren.");
         }
     } catch (InterruptedException e) {
         executorService.shutdownNow();
          log.error("Warten auf Executor Service Beendigung unterbrochen.", e);
         Thread.currentThread().interrupt();
     }
 }
}