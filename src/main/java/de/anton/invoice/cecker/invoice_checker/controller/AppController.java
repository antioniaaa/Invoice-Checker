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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


//PDFBox für Keyword Suche
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.swing.event.ChangeListener;
import java.awt.event.ItemListener;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
* Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
* Er reagiert auf Benutzeraktionen und delegiert Aufgaben an das Modell.
* Stößt explizit GUI-Updates nach Neuverarbeitung an.
*/
public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private final MainFrame view;
 private JFileChooser dateiAuswahlDialog;

 /**
  * Konstruktor. Initialisiert Modell und View und registriert die Listener.
  * @param model Das Anwendungsmodell.
  * @param view Das Hauptfenster (GUI).
  */
 public AppController(AnwendungsModell model, MainFrame view) {
     this.model = model;
     this.view = view;
     initController();
 }

 /**
  * Registriert alle notwendigen Listener bei den GUI-Komponenten.
  * Lädt initiale Konfigurationen.
  */
 private void initController() {
     view.addLadeButtonListener(this::handleLadePdfAktion);
     view.addExportButtonListener(this::handleExportExcelAktion);
     view.addRefreshButtonListener(this::handleRefreshAction);
     view.addEditCsvButtonListener(this::handleEditCsv);
     view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
     view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
     view.addFlavorComboBoxListener(this::handleParameterChange);
     view.addRowToleranceSpinnerListener(this::handleParameterChange);
     view.addConfigMenuOpenListener(this::handleOpenConfigEditor);
     view.addConfigSelectionListener(this::handleConfigSelectionChange);

     setupDateiAuswahlDialog();
     updateAvailableConfigsInView(); // Lade Bereichs-Konfigs beim Start
 }

 /**
  * Konfiguriert den JFileChooser.
  */
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
              view.setProgressBarVisible(true); view.setProgressBarValue(0);

              Map<String, String> parameterGui = getCurrentParametersFromGui();
              ExtractionConfiguration aktiveConfig = model.getAktiveKonfiguration();
              log.info("--> Verwende Parameter: {} und aktive Bereichs-Konfig: {}", parameterGui, (aktiveConfig != null ? aktiveConfig.getName() : "Keine"));

              // Rufe Modell auf
              model.ladeUndVerarbeitePdfsMitKonfiguration(pdfPfade, parameterGui, aktiveConfig,
                 processedDoc -> { // Callback für Status
                     if (processedDoc != null) {
                          log.info("Callback nach Verarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                          SwingUtilities.invokeLater(() -> view.setStatus("Verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : "")));
                     } else { log.info("Callback empfangen (Dokument war null)."); }
                 },
                 progress -> { // Callback für Fortschritt
                      SwingUtilities.invokeLater(() -> {
                           view.setProgressBarValue((int) (progress * 100));
                           if (progress >= 1.0) { Timer t = new Timer(2000, ae -> view.setProgressBarVisible(false)); t.setRepeats(false); t.start();}
                      });
                 }
              );
          } else { log.warn("Keine Dateien ausgewählt."); view.setStatus("Keine Dateien ausgewählt.");}
      } else { log.info("Dateiauswahl abgebrochen."); view.setStatus("Dateiauswahl abgebrochen.");}
 }

 private void handleExportExcelAktion(ActionEvent e) {
     log.info("Export nach Excel Button geklickt.");
     dateiAuswahlDialog.setDialogTitle("Excel-Datei speichern unter..."); dateiAuswahlDialog.setMultiSelectionEnabled(false); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx")); dateiAuswahlDialog.setSelectedFile(new File("Extrahierte_Tabellen.xlsx"));
     int ret = dateiAuswahlDialog.showSaveDialog(view);
     if (ret == JFileChooser.APPROVE_OPTION) {
          File file = dateiAuswahlDialog.getSelectedFile(); Path ziel = file.toPath(); if(!ziel.toString().toLowerCase().endsWith(".xlsx")) ziel = ziel.resolveSibling(ziel.getFileName()+".xlsx");
          final Path finalZiel = ziel; view.setStatus("Exportiere..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
          SwingWorker<Void,Void> worker=new SwingWorker<>(){ boolean erfolg=false; String fehler=null; @Override protected Void doInBackground(){try{model.exportiereAlleNachExcel(finalZiel); erfolg=true;} catch(Exception ex){fehler=ex.getMessage();log.error("Excel Export Fehler",ex);} finally{SwingUtilities.invokeLater(()->{view.setProgressBarValue(100); Timer t=new Timer(1000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();});} return null;} @Override protected void done(){if(erfolg)JOptionPane.showMessageDialog(view,"Export erfolgreich nach\n"+finalZiel,"Erfolg",JOptionPane.INFORMATION_MESSAGE); else JOptionPane.showMessageDialog(view,"Export fehlgeschlagen.\n"+fehler,"Fehler",JOptionPane.ERROR_MESSAGE); view.setStatus(erfolg?"Export fertig.":"Export fehlgeschlagen.");}};
          worker.execute();
     } else { log.info("Export abgebrochen."); view.setStatus("Export abgebrochen."); }
 }

 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand())) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
              model.setAusgewaehltesDokument(selectedDoc); // Löst Events aus
              updateInvoiceTypePanel(selectedDoc); // Aktualisiere Panel für neuen Typ
          }
     }
 }

 private void handleTabelleComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand())) {
         ExtrahierteTabelle selectedTable = (ExtrahierteTabelle) view.getTabelleComboBox().getSelectedItem();
         if (selectedTable != null && !Objects.equals(model.getAusgewaehlteTabelle(), selectedTable)) {
              log.info("Tabellen ComboBox Auswahl geändert zu: {}", selectedTable);
              model.setAusgewaehlteTabelle(selectedTable); // Löst Event aus
          }
     }
 }

 private void handleParameterChange(AWTEvent e) { triggerReprocessing("Parameter geändert"); }
 private void handleParameterChange(ChangeEvent e) { triggerReprocessing("Parameter geändert"); }

 private void handleOpenConfigEditor(ActionEvent e) {
      log.info("Öffne Bereichs-Konfigurationseditor (Menü)...");
      ExtractionConfiguration configToEdit = model.getAktiveKonfiguration();
      ExtractionConfiguration configForDialog;
      if (configToEdit != null) { configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName()); if (configForDialog == null) { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); } }
      else { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); }
      ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);
      dialog.setVisible(true);
      ExtractionConfiguration savedConfig = dialog.getSavedConfiguration();
      if (savedConfig != null) {
          log.info("Bereichs-Konfiguration '{}' wurde im Dialog gespeichert.", savedConfig.getName());
          updateAvailableConfigsInView(); // Liste neu laden
          model.setAktiveKonfiguration(savedConfig); // Als aktiv setzen (löst Event in View)
          triggerReprocessing("Bereichs-Konfiguration gespeichert"); // Neu verarbeiten
      } else { log.info("Bereichs-Konfigurationsdialog geschlossen ohne Speichern."); }
 }

  private void handleConfigSelectionChange(ItemEvent e) {
     if (e.getStateChange() == ItemEvent.SELECTED) {
         Object selectedItem = e.getItem();
         ExtractionConfiguration selectedConfig = null;
         if (selectedItem instanceof ExtractionConfiguration) { selectedConfig = (ExtractionConfiguration) selectedItem; }
         else if (!"Keine".equals(selectedItem)) { log.warn("Unerwartetes Item in Konfig-Combo: {}", selectedItem); return; }

         log.info("Bereichs-Konfig Auswahl geändert zu: {}", (selectedConfig != null ? selectedConfig.getName() : "Keine"));
         if (!Objects.equals(model.getAktiveKonfiguration(), selectedConfig)) {
              model.setAktiveKonfiguration(selectedConfig); // Löst Event aus
              triggerReprocessing("Bereichs-Konfiguration geändert"); // Neuverarbeitung
         }
     }
 }

 private void handleEditCsv(ActionEvent e) { log.info("Edit CSV Button geklickt."); model.getInvoiceTypeService().openCsvInEditor(); }
 private void handleRefreshAction(ActionEvent e) { triggerReprocessing("Refresh Button"); }


 // --- Hilfsmethoden ---

 /**
  * Löst die Neuverarbeitung des aktuell im Modell ausgewählten PDF-Dokuments
  * mit den aktuell in der GUI eingestellten Parametern und der im Modell aktiven Bereichs-Konfiguration aus.
  * Stößt die GUI-Updates im Callback explizit an.
  * @param grund Ein String, der den Grund für die Neuverarbeitung beschreibt (für Logging).
  */
 private void triggerReprocessing(String grund) {
     PdfDokument selectedDoc = model.getAusgewaehltesDokument();
     if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
         Path pdfPath = null;
         try { pdfPath = Paths.get(selectedDoc.getFullPath()); }
         catch (Exception pathEx) { log.error("Ungültiger Pfad: {}", selectedDoc.getFullPath(), pathEx); view.setStatus("Fehler: Ungültiger Pfad."); return; }

         if (pdfPath != null) {
             Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();
             ExtractionConfiguration aktiveConfig = model.getAktiveKonfiguration();

             log.info("({}) Starte Neuverarbeitung für PDF: {} mit GUI-Parametern: {}, Bereichs-Konfig: {}",
                      grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, (aktiveConfig != null ? aktiveConfig.getName() : "Keine"));
             view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");
             view.setProgressBarVisible(true); view.setProgressBarValue(0);

             // Rufe Modell auf (Methode, die Konfig explizit nimmt)
             model.ladeUndVerarbeitePdfsMitKonfiguration(
                 Collections.singletonList(pdfPath),
                 aktuelleParameterGui,
                 aktiveConfig, // Übergib die aktuell aktive Konfig
                 processedDoc -> { // Callback für Status nach Neuverarbeitung
                     if (processedDoc != null) {
                         log.info("Callback nach Neuverarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                         SwingUtilities.invokeLater(() -> { // Alles im EDT ausführen!
                             view.setStatus("Neu verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : ""));

                             // --- EXPLIZITE GUI-UPDATES NACH NEUVERARBEITUNG ---
                             log.info("Stoße explizite GUI-Updates nach Neuverarbeitung an...");
                             // 1. PDF-ComboBox ist schon richtig (Dokument hat sich nicht geändert)
                             // 2. Tabellen-ComboBox neu laden (Inhalt des Dokuments hat sich geändert!)
                             view.updateTabelleComboBox();
                             // 3. Daten-Tabelle aktualisieren (basierend auf der Auswahl in der Tabellen-Combo)
                             view.updateDatenTabelle();
                             // 4. InvoiceType Panel neu laden (falls sich was geändert haben könnte - unwahrscheinlich hier)
                             // updateInvoiceTypePanel(processedDoc); // Normalerweise nicht nötig bei Reprocessing
                             // --- ENDE EXPLIZITE UPDATES ---

                         });
                     } else {
                         log.warn("Callback nach Neuverarbeitung: Dokument ist null.");
                         SwingUtilities.invokeLater(() -> view.setStatus("Fehler bei Neuverarbeitung."));
                     }
                 },
                 progress -> { // Callback für Fortschritt
                      SwingUtilities.invokeLater(()->{
                           view.setProgressBarValue((int)(progress*100));
                           if(progress>=1.0){ Timer t = new Timer(2000, ae -> view.setProgressBarVisible(false)); t.setRepeats(false); t.start();}
                      });
                 }
             );
         }
     } else {
         log.debug("({}) aber kein PDF ausgewählt oder Pfad fehlt. Keine Aktion.", grund);
         view.setStatus("Bitte zuerst ein PDF auswählen."); // Hinweis an User
     }
 }

 /** Liest Parameter aus GUI. */
 private Map<String, String> getCurrentParametersFromGui() {
      Map<String,String> p = new HashMap<>();
      try {
          p.put("flavor", (String)view.getFlavorComboBox().getSelectedItem());
          Object v=view.getRowToleranceSpinner().getValue();
          p.put("row_tol", String.valueOf(v instanceof Number ? ((Number)v).intValue() : "2"));
      } catch (Exception e) { log.error("Fehler GUI-Parameter Lesen", e); p.putIfAbsent("flavor", "lattice"); p.putIfAbsent("row_tol", "2"); }
      return p;
 }

  /** Lädt verfügbare Bereichs-Konfigurationen und aktualisiert die View. */
  private void updateAvailableConfigsInView() {
      log.debug("Aktualisiere Bereichs-Konfigurations-ComboBox in der View...");
      List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations();
      ExtractionConfiguration activeConfig = model.getAktiveKonfiguration();
      view.updateConfigurationComboBox(configs, activeConfig);
  }

  /** Aktualisiert das Invoice Type Panel in der GUI basierend auf PDF-Inhalt. */
  private void updateInvoiceTypePanel(PdfDokument pdfDoc) {
      if (pdfDoc == null || pdfDoc.getFullPath() == null || pdfDoc.getFullPath().isBlank()) {
          view.updateInvoiceTypeDisplay(null); return;
      }
      final Path pdfPath;
      try { pdfPath = Paths.get(pdfDoc.getFullPath()); if (!Files.exists(pdfPath)) { log.error("PDF für Keyword nicht gefunden: {}", pdfPath); view.updateInvoiceTypeDisplay(null); view.setStatus("Fehler: PDF für Typ nicht gefunden."); return; } }
      catch (Exception e) { log.error("Ungültiger PDF-Pfad: {}", pdfDoc.getFullPath(), e); view.updateInvoiceTypeDisplay(null); view.setStatus("Fehler: Ungültiger PDF-Pfad."); return; }

      view.updateInvoiceTypeDisplay(null); // Zeige "Suche..." oder leere Felder

      SwingWorker<InvoiceTypeConfig, Void> worker = new SwingWorker<>() {
          @Override protected InvoiceTypeConfig doInBackground() throws Exception {
               log.debug("Starte Keyword-Suche für {}", pdfDoc.getSourcePdf());
               PDDocument pdDoc = null;
               try { pdDoc = PDDocument.load(pdfPath.toFile()); return model.getInvoiceTypeService().findConfigForPdf(pdDoc); }
               finally { if (pdDoc != null) try { pdDoc.close(); } catch (IOException e) { log.error("Fehler Schließen Doc nach Keyword", e);} }
          }
          @Override protected void done() {
              try { InvoiceTypeConfig found = get(); log.info("Keyword-Suche fertig. Typ: {}", (found != null ? found.getType() : "Default/Fehler")); view.updateInvoiceTypeDisplay(found); }
              catch (Exception e) { log.error("Fehler Abrufen Keyword-Ergebnis", e); view.updateInvoiceTypeDisplay(null); view.setStatus("Fehler bei Rechnungstyp-Erkennung."); }
          }
      };
      worker.execute();
  }
}