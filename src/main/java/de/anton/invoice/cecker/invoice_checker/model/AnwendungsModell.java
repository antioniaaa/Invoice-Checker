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


//IO und NIO für Dateipfade und Exceptions
import java.io.File; // Für PDDocument.load(File)
import java.nio.file.Paths;

import java.util.Comparator;


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
 public static final String PROGRESS_UPDATE_PROPERTY = "progressUpdate"; // Fortschritt der Verarbeitung (aktuell nicht als Event genutzt)
 public static final String SINGLE_DOCUMENT_REPROCESSED_PROPERTY = "singleDocumentReprocessed"; // Einzelnes Dokument neu verarbeitet

 // Zustand des Modells
 private final List<PdfDokument> dokumente = Collections.synchronizedList(new ArrayList<>()); // Thread-sichere Liste
 private PdfDokument ausgewaehltesDokument = null; // Das aktuell in der GUI ausgewählte PDF
 private ExtrahierteTabelle ausgewaehlteTabelle = null; // Die aktuell in der GUI ausgewählte Tabelle
 private ExtractionConfiguration aktiveKonfiguration = null; // Aktive Bereichs-Konfiguration

 // Service-Klassen
 private final ExtraktionsService extraktionsService;
 private final ExcelExportService excelExportService;
 private final ConfigurationService configurationService;
 private final InvoiceTypeService invoiceTypeService;

 // MVC Unterstützung
 private final PropertyChangeSupport support = new PropertyChangeSupport(this);

 // Thread-Pool
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
 }

 // --- PropertyChange Support Methoden ---
 public void addPropertyChangeListener(PropertyChangeListener pcl) { support.addPropertyChangeListener(pcl); }
 public void removePropertyChangeListener(PropertyChangeListener pcl) { support.removePropertyChangeListener(pcl); }

 // --- Getter ---
 public List<PdfDokument> getDokumente() { synchronized (dokumente) { return new ArrayList<>(dokumente); } }
 public PdfDokument getAusgewaehltesDokument() { return ausgewaehltesDokument; }
 public ExtrahierteTabelle getAusgewaehlteTabelle() { return ausgewaehlteTabelle; }
 public Optional<List<List<String>>> getAusgewaehlteTabellenDaten() { if(this.ausgewaehlteTabelle!=null&&this.ausgewaehlteTabelle.getData()!=null) return Optional.of(this.ausgewaehlteTabelle.getData()); return Optional.empty();}
 public List<ExtrahierteTabelle> getVerfuegbareTabellen() { if(ausgewaehltesDokument!=null&&ausgewaehltesDokument.getTables()!=null) return new ArrayList<>(ausgewaehltesDokument.getTables()); return Collections.emptyList();}
 public ExtractionConfiguration getAktiveKonfiguration() { return aktiveKonfiguration; }
 public ConfigurationService getConfigurationService() { return configurationService; }
 public InvoiceTypeService getInvoiceTypeService() { return invoiceTypeService; }

 // --- Setter (lösen Events aus) ---
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
     } else { log.debug("--> Aktive Bereichs-Konfig nicht geändert."); }
 }

 // --- Kernfunktionalität: Laden und Exportieren ---

 /**
  * Überladene Methode zum Laden/Verarbeiten ohne explizite Konfig-Angabe.
  * Verwendet die aktuell im Modell gesetzte aktive Konfiguration.
  */
 public void ladeUndVerarbeitePdfs(List<Path> pdfPfade, Map<String, String> parameterGui, Consumer<PdfDokument> onSingleDocumentProcessedForStatus, Consumer<Double> progressCallback) {
     ladeUndVerarbeitePdfsMitKonfiguration(pdfPfade, parameterGui, this.aktiveKonfiguration, onSingleDocumentProcessedForStatus, progressCallback);
 }

 /**
  * Interne Methode: Lädt und verarbeitet PDFs asynchron mit einer spezifischen Bereichs-Konfiguration.
  * Orchestriert die Extraktion basierend auf globalen oder seitenspezifischen Bereichen.
  * Feuert 'documentsUpdated' und 'singleDocumentReprocessed' Events.
  * Aktualisiert die Referenz 'ausgewaehltesDokument' direkt, wenn dieses neu verarbeitet wird.
  *
  * @param pdfPfade Liste der PDF-Pfade.
  * @param parameterGui Extraktionsparameter aus der GUI (Flavor, RowTol etc.).
  * @param config Die zu verwendende ExtractionConfiguration (Bereichs-Konfig, kann null sein).
  * @param onSingleDocumentProcessedForStatus Callback für Status-Updates (pro Dokument).
  * @param progressCallback Callback für Gesamtfortschritt (0.0 bis 1.0).
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
         final Map<String, String> aktuelleGuiParameter = (parameterGui != null) ? new HashMap<>(parameterGui) : Collections.emptyMap();
         final Path aktuellerPdfPfad = pdfPfad; // Finale Referenz für Lambda

         log.info("Reiche PDF zur Verarbeitung ein: {}", aktuellerPdfPfad);

         executorService.submit(() -> { // Starte Verarbeitung im Thread-Pool
             PdfDokument finalesErgebnisDokument = null; // Das Dokument, das am Ende hinzugefügt wird
             boolean listUpdated = false; // Wurde die Hauptliste 'dokumente' geändert?
             StringBuilder gesammelteFehler = new StringBuilder(); // Sammelt Fehler von einzelnen Seiten
             PDDocument pdDocForKeyword = null; // Für Keyword-Suche
             boolean isSelectedDocumentBeingReprocessed = false; // Flag für Neuverarbeitung des Aktuellen

             try {
                 // 1. Basis-Dokumentobjekt erstellen
                 finalesErgebnisDokument = new PdfDokument();
                 finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                 finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());

                 // 1a. Prüfen, ob das aktuell ausgewählte Dokument neu verarbeitet wird
                 synchronized (dokumente) { // Nutze Lock von dokumente für beide Variablen
                      if (this.ausgewaehltesDokument != null &&
                          this.ausgewaehltesDokument.getFullPath() != null &&
                          this.ausgewaehltesDokument.getFullPath().equals(aktuellerPdfPfad.toString()))
                      {
                           isSelectedDocumentBeingReprocessed = true;
                           log.debug("Das aktuell ausgewählte Dokument '{}' wird neu verarbeitet.", this.ausgewaehltesDokument.getSourcePdf());
                      }
                 }

                 // 2. Rechnungstyp anhand Keywords ermitteln
                 InvoiceTypeConfig typConfig = null;
                 try {
                      pdDocForKeyword = PDDocument.load(aktuellerPdfPfad.toFile());
                      typConfig = invoiceTypeService.findConfigForPdf(pdDocForKeyword);
                 } catch (IOException e) {
                      log.error("Fehler beim Laden von {} für Keyword-Suche: {}", aktuellerPdfPfad, e.getMessage());
                      gesammelteFehler.append("Fehler Öffnen f. Keyword-Suche; ");
                      typConfig = invoiceTypeService.getDefaultConfig();
                 } finally {
                      if (pdDocForKeyword != null) try { pdDocForKeyword.close(); } catch (IOException e) { log.error("Fehler Schliessen PDF nach Keyword-Suche", e); }
                 }
                 log.info("--> Ermittelter Rechnungstyp für {}: {}", aktuellerPdfPfad.getFileName(), (typConfig != null ? typConfig.getType() : "Unbekannt/Default"));

                 // 3. Finale Extraktionsparameter bestimmen (GUI überschreibt CSV-Default)
                 Map<String, String> finaleParameter = new HashMap<>();
                 InvoiceTypeConfig effectiveTypConfig = (typConfig != null) ? typConfig : invoiceTypeService.getDefaultConfig();
                 finaleParameter.put("flavor", aktuelleGuiParameter.getOrDefault("flavor", effectiveTypConfig.getDefaultFlavor()));
                 finaleParameter.put("row_tol", aktuelleGuiParameter.getOrDefault("row_tol", effectiveTypConfig.getDefaultRowTol()));
                 log.debug("--> Finale Extraktionsparameter: {}", finaleParameter);

                 // 4. Bereiche und Seite(n) basierend auf Bereichs-Konfig ermitteln & Extraktion(en) starten
                 List<PdfDokument> teilErgebnisse = new ArrayList<>();

                 if (aktuelleBereichsKonfig == null) {
                     // Fall A: Keine Bereichs-Konfig -> 1 Aufruf für alles
                     log.debug("--> Keine Bereichs-Konfiguration aktiv, extrahiere alle Seiten ohne Bereiche.");
                     PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, null, "all");
                     if (ergebnis != null) teilErgebnisse.add(ergebnis); else gesammelteFehler.append("Extraktion lieferte null; ");

                 } else if (!aktuelleBereichsKonfig.isUsePageSpecificAreas()) {
                     // Fall B: Globale Bereichs-Konfig -> 1 Aufruf für alles mit Bereichen
                     List<String> areasForPython = null; List<AreaDefinition> globalAreas = aktuelleBereichsKonfig.getGlobalAreasList();
                     if (globalAreas != null && !globalAreas.isEmpty()) { areasForPython = globalAreas.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList()); log.info("--> Verwende {} globale Bereiche für alle Seiten.", areasForPython.size()); }
                     else { log.debug("--> Globaler Modus aktiv, aber keine Bereiche definiert."); }
                     PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, areasForPython, "all");
                     if (ergebnis != null) teilErgebnisse.add(ergebnis); else gesammelteFehler.append("Extraktion (global) lieferte null; ");

                 } else {
                     // Fall C: Seitenspezifische Bereichs-Konfig -> Mehrere Aufrufe
                     log.info("--> Seitenspezifischer Modus aktiv."); Map<Integer, List<AreaDefinition>> seitenBereicheMap = aktuelleBereichsKonfig.getPageSpecificAreasMap();
                     if (seitenBereicheMap == null || seitenBereicheMap.isEmpty()) {
                          log.warn("--> Seitenspezifischer Modus, aber KEINE Bereiche definiert. Extrahiere alle Seiten ohne Bereiche.");
                          PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, null, "all");
                          if (ergebnis != null) teilErgebnisse.add(ergebnis); else gesammelteFehler.append("Extraktion (Fallback) lieferte null; ");
                     } else {
                         Set<Integer> seiten = seitenBereicheMap.keySet(); log.info("--> Verarbeite spezifische Seiten mit Bereichen: {}", seiten);
                         for (Integer pageNum : seiten) { // Iteriere über definierte Seiten (1-basiert)
                             List<AreaDefinition> areasForPage = seitenBereicheMap.get(pageNum); if (areasForPage == null || areasForPage.isEmpty()) continue;
                             List<String> areasStr = areasForPage.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList());
                             log.info("----> Verarbeite Seite {} mit {} Bereichen...", pageNum, areasStr.size());
                             PdfDokument seitenErgebnis = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, areasStr, String.valueOf(pageNum)); // Nur diese Seite verarbeiten
                             if (seitenErgebnis != null) teilErgebnisse.add(seitenErgebnis);
                             else gesammelteFehler.append("Seite ").append(pageNum).append(": Extraktion fehlgeschlagen; ");
                         }
                     }
                 } // Ende Fallunterscheidung Konfiguration

                 // 5. Ergebnisse zusammenführen
                 log.info("Führe Ergebnisse von {} Aufruf(en) zusammen.", teilErgebnisse.size());
                 boolean firstResult = true;
                 for (PdfDokument teilErgebnis : teilErgebnisse) {
                      if (teilErgebnis.getTables() != null) finalesErgebnisDokument.addTables(teilErgebnis.getTables()); // Fügt Tabellen hinzu
                      if (firstResult && teilErgebnis.getAbrechnungszeitraumStartStr() != null) { // Nimm Datum vom ersten
                           finalesErgebnisDokument.setAbrechnungszeitraumStartStr(teilErgebnis.getAbrechnungszeitraumStartStr());
                           finalesErgebnisDokument.setAbrechnungszeitraumEndeStr(teilErgebnis.getAbrechnungszeitraumEndeStr());
                           firstResult = false;
                      }
                      if (teilErgebnis.getError() != null) gesammelteFehler.append(teilErgebnis.getError()).append("; "); // Sammle Fehler
                 }
                 log.info("Zusammengeführtes Ergebnis enthält {} Tabellen.", finalesErgebnisDokument.getTables().size());

                 // Setze gesammelte Fehler
                 if (gesammelteFehler.length() > 0) finalesErgebnisDokument.setError(gesammelteFehler.toString().trim());

                 // --- Liste aktualisieren UND ggf. Auswahl im Modell direkt aktualisieren ---
                  synchronized (dokumente) { // Lock für dokumente UND ausgewaehltesDokument/Tabelle
                      final String pfadStr = aktuellerPdfPfad.toString();
                      dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                      dokumente.add(finalesErgebnisDokument);
                      Collections.sort(dokumente);
                      listUpdated = true;

                      // *** Wenn das ausgewählte Dokument neu verarbeitet wurde, aktualisiere die Referenzen ***
                      if (isSelectedDocumentBeingReprocessed) {
                           log.debug("Aktualisiere Referenzen für ausgewaehltesDokument/Tabelle auf das neu verarbeitete Objekt.");
                           // Setze Referenzen direkt, ohne Events auszulösen (die kommen separat)
                           this.ausgewaehltesDokument = finalesErgebnisDokument;
                           List<ExtrahierteTabelle> neueTabellen = this.ausgewaehltesDokument.getTables();
                           // Setze auf erste Tabelle oder null
                           this.ausgewaehlteTabelle = (neueTabellen != null && !neueTabellen.isEmpty()) ? neueTabellen.get(0) : null;
                      }
                  }
                  // Callback für Status-Update
                  if (onSingleDocumentProcessedForStatus != null) { onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument); }

             } catch (Exception e) {
                 // Allgemeine Fehlerbehandlung
                  log.error("Unerwarteter Fehler bei der Verarbeitung von PDF {}: {}", aktuellerPdfPfad, e.getMessage(), e);
                  if (finalesErgebnisDokument == null) { finalesErgebnisDokument = new PdfDokument(); finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString()); finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());}
                  finalesErgebnisDokument.setError("Interner Fehler: " + e.getMessage());
                  synchronized(dokumente){ final String ps = aktuellerPdfPfad.toString(); dokumente.removeIf(d->d.getFullPath()!=null && d.getFullPath().equals(ps)); dokumente.add(finalesErgebnisDokument); Collections.sort(dokumente); listUpdated = true;
                       // Auch hier prüfen, ob das ausgewählte betroffen war
                       if (isSelectedDocumentBeingReprocessed) {
                            this.ausgewaehltesDokument = finalesErgebnisDokument;
                            this.ausgewaehlteTabelle = null;
                       }
                  }
                  if(onSingleDocumentProcessedForStatus!=null){onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument);}
             } finally {
                  // --- Fortschritt und Events ---
                  int processed = processedCount.incrementAndGet();
                  double progress = (totalPdfs > 0) ? (double) processed / totalPdfs : 1.0;
                  if (progressCallback != null) SwingUtilities.invokeLater(() -> progressCallback.accept(progress));

                  // Event für GUI-Update (Liste geändert), falls nötig
                  if (listUpdated) {
                       log.debug("Feuere PropertyChangeEvent '{}'", DOCUMENTS_UPDATED_PROPERTY);
                       SwingUtilities.invokeLater(() -> support.firePropertyChange(DOCUMENTS_UPDATED_PROPERTY, null, getDokumente()));
                  }
                  // Event speziell für das (neu) verarbeitete Dokument (wird *immer* gefeuert)
                  if (finalesErgebnisDokument != null) {
                      log.debug("Feuere PropertyChangeEvent '{}' für {}", SINGLE_DOCUMENT_REPROCESSED_PROPERTY, finalesErgebnisDokument.getSourcePdf());
                      final PdfDokument docToSend = finalesErgebnisDokument; // Finale Referenz
                      SwingUtilities.invokeLater(() -> support.firePropertyChange(SINGLE_DOCUMENT_REPROCESSED_PROPERTY, null, docToSend));
                  }
             }
         }); // Ende des Runnables für den ExecutorService
     } // Ende der for-Schleife über pdfPfade
 }


 // --- Excel Export und Shutdown ---
 public void exportiereAlleNachExcel(Path zielPfad) throws IOException {
     // Delegiere den Export an den ExcelExportService
     excelExportService.exportiereNachExcel(getDokumente(), zielPfad);
 }

 public void shutdownExecutor() {
      log.info("Fahre Executor Service herunter.");
     executorService.shutdown(); // Initiiert das Herunterfahren
     try {
         // Warte eine kurze Zeit auf die Beendigung laufender Tasks
         if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
             // Wenn Tasks nicht innerhalb des Timeouts beendet werden, erzwinge das Herunterfahren
             executorService.shutdownNow(); // Sendet Interrupts an laufende Threads
              log.warn("Executor Service wurde zwangsweise heruntergefahren.");
         } else {
              log.info("Executor Service erfolgreich heruntergefahren.");
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