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


import java.util.Set; // Import für Set (für Seitenzahlen)



import java.util.concurrent.atomic.AtomicInteger; // Für Fortschrittszähler
//PDFBox für Keyword-Suche
import org.apache.pdfbox.pdmodel.PDDocument;


/**
* Das Kernmodell der Anwendung. Verwaltet die Liste der verarbeiteten PDF-Dokumente,
* die Auswahl des aktuell angezeigten Dokuments und der Tabelle, die Extraktionsparameter
* und Konfigurationen. Orchestriert die Extraktion und den Export.
* Nutzt PropertyChangeSupport, um die View über Änderungen zu informieren.
*/
public class AnwendungsModell {
 private static final Logger log = LoggerFactory.getLogger(AnwendungsModell.class);

 // Konstanten für Property-Namen (Events für die View)
 public static final String DOCUMENTS_UPDATED_PROPERTY = "documentsUpdated"; // Liste der Dokumente geändert
 public static final String SELECTED_DOCUMENT_PROPERTY = "selectedDocument"; // Ausgewähltes PDF geändert
 public static final String SELECTED_TABLE_PROPERTY = "selectedTable";       // Ausgewählte Tabelle geändert
 public static final String ACTIVE_CONFIG_PROPERTY = "activeConfig"; // Aktive Bereichs-Konfig geändert
 public static final String PROGRESS_UPDATE_PROPERTY = "progressUpdate"; // Fortschritt der Verarbeitung

 // Zustand des Modells
 private final List<PdfDokument> dokumente = Collections.synchronizedList(new ArrayList<>()); // Thread-sichere Liste
 private PdfDokument ausgewaehltesDokument = null;
 private ExtrahierteTabelle ausgewaehlteTabelle = null;
 private ExtractionConfiguration aktiveKonfiguration = null; // Aktive Bereichs-Konfiguration

 // Service-Klassen
 private final ExtraktionsService extraktionsService;
 private final ExcelExportService excelExportService;
 private final ConfigurationService configurationService; // Für Bereichs-Konfigs (.json)
 private final InvoiceTypeService invoiceTypeService;       // Für Rechnungs-Typ-Konfigs (.csv)

 // MVC Unterstützung
 private final PropertyChangeSupport support = new PropertyChangeSupport(this);

 // Thread-Pool für asynchrone Extraktion
 private final ExecutorService executorService = Executors.newFixedThreadPool(
         Runtime.getRuntime().availableProcessors()
 );

 /**
  * Konstruktor: Initialisiert die Service-Klassen.
  */
 public AnwendungsModell() {
     this.extraktionsService = new ExtraktionsService();
     this.excelExportService = new ExcelExportService();
     this.configurationService = new ConfigurationService();
     this.invoiceTypeService = new InvoiceTypeService();
     // Optional: Standard Bereichs-Konfiguration laden
     // this.aktiveKonfiguration = configurationService.loadConfiguration("Standard");
 }

 // --- PropertyChange Support Methoden ---
 public void addPropertyChangeListener(PropertyChangeListener pcl) { support.addPropertyChangeListener(pcl); }
 public void removePropertyChangeListener(PropertyChangeListener pcl) { support.removePropertyChangeListener(pcl); }

 // --- Getter für den Modellzustand ---
 public List<PdfDokument> getDokumente() { synchronized (dokumente) { return new ArrayList<>(dokumente); } }
 public PdfDokument getAusgewaehltesDokument() { return ausgewaehltesDokument; }
 public ExtrahierteTabelle getAusgewaehlteTabelle() { return ausgewaehlteTabelle; }
 public Optional<List<List<String>>> getAusgewaehlteTabellenDaten() { if(this.ausgewaehlteTabelle!=null&&this.ausgewaehlteTabelle.getData()!=null) return Optional.of(this.ausgewaehlteTabelle.getData()); return Optional.empty();}
 public List<ExtrahierteTabelle> getVerfuegbareTabellen() { if(ausgewaehltesDokument!=null&&ausgewaehltesDokument.getTables()!=null) return new ArrayList<>(ausgewaehltesDokument.getTables()); return Collections.emptyList();}
 public ExtractionConfiguration getAktiveKonfiguration() { return aktiveKonfiguration; }
 // Getter für Services (damit Controller zugreifen kann)
 public ConfigurationService getConfigurationService() { return configurationService; }
 public InvoiceTypeService getInvoiceTypeService() { return invoiceTypeService; }

 // --- Setter für den Modellzustand (lösen Events aus) ---
 public void setAusgewaehltesDokument(PdfDokument selectedDocument) {
     log.info("Setze ausgew. Dok: {}", (selectedDocument != null ? selectedDocument.getSourcePdf() : "null"));
     PdfDokument oldSelection = this.ausgewaehltesDokument;
     if (!Objects.equals(oldSelection, selectedDocument)) {
         this.ausgewaehltesDokument = selectedDocument;
         log.info("--> PDF geändert. Event '{}'", SELECTED_DOCUMENT_PROPERTY);
         SwingUtilities.invokeLater(() -> support.firePropertyChange(SELECTED_DOCUMENT_PROPERTY, oldSelection, this.ausgewaehltesDokument));
         List<ExtrahierteTabelle> tables = getVerfuegbareTabellen();
         setAusgewaehlteTabelle(!tables.isEmpty() ? tables.get(0) : null);
     } else { log.debug("--> PDF nicht geändert."); }
 }

 public void setAusgewaehlteTabelle(ExtrahierteTabelle selectedTable) {
      log.info("Setze ausgew. Tabelle: {}", selectedTable);
      ExtrahierteTabelle oldSelection = this.ausgewaehlteTabelle;
      if (!Objects.equals(oldSelection, selectedTable)) {
          this.ausgewaehlteTabelle = selectedTable;
          log.info("--> Tabelle geändert. Event '{}'", SELECTED_TABLE_PROPERTY);
          SwingUtilities.invokeLater(() -> support.firePropertyChange(SELECTED_TABLE_PROPERTY, oldSelection, this.ausgewaehlteTabelle));
      } else { log.debug("--> Tabelle nicht geändert."); }
 }

 public void setAktiveKonfiguration(ExtractionConfiguration aktiveKonfiguration) {
     log.info("Setze aktive Bereichs-Konfig: {}", (aktiveKonfiguration != null ? aktiveKonfiguration.getName() : "null"));
     ExtractionConfiguration oldConfig = this.aktiveKonfiguration;
     if (!Objects.equals(oldConfig, aktiveKonfiguration)) {
         this.aktiveKonfiguration = aktiveKonfiguration;
         log.info("--> Aktive Bereichs-Konfig geändert. Event '{}'", ACTIVE_CONFIG_PROPERTY);
         SwingUtilities.invokeLater(() -> support.firePropertyChange(ACTIVE_CONFIG_PROPERTY, oldConfig, this.aktiveKonfiguration));
         // Optional: Neuverarbeitung auslösen? Wird aktuell vom Controller gemacht.
     } else { log.debug("--> Aktive Bereichs-Konfig nicht geändert."); }
 }

 // --- Kernfunktionalität: Laden und Exportieren ---

 /**
  * Lädt und verarbeitet PDFs asynchron. Ermittelt Parameter aus GUI und InvoiceType-Konfig.
  * Verwendet die aktive Bereichs-Konfiguration. Meldet Fortschritt.
  * Dies ist die Hauptmethode, die vom Controller aufgerufen wird.
  *
  * @param pdfPfade Liste der PDF-Pfade.
  * @param parameterGui Parameter aus der GUI (Flavor, RowTol).
  * @param onSingleDocumentProcessedForStatus Callback für Status-Updates.
  * @param progressCallback Callback für Fortschritts-Updates (Wert 0.0 bis 1.0).
  */
 public void ladeUndVerarbeitePdfs(List<Path> pdfPfade, Map<String, String> parameterGui, Consumer<PdfDokument> onSingleDocumentProcessedForStatus, Consumer<Double> progressCallback) {
     // Nutze die aktuell im Modell gesetzte aktive Bereichs-Konfiguration
     ladeUndVerarbeitePdfsMitKonfiguration(pdfPfade, parameterGui, this.aktiveKonfiguration, onSingleDocumentProcessedForStatus, progressCallback);
 }

 /**
  * Interne Methode: Lädt und verarbeitet PDFs asynchron mit einer spezifischen Bereichs-Konfiguration.
  * Orchestriert die Extraktion basierend auf globalen oder seitenspezifischen Bereichen.
  *
  * @param pdfPfade Liste der PDF-Pfade.
  * @param parameterGui Extraktionsparameter aus der GUI (Flavor, RowTol etc.).
  * @param config Die zu verwendende ExtractionConfiguration (Bereichs-Konfig, kann null sein).
  * @param onSingleDocumentProcessedForStatus Callback für Status.
  * @param progressCallback Callback für Fortschritt.
  */
 public void ladeUndVerarbeitePdfsMitKonfiguration(List<Path> pdfPfade, Map<String, String> parameterGui, ExtractionConfiguration config, Consumer<PdfDokument> onSingleDocumentProcessedForStatus, Consumer<Double> progressCallback) {
     final ExtractionConfiguration aktuelleBereichsKonfig = config; // Aktive Bereichs-Konfig für diesen Lauf
     log.info("Starte Ladevorgang für {} PDFs mit GUI-Parametern: {} und Bereichs-Konfig: {}",
              pdfPfade.size(), parameterGui, (aktuelleBereichsKonfig != null ? aktuelleBereichsKonfig.getName() : "Keine"));

     final int totalPdfs = pdfPfade.size();
     final AtomicInteger processedCount = new AtomicInteger(0);

     // Initialer Fortschritt 0%
     if (progressCallback != null) SwingUtilities.invokeLater(() -> progressCallback.accept(0.0));

     for (Path pdfPfad : pdfPfade) {
         // Kopiere GUI-Parameter für den Thread
         final Map<String, String> aktuelleGuiParameter = (parameterGui != null) ? new HashMap<>(parameterGui) : Collections.emptyMap();
         final Path aktuellerPdfPfad = pdfPfad;

         log.info("Reiche PDF zur Verarbeitung ein: {}", aktuellerPdfPfad);

         executorService.submit(() -> { // Starte Verarbeitung im Thread-Pool
             PdfDokument finalesErgebnisDokument = null;
             boolean listUpdated = false;
             StringBuilder gesammelteFehler = new StringBuilder();
             PDDocument pdDocForKeyword = null; // Für Keyword-Suche

             try {
                 // 1. Basis-Dokumentobjekt erstellen
                 finalesErgebnisDokument = new PdfDokument();
                 finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                 finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());

                 // 2. Rechnungstyp anhand Keywords ermitteln
                 InvoiceTypeConfig typConfig = null;
                 try {
                      pdDocForKeyword = PDDocument.load(aktuellerPdfPfad.toFile());
                      typConfig = invoiceTypeService.findConfigForPdf(pdDocForKeyword);
                 } catch (IOException e) {
                      log.error("Fehler beim Laden von {} für Keyword-Suche: {}", aktuellerPdfPfad, e.getMessage());
                      gesammelteFehler.append("Fehler Öffnen f. Keyword-Suche; ");
                      typConfig = invoiceTypeService.getDefaultConfig(); // Nutze Default bei Fehler
                 } finally {
                      if (pdDocForKeyword != null) try { pdDocForKeyword.close(); } catch (IOException e) { /* ignored */ }
                 }
                 log.info("--> Ermittelter Rechnungstyp für {}: {}", aktuellerPdfPfad.getFileName(), (typConfig != null ? typConfig.getType() : "Unbekannt/Default"));

                 // 3. Finale Extraktionsparameter bestimmen (GUI überschreibt CSV-Default)
                 Map<String, String> finaleParameter = new HashMap<>();
                 InvoiceTypeConfig effectiveTypConfig = (typConfig != null) ? typConfig : invoiceTypeService.getDefaultConfig(); // Sicherstellen, dass wir einen Typ haben
                 finaleParameter.put("flavor", aktuelleGuiParameter.getOrDefault("flavor", effectiveTypConfig.getDefaultFlavor()));
                 finaleParameter.put("row_tol", aktuelleGuiParameter.getOrDefault("row_tol", effectiveTypConfig.getDefaultRowTol()));
                 log.debug("--> Finale Extraktionsparameter: {}", finaleParameter);


                 // 4. Bereiche und Seite(n) basierend auf Bereichs-Konfig ermitteln & Extraktion(en) starten
                 String pageStringToProcess = "all";
                 List<String> areasForPython = null;
                 List<PdfDokument> teilErgebnisse = new ArrayList<>(); // Für seitenspezifische Ergebnisse

                 if (aktuelleBereichsKonfig == null) {
                     // Fall A: Keine Bereichs-Konfig -> 1 Aufruf für alles
                     log.debug("--> Keine Bereichs-Konfiguration aktiv.");
                     PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, null, "all");
                     if (ergebnis != null) teilErgebnisse.add(ergebnis);

                 } else if (!aktuelleBereichsKonfig.isUsePageSpecificAreas()) {
                     // Fall B: Globale Bereichs-Konfig -> 1 Aufruf für alles mit Bereichen
                     List<AreaDefinition> globalAreas = aktuelleBereichsKonfig.getGlobalAreasList();
                     if (globalAreas != null && !globalAreas.isEmpty()) {
                         areasForPython = globalAreas.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList());
                         pageStringToProcess = "all";
                         log.info("--> Verwende {} globale Bereiche für alle Seiten.", areasForPython.size());
                     } else { log.debug("--> Globaler Modus, aber keine Bereiche definiert."); }
                     PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, areasForPython, pageStringToProcess);
                     if (ergebnis != null) teilErgebnisse.add(ergebnis);

                 } else {
                     // Fall C: Seitenspezifische Bereichs-Konfig -> Mehrere Aufrufe
                     log.info("--> Seitenspezifischer Modus aktiv.");
                     Map<Integer, List<AreaDefinition>> seitenBereicheMap = aktuelleBereichsKonfig.getPageSpecificAreasMap();
                     if (seitenBereicheMap == null || seitenBereicheMap.isEmpty()) {
                          log.warn("--> Seitenspezifischer Modus, aber KEINE Bereiche definiert. Extrahiere alle Seiten ohne Bereiche.");
                          PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, null, "all");
                          if (ergebnis != null) teilErgebnisse.add(ergebnis);
                     } else {
                         // Iteriere über definierte Seiten und rufe Service einzeln auf
                         Set<Integer> seiten = seitenBereicheMap.keySet();
                         log.info("--> Verarbeite spezifische Seiten: {}", seiten);
                         for (Integer pageNum : seiten) {
                             List<AreaDefinition> areasForPage = seitenBereicheMap.get(pageNum);
                             if (areasForPage == null || areasForPage.isEmpty()) continue;
                             List<String> areasStr = areasForPage.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList());
                             log.info("----> Verarbeite Seite {} mit {} Bereichen...", pageNum, areasStr.size());
                             PdfDokument seitenErgebnis = extraktionsService.extrahiereTabellenAusPdf(
                                 aktuellerPdfPfad, finaleParameter, areasStr, String.valueOf(pageNum));
                             if (seitenErgebnis != null) teilErgebnisse.add(seitenErgebnis);
                             else gesammelteFehler.append("Seite ").append(pageNum).append(": Extraktion fehlgeschlagen; "); // Fehler sammeln
                         }
                     }
                 } // Ende Fallunterscheidung Konfiguration

                 // 5. Ergebnisse zusammenführen (wenn mehrere Teilergebnisse)
                 log.info("Führe Ergebnisse von {} Aufruf(en) zusammen.", teilErgebnisse.size());
                 boolean firstResult = true;
                 for (PdfDokument teilErgebnis : teilErgebnisse) {
                      if (teilErgebnis.getTables() != null) {
                           finalesErgebnisDokument.addTables(teilErgebnis.getTables());
                      }
                      // Nimm Datum vom ersten gültigen Teilergebnis
                      if (firstResult && teilErgebnis.getAbrechnungszeitraumStartStr() != null) {
                           finalesErgebnisDokument.setAbrechnungszeitraumStartStr(teilErgebnis.getAbrechnungszeitraumStartStr());
                           finalesErgebnisDokument.setAbrechnungszeitraumEndeStr(teilErgebnis.getAbrechnungszeitraumEndeStr());
                           firstResult = false;
                      }
                      // Sammle Fehler aus Teilergebnissen
                      if (teilErgebnis.getError() != null) {
                           gesammelteFehler.append(teilErgebnis.getError()).append("; ");
                      }
                 }
                 log.info("Zusammengeführtes Ergebnis enthält {} Tabellen.", finalesErgebnisDokument.getTables().size());

                 // Setze gesammelte Fehler im finalen Dokument
                 if (gesammelteFehler.length() > 0) {
                     finalesErgebnisDokument.setError(gesammelteFehler.toString().trim());
                 }

                 // --- Liste aktualisieren ---
                  synchronized (dokumente) {
                      final String pfadStr = aktuellerPdfPfad.toString();
                      dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                      dokumente.add(finalesErgebnisDokument);
                      Collections.sort(dokumente);
                      listUpdated = true;
                  }
                  // Callback für Status
                  if (onSingleDocumentProcessedForStatus != null) {
                      onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument);
                  }

             } catch (Exception e) {
                 // Allgemeine Fehlerbehandlung
                 log.error("Unerwarteter Fehler bei Verarbeitung von PDF {}: {}", aktuellerPdfPfad, e.getMessage(), e);
                 if (finalesErgebnisDokument == null) { /* ... Erstelle Fehlerobjekt ... */ finalesErgebnisDokument = new PdfDokument(); finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString()); finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());}
                 finalesErgebnisDokument.setError("Interner Fehler: " + e.getMessage());
                 synchronized(dokumente){ final String ps = aktuellerPdfPfad.toString(); dokumente.removeIf(d->d.getFullPath()!=null && d.getFullPath().equals(ps)); dokumente.add(finalesErgebnisDokument); Collections.sort(dokumente); listUpdated = true;}
                 if(onSingleDocumentProcessedForStatus!=null){onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument);}
             } finally {
                  // --- Fortschritt und Event ---
                  int processed = processedCount.incrementAndGet();
                  double progress = (double) processed / totalPdfs;
                  if (progressCallback != null) SwingUtilities.invokeLater(() -> progressCallback.accept(progress));
                  if (listUpdated) { SwingUtilities.invokeLater(() -> support.firePropertyChange(DOCUMENTS_UPDATED_PROPERTY, null, getDokumente())); }
             }
         }); // Ende Runnable
     } // Ende for Schleife
 }


 // --- Excel Export und Shutdown (unverändert) ---
 public void exportiereAlleNachExcel(Path zielPfad) throws IOException { excelExportService.exportiereNachExcel(getDokumente(), zielPfad); }
 public void shutdownExecutor() { log.info("Shutdown executor."); executorService.shutdown(); try { if (!executorService.awaitTermination(5,TimeUnit.SECONDS)) executorService.shutdownNow(); } catch (InterruptedException e) { executorService.shutdownNow(); Thread.currentThread().interrupt(); } }

}