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
  * Orchestriert die Extraktion basierend auf globalen oder seitenspezifischen Bereichen.
  *
  * @param pdfPfade Liste der PDF-Pfade.
  * @param parameter Extraktionsparameter (Flavor, RowTol etc.).
  * @param config Die zu verwendende ExtractionConfiguration (kann null sein).
  * @param onSingleDocumentProcessedForStatus Callback für Status.
  */
 public void ladeUndVerarbeitePdfsMitKonfiguration(List<Path> pdfPfade, Map<String, String> parameter, ExtractionConfiguration config, Consumer<PdfDokument> onSingleDocumentProcessedForStatus) {
     log.info("Starte Ladevorgang für {} PDFs mit Parametern: {} und Konfiguration: {}",
              pdfPfade.size(), parameter, (config != null ? config.getName() : "Keine"));

     for (Path pdfPfad : pdfPfade) {
         final Map<String, String> aktuelleParameter = (parameter != null) ? new HashMap<>(parameter) : Collections.emptyMap();
         final ExtractionConfiguration aktuelleConfig = config; // Finale Referenz für Lambda
         final Path aktuellerPdfPfad = pdfPfad;

         log.info("Reiche PDF zur Verarbeitung ein: {}", aktuellerPdfPfad);

         executorService.submit(() -> { // Starte Verarbeitung im Thread-Pool
             PdfDokument finalesErgebnisDokument = null; // Das Dokument, das am Ende hinzugefügt wird
             boolean listUpdated = false;
             StringBuilder gesammelteFehler = new StringBuilder(); // Sammelt Fehler von einzelnen Seiten

             try {
                 // Erstelle ein Basis-Dokumentobjekt für Metadaten
                 finalesErgebnisDokument = new PdfDokument();
                 finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                 finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());

                 // --- Entscheide Verarbeitungsstrategie basierend auf Konfig ---
                 if (aktuelleConfig == null) {
                     // Fall 1: Keine Konfiguration -> Extrahiere alle Seiten ohne Bereiche
                     log.debug("--> Keine aktive Konfiguration, extrahiere alle Seiten ohne Bereichseinschränkung.");
                     PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(
                         aktuellerPdfPfad, aktuelleParameter, null, "all");
                     if (ergebnis != null) {
                         finalesErgebnisDokument.addTables(ergebnis.getTables());
                         finalesErgebnisDokument.setAbrechnungszeitraumStartStr(ergebnis.getAbrechnungszeitraumStartStr());
                         finalesErgebnisDokument.setAbrechnungszeitraumEndeStr(ergebnis.getAbrechnungszeitraumEndeStr());
                         if (ergebnis.getError() != null) gesammelteFehler.append(ergebnis.getError()).append("; ");
                     } else { gesammelteFehler.append("Extraktion lieferte null zurück; "); }

                 } else if (!aktuelleConfig.isUsePageSpecificAreas()) {
                     // Fall 2: Globale Bereiche sind aktiv
                     List<AreaDefinition> globalAreas = aktuelleConfig.getGlobalAreasList();
                     List<String> areasForPython = null;
                     if (globalAreas != null && !globalAreas.isEmpty()) {
                         areasForPython = globalAreas.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList());
                         log.info("--> Verwende {} globale Bereiche für alle Seiten.", areasForPython.size());
                     } else {
                         log.debug("--> Globaler Modus aktiv, aber keine globalen Bereiche definiert. Extrahiere ohne Bereiche.");
                     }
                     PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(
                         aktuellerPdfPfad, aktuelleParameter, areasForPython, "all");
                     if (ergebnis != null) {
                          finalesErgebnisDokument.addTables(ergebnis.getTables());
                          finalesErgebnisDokument.setAbrechnungszeitraumStartStr(ergebnis.getAbrechnungszeitraumStartStr());
                          finalesErgebnisDokument.setAbrechnungszeitraumEndeStr(ergebnis.getAbrechnungszeitraumEndeStr());
                          if (ergebnis.getError() != null) gesammelteFehler.append(ergebnis.getError()).append("; ");
                     } else { gesammelteFehler.append("Extraktion (global) lieferte null zurück; "); }

                 } else {
                     // Fall 3: Seitenspezifische Bereiche sind aktiv
                     log.info("--> Seitenspezifischer Modus aktiv. Verarbeite Seiten mit definierten Bereichen.");
                     Map<Integer, List<AreaDefinition>> seitenBereicheMap = aktuelleConfig.getPageSpecificAreasMap();
                     if (seitenBereicheMap == null || seitenBereicheMap.isEmpty()) {
                         // Fall 3a: Seitenspezifisch aktiv, aber keine Bereiche definiert
                         log.warn("--> Seitenspezifischer Modus, aber KEINE Bereiche definiert. Extrahiere alle Seiten ohne Bereiche.");
                         PdfDokument ergebnis = extraktionsService.extrahiereTabellenAusPdf(
                             aktuellerPdfPfad, aktuelleParameter, null, "all");
                          if (ergebnis != null) {
                              finalesErgebnisDokument.addTables(ergebnis.getTables());
                              finalesErgebnisDokument.setAbrechnungszeitraumStartStr(ergebnis.getAbrechnungszeitraumStartStr());
                              finalesErgebnisDokument.setAbrechnungszeitraumEndeStr(ergebnis.getAbrechnungszeitraumEndeStr());
                              if (ergebnis.getError() != null) gesammelteFehler.append(ergebnis.getError()).append("; ");
                          } else { gesammelteFehler.append("Extraktion (Fallback ohne Bereiche) lieferte null zurück; "); }
                     } else {
                         // Fall 3b: Seitenspezifisch aktiv UND Bereiche definiert -> Iteriere über Seiten
                         Set<Integer> seitenMitBereichen = seitenBereicheMap.keySet();
                          log.info("--> Verarbeite spezifische Seiten: {}", seitenMitBereichen);
                         List<PdfDokument> teilErgebnisse = new ArrayList<>();
                         boolean firstPageProcessed = false; // Um Datum nur einmal zu setzen

                         for (Integer pageNum : seitenMitBereichen) { // Iteriere über definierte Seiten (1-basiert)
                             List<AreaDefinition> areasForPage = seitenBereicheMap.get(pageNum);
                             if (areasForPage == null || areasForPage.isEmpty()) continue; // Sollte nicht vorkommen, aber sicher ist sicher

                             List<String> areasForPythonPage = areasForPage.stream()
                                                                 .map(AreaDefinition::toCamelotString)
                                                                 .collect(Collectors.toList());
                             log.info("----> Verarbeite Seite {} mit {} Bereichen...", pageNum, areasForPythonPage.size());

                             // Rufe Service für DIESE EINE Seite mit IHREN Bereichen auf
                             PdfDokument seitenErgebnis = extraktionsService.extrahiereTabellenAusPdf(
                                 aktuellerPdfPfad,
                                 aktuelleParameter,
                                 areasForPythonPage, // Bereiche nur für diese Seite
                                 String.valueOf(pageNum) // Nur diese Seite verarbeiten
                             );

                             if (seitenErgebnis != null) {
                                  teilErgebnisse.add(seitenErgebnis);
                                  // Nimm Datum vom Ergebnis der ersten erfolgreich verarbeiteten Seite
                                  if (!firstPageProcessed && seitenErgebnis.getAbrechnungszeitraumStartStr() != null) {
                                       finalesErgebnisDokument.setAbrechnungszeitraumStartStr(seitenErgebnis.getAbrechnungszeitraumStartStr());
                                       finalesErgebnisDokument.setAbrechnungszeitraumEndeStr(seitenErgebnis.getAbrechnungszeitraumEndeStr());
                                       firstPageProcessed = true;
                                  }
                                  // Sammle Fehler von einzelnen Seiten
                                  if (seitenErgebnis.getError() != null) {
                                       gesammelteFehler.append("Seite ").append(pageNum).append(": ").append(seitenErgebnis.getError()).append("; ");
                                       log.warn("Fehler bei Extraktion von Seite {}: {}", pageNum, seitenErgebnis.getError());
                                  }
                             } else {
                                  log.error("Extraktion für Seite {} lieferte null zurück.", pageNum);
                                  gesammelteFehler.append("Seite ").append(pageNum).append(": Extraktion fehlgeschlagen (null); ");
                             }
                         } // Ende Schleife über Seiten

                         // Füge alle gefundenen Tabellen aus den Teilergebnissen zusammen
                         log.info("Führe Ergebnisse von {} verarbeiteten Seiten zusammen.", teilErgebnisse.size());
                         for(PdfDokument teilErgebnis : teilErgebnisse) {
                             if (teilErgebnis.getTables() != null) {
                                  finalesErgebnisDokument.addTables(teilErgebnis.getTables());
                             }
                         }
                         log.info("Zusammengeführtes Ergebnis enthält {} Tabellen.", finalesErgebnisDokument.getTables().size());
                     }
                 } // Ende Fallunterscheidung Konfiguration

                 // Setze gesammelte Fehler (falls vorhanden) im finalen Dokument
                 if (gesammelteFehler.length() > 0) {
                     finalesErgebnisDokument.setError(gesammelteFehler.toString().trim());
                 }

                 // --- Liste aktualisieren (mit dem finalen Ergebnis) ---
                  synchronized (dokumente) {
                      final String pfadStr = aktuellerPdfPfad.toString();
                      dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                      dokumente.add(finalesErgebnisDokument);
                      Collections.sort(dokumente);
                      listUpdated = true;
                  }
                  // Callback für Status-Update aufrufen
                  if (onSingleDocumentProcessedForStatus != null) {
                      onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument);
                  }

             } catch (Exception e) {
                 // Fange unerwartete Fehler während der Orchestrierung
                 log.error("Unerwarteter Fehler bei der Verarbeitung von PDF {}: {}", aktuellerPdfPfad, e.getMessage(), e);
                 if (finalesErgebnisDokument == null) { // Erstelle Fehlerobjekt, falls noch nicht geschehen
                      finalesErgebnisDokument = new PdfDokument();
                      finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                      finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());
                 }
                 finalesErgebnisDokument.setError("Interner Orchestrierungsfehler: " + e.getMessage());
                 // Füge Fehlerdokument zur Liste hinzu
                 synchronized(dokumente){
                      final String pfadStr = aktuellerPdfPfad.toString();
                      dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                      dokumente.add(finalesErgebnisDokument);
                      Collections.sort(dokumente);
                      listUpdated = true;
                 }
                 if(onSingleDocumentProcessedForStatus!=null){ onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument); }
             } finally {
                  // Feuere das Update-Event für die View, wenn die Liste geändert wurde
                  if (listUpdated) {
                       log.debug("Feuere PropertyChangeEvent '{}'", DOCUMENTS_UPDATED_PROPERTY);
                       SwingUtilities.invokeLater(() -> support.firePropertyChange(DOCUMENTS_UPDATED_PROPERTY, null, getDokumente()));
                  }
             }
         }); // Ende des Runnables für den ExecutorService
     } // Ende der for-Schleife über pdfPfade
 }


 // --- Excel Export und Shutdown (unverändert) ---
 public void exportiereAlleNachExcel(Path zielPfad) throws IOException { excelExportService.exportiereNachExcel(getDokumente(), zielPfad); }
 public void shutdownExecutor() { log.info("Shutdown executor."); executorService.shutdown(); try { if (!executorService.awaitTermination(5,TimeUnit.SECONDS)) executorService.shutdownNow(); } catch (InterruptedException e) { executorService.shutdownNow(); Thread.currentThread().interrupt(); } }

}