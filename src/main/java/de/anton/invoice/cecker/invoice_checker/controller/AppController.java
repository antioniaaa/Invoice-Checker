package de.anton.invoice.cecker.invoice_checker.controller;


import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeConfig;
import de.anton.invoice.cecker.invoice_checker.model.PdfDokument;
import de.anton.invoice.cecker.invoice_checker.view.MainFrame;
import de.anton.invoice.cecker.invoice_checker.model.ExtractionConfiguration;
import de.anton.invoice.cecker.invoice_checker.view.ConfigurationDialog; // Import für Dialog


//Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Swing-Komponenten und Event-Handling
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;     // Für ComboBox Item Listener
import java.awt.AWTEvent;             // Für gemeinsamen Parameter-Handler
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


import javax.swing.event.ChangeListener;
import java.awt.event.ItemListener;
import java.util.Comparator;
//PDFBox für Keyword Suche
import org.apache.pdfbox.pdmodel.PDDocument;

public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private final MainFrame view;
 private JFileChooser dateiAuswahlDialog;

 public AppController(AnwendungsModell model, MainFrame view) {
     this.model = model;
     this.view = view;
     initController();
 }

 private void initController() {
     // Buttons
     view.addLadeButtonListener(this::handleLadePdfAktion);
     view.addExportButtonListener(this::handleExportExcelAktion);
     view.addRefreshButtonListener(this::handleRefreshAction); // NEU: Refresh Button
     view.addEditCsvButtonListener(this::handleEditCsv);     // NEU: Edit CSV Button
     // ComboBoxen
     view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
     view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
     // Parameter
     view.addFlavorComboBoxListener(this::handleParameterChange);
     view.addRowToleranceSpinnerListener(this::handleParameterChange);
     // Konfiguration
     view.addConfigMenuOpenListener(this::handleOpenConfigEditor);
     view.addConfigSelectionListener(this::handleConfigSelectionChange); // ItemListener

     setupDateiAuswahlDialog();
     // Lade verfügbare Konfigurationen beim Start und zeige sie an
     updateAvailableConfigsInView();
 }

 private void setupDateiAuswahlDialog() {
     dateiAuswahlDialog = new JFileChooser();
     dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
     dateiAuswahlDialog.setAcceptAllFileFilterUsed(false);
 }

 // --- Event Handler ---

 private void handleLadePdfAktion(ActionEvent e) {
      log.info("Lade PDF(s) Button geklickt.");
      view.setStatus("Öffne Dateiauswahl zum Laden...");
      dateiAuswahlDialog.setDialogTitle("PDF-Dateien auswählen");
      dateiAuswahlDialog.setMultiSelectionEnabled(true);
      dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));

      int rueckgabeWert = dateiAuswahlDialog.showOpenDialog(view);
      if (rueckgabeWert == JFileChooser.APPROVE_OPTION) {
          File[] ausgewaehlteDateien = dateiAuswahlDialog.getSelectedFiles();
          if (ausgewaehlteDateien != null && ausgewaehlteDateien.length > 0) {
              List<Path> pdfPfade = new ArrayList<>();
              for (File datei : ausgewaehlteDateien) {
                  pdfPfade.add(datei.toPath());
              }
              log.info("Ausgewählte Dateien: {}", Arrays.toString(ausgewaehlteDateien));
              view.setStatus("Verarbeite " + pdfPfade.size() + " PDF(s)...");
              view.setProgressBarVisible(true); // Fortschritt anzeigen
              view.setProgressBarValue(0);    // Fortschritt zurücksetzen

              // Aktuelle Parameter und die AKTIVE Konfiguration aus dem Modell verwenden
              Map<String, String> parameterGui = getCurrentParametersFromGui();
              // Modell ruft aktive Konfig selbst ab

              // Rufe Modell auf (überladene Methode ohne explizite Konfig)
              model.ladeUndVerarbeitePdfs(pdfPfade, parameterGui,
                 processedDoc -> { // Callback für Status
                     if (processedDoc != null) {
                          log.info("Callback nach Verarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                          SwingUtilities.invokeLater(() -> view.setStatus("Verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : "")));
                     } else { log.info("Callback empfangen (Dokument war null)."); }
                 },
                 progress -> { // Callback für Fortschritt
                      SwingUtilities.invokeLater(() -> {
                           view.setProgressBarValue((int) (progress * 100));
                           if (progress >= 1.0) {
                                // Blende Balken nach 2 Sek aus
                                Timer timer = new Timer(2000, ae -> view.setProgressBarVisible(false));
                                timer.setRepeats(false);
                                timer.start();
                           }
                      });
                 }
              );
          } else { log.warn("Keine Dateien ausgewählt."); view.setStatus("Keine Dateien ausgewählt.");}
      } else { log.info("Dateiauswahl abgebrochen."); view.setStatus("Dateiauswahl abgebrochen.");}
 }

 private void handleExportExcelAktion(ActionEvent e) { /* ... unverändert ... */ }

 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if (e.getActionCommand().equals("comboBoxChanged")) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", selectedDoc);
              model.setAusgewaehltesDokument(selectedDoc); // Löst Events aus
              // Aktualisiere das InvoiceType Panel für das neue Dokument
              updateInvoiceTypePanel(selectedDoc);
          }
     }
 }

 private void handleTabelleComboBoxAuswahl(ActionEvent e) { /* ... unverändert ... */ }
 private void handleParameterChange(AWTEvent e) { triggerReprocessing("Parameter geändert"); }
 private void handleParameterChange(ChangeEvent e) { triggerReprocessing("Parameter geändert"); }
 private void handleOpenConfigEditor(ActionEvent e) { /* ... unverändert, ruft jetzt updateAvailableConfigsInView ... */ }
 private void handleConfigSelectionChange(ItemEvent e) { /* ... unverändert, löst triggerReprocessing aus ... */ }
 private void handleEditCsv(ActionEvent e) { log.info("Edit CSV Button geklickt."); model.getInvoiceTypeService().openCsvInEditor(); }
 // NEU: Handler für Refresh Button
 private void handleRefreshAction(ActionEvent e) { triggerReprocessing("Refresh Button"); }


 // --- Hilfsmethoden ---

 private void triggerReprocessing(String grund) {
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
          Path pdfPath = null;
          try { pdfPath = Paths.get(selectedDoc.getFullPath()); }
          catch (Exception pathEx) { log.error("Ungültiger Pfad: {}", selectedDoc.getFullPath(), pathEx); view.setStatus("Fehler: Ungültiger Pfad."); return; }

          if (pdfPath != null) {
              Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();
              ExtractionConfiguration aktiveConfig = model.getAktiveKonfiguration();

              log.info("({}) Starte Neuverarbeitung für PDF: {} mit GUI-Parametern: {}, Konfig: {}",
                       grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, (aktiveConfig != null ? aktiveConfig.getName() : "Keine"));
              view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);

              // Rufe Modell auf (Methode, die Konfig explizit nimmt)
              model.ladeUndVerarbeitePdfsMitKonfiguration(
                  Collections.singletonList(pdfPath),
                  aktuelleParameterGui,
                  aktiveConfig, // Übergib die aktuell aktive Konfig
                  processedDoc -> { /* Callback Status */ if(processedDoc!=null) SwingUtilities.invokeLater(()->view.setStatus("Neu verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));},
                  progress -> { /* Callback Progress */ SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(progress*100)); if(progress>=1.0){ Timer t = new Timer(2000, ae -> view.setProgressBarVisible(false)); t.setRepeats(false); t.start();}}); }
              );
          }
      } else { log.debug("({}) aber kein PDF ausgewählt oder Pfad fehlt. Keine Aktion.", grund); }
 }


 private Map<String, String> getCurrentParametersFromGui() {
      Map<String,String> p = new HashMap<>();
      try {
          p.put("flavor", (String)view.getFlavorComboBox().getSelectedItem());
          Object v=view.getRowToleranceSpinner().getValue();
          p.put("row_tol", String.valueOf(v instanceof Number ? ((Number)v).intValue() : "2"));
      } catch (Exception e) { log.error("Fehler GUI-Parameter Lesen", e); p.putIfAbsent("flavor", "lattice"); p.putIfAbsent("row_tol", "2"); }
      return p;
 }

  private void updateAvailableConfigsInView() {
      log.debug("Aktualisiere Bereichs-Konfigurations-ComboBox in der View...");
      List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations();
      ExtractionConfiguration activeConfig = model.getAktiveKonfiguration();
      view.updateConfigurationComboBox(configs, activeConfig); // Rufe View-Methode auf
  }

  private void updateInvoiceTypePanel(PdfDokument pdfDoc) {
      if (pdfDoc == null || pdfDoc.getFullPath() == null || pdfDoc.getFullPath().isBlank()) {
          view.updateInvoiceTypeDisplay(null); // Panel leeren
          return;
      }
      final Path pdfPath = Paths.get(pdfDoc.getFullPath()); // Pfad für den Worker

      SwingWorker<InvoiceTypeConfig, Void> worker = new SwingWorker<>() {
          @Override protected InvoiceTypeConfig doInBackground() throws Exception {
               log.debug("Starte Keyword-Suche für {}", pdfDoc.getSourcePdf());
               PDDocument pdDoc = null;
               try {
                   pdDoc = PDDocument.load(pdfPath.toFile());
                   return model.getInvoiceTypeService().findConfigForPdf(pdDoc);
               } finally { if (pdDoc != null) try { pdDoc.close(); } catch (IOException e) { log.error("Fehler beim Schließen des Dokuments nach Keyword-Suche", e);} }
          }
          @Override protected void done() {
              try {
                  InvoiceTypeConfig foundConfig = get();
                  log.info("Keyword-Suche abgeschlossen. Typ: {}", (foundConfig != null ? foundConfig.getType() : "Default/Fehler"));
                  view.updateInvoiceTypeDisplay(foundConfig); // GUI im EDT aktualisieren
              } catch (Exception e) { log.error("Fehler beim Abrufen des Keyword-Suchergebnisses", e); view.updateInvoiceTypeDisplay(null); }
          }
      };
      worker.execute();
  }
}