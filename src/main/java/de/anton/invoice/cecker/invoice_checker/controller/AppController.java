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
import java.awt.AWTEvent;
import java.util.Comparator;
import java.util.stream.Collectors;

public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private final MainFrame view; // Wird im Konstruktor übergeben
 private JFileChooser dateiAuswahlDialog;
 // private InvoiceTypeConfig lastDetectedInvoiceType = null; // Nicht mehr benötigt, holen wir aus PdfDokument

 /**
  * Konstruktor. Initialisiert Modell und View und registriert die Listener.
  * @param model Das Anwendungsmodell.
  * @param view Das Hauptfenster (GUI).
  */
 public AppController(AnwendungsModell model, MainFrame view) { // Nimmt View entgegen
     this.model = model;
     this.view = view;
     if (this.view == null || this.model == null) {
          throw new IllegalArgumentException("Modell und View dürfen im Controller nicht null sein!");
     }
     initializeListeners(); // Listener registrieren
 }

 /**
  * Registriert alle notwendigen Listener bei den GUI-Komponenten der View.
  */
 private void initializeListeners() {
      log.info("Initialisiere Controller-Listener für View...");
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
      updateAvailableConfigsInView();
      log.info("Controller-Listener erfolgreich initialisiert.");
 }

 private void setupDateiAuswahlDialog() {
     dateiAuswahlDialog = new JFileChooser();
     dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
     dateiAuswahlDialog.setAcceptAllFileFilterUsed(false);
 }

 // --- Event Handler ---

 private void handleLadePdfAktion(ActionEvent e) {
      log.info("Lade PDF(s) Button geklickt.");
      if (view == null) { log.error("View ist null in handleLadePdfAktion"); return; }
      view.setStatus("Öffne Dateiauswahl zum Laden...");
      dateiAuswahlDialog.setDialogTitle("PDF-Dateien auswählen"); dateiAuswahlDialog.setMultiSelectionEnabled(true); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
      int rueckgabeWert = dateiAuswahlDialog.showOpenDialog(view);
      if (rueckgabeWert == JFileChooser.APPROVE_OPTION) {
          File[] ausgewaehlteDateien = dateiAuswahlDialog.getSelectedFiles();
          if (ausgewaehlteDateien != null && ausgewaehlteDateien.length > 0) {
              List<Path> pdfPfade = new ArrayList<>(); for (File datei : ausgewaehlteDateien) { pdfPfade.add(datei.toPath()); }
              log.info("Ausgewählte Dateien: {}", Arrays.toString(ausgewaehlteDateien));
              view.setStatus("Verarbeite " + pdfPfade.size() + " PDF(s)...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);
              Map<String, String> parameterGui = getCurrentParametersFromGui();
              model.ladeUndVerarbeitePdfs(pdfPfade, parameterGui, // Ruft Methode auf, die aktive Konfig nutzt
                 processedDoc -> { /* Status Callback */ if(processedDoc!=null) SwingUtilities.invokeLater(()->view.setStatus("Verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":""))); },
                 progress -> { /* Progress Callback */ SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(progress*100)); if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}}); }
              );
          } else { log.warn("Keine Dateien ausgewählt."); view.setStatus("Keine Dateien ausgewählt.");}
      } else { log.info("Dateiauswahl abgebrochen."); view.setStatus("Dateiauswahl abgebrochen.");}
 }

 private void handleExportExcelAktion(ActionEvent e) {
     log.info("Export nach Excel Button geklickt."); if (view == null) { log.error("View ist null..."); return; }
     dateiAuswahlDialog.setDialogTitle("Excel speichern"); dateiAuswahlDialog.setMultiSelectionEnabled(false); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx")); dateiAuswahlDialog.setSelectedFile(new File("Export.xlsx"));
     if(dateiAuswahlDialog.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
         Path ziel = dateiAuswahlDialog.getSelectedFile().toPath(); if(!ziel.toString().toLowerCase().endsWith(".xlsx")) ziel=ziel.resolveSibling(ziel.getFileName()+".xlsx");
         final Path finalZiel = ziel; view.setStatus("Exportiere..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
         new SwingWorker<Void,Void>(){ boolean ok=false; String err=null; @Override protected Void doInBackground(){try{model.exportiereAlleNachExcel(finalZiel); ok=true;} catch(Exception ex){err=ex.getMessage();log.error("Excel Export Fehler",ex);} finally{SwingUtilities.invokeLater(()->{view.setProgressBarValue(100); new Timer(1000,ae->view.setProgressBarVisible(false)).start();});} return null;} @Override protected void done(){if(ok)JOptionPane.showMessageDialog(view,"Export erfolgreich:\n"+finalZiel,"Erfolg",1); else JOptionPane.showMessageDialog(view,"Export fehlgeschlagen.\n"+err,"Fehler",0); view.setStatus(ok?"Export fertig.":"Fehler Export.");}}.execute();
     } else { log.info("Export abgebrochen."); view.setStatus("Export abgebrochen."); }
 }

 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
              model.setAusgewaehltesDokument(selectedDoc); // Löst Event aus, das View updated
              // Das SELECTED_DOCUMENT Event im MainFrame löst jetzt den Aufruf unten aus
              // refreshInvoiceTypePanelForCurrentSelection(); // Nicht mehr hier direkt
          }
     }
 }

 private void handleTabelleComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
         ExtrahierteTabelle selectedTable = (ExtrahierteTabelle) view.getTabelleComboBox().getSelectedItem();
         if (selectedTable != null && !Objects.equals(model.getAusgewaehlteTabelle(), selectedTable)) {
              log.info("Tabellen ComboBox Auswahl geändert zu: {}", selectedTable);
              model.setAusgewaehlteTabelle(selectedTable); // Löst Event für Tabellenanzeige aus
          }
     }
 }

 // Kein triggerReprocessing mehr hier
 private void handleParameterChange(AWTEvent e) { log.debug("Parameter geändert. Klicke 'Neu verarbeiten'."); if(view!=null) view.setStatus("Parameter geändert. Klicken Sie 'Neu verarbeiten'.");}
 private void handleParameterChange(ChangeEvent e) { handleParameterChange((AWTEvent)null); }

 private void handleOpenConfigEditor(ActionEvent e) {
      log.info("Öffne Bereichs-Konfigurationseditor (Menü)..."); if (view == null) { log.error("View ist null"); return; }
      ExtractionConfiguration configToEdit = model.getAktiveKonfiguration(); ExtractionConfiguration configForDialog;
      if (configToEdit != null) { configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName()); if (configForDialog == null) { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); } }
      else { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); }
      ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);
      dialog.setVisible(true);
      ExtractionConfiguration savedConfig = dialog.getSavedConfiguration();
      if (savedConfig != null) { log.info("Bereichs-Konfiguration '{}' gespeichert.", savedConfig.getName()); updateAvailableConfigsInView(); model.setAktiveKonfiguration(savedConfig); /* KEIN triggerReprocessing */; view.setStatus("Konfig '" + savedConfig.getName() + "' gespeichert/aktiviert. Ggf. 'Neu verarbeiten'.");}
      else { log.info("Bereichs-Konfig geschlossen ohne Speichern."); updateAvailableConfigsInView();}
 }

  private void handleConfigSelectionChange(ItemEvent e) {
     if (e.getStateChange() == ItemEvent.SELECTED) {
         Object selectedItem = e.getItem(); ExtractionConfiguration selectedConfig = null;
         if (selectedItem instanceof ExtractionConfiguration) { selectedConfig = (ExtractionConfiguration) selectedItem; }
         else if (!"Keine".equals(selectedItem)) { log.warn("Unerwartetes Item: {}", selectedItem); return; }
         log.info("Bereichs-Konfig Auswahl geändert zu: {}", (selectedConfig != null ? selectedConfig.getName() : "Keine"));
         if (!Objects.equals(model.getAktiveKonfiguration(), selectedConfig)) {
              model.setAktiveKonfiguration(selectedConfig); // Löst Event für GUI Combo Update aus
              // triggerReprocessing("Bereichs-Konfiguration geändert"); // KEINE Neuverarbeitung mehr hier
              view.setStatus("Aktive Bereichs-Konfig auf '" + (selectedConfig != null ? selectedConfig.getName() : "Keine") + "' geändert. Klicken Sie 'Neu verarbeiten'.");
         }
     }
 }

 private void handleEditCsv(ActionEvent e) { log.info("Edit CSV Button geklickt."); model.getInvoiceTypeService().openCsvInEditor(); }
 // Refresh Button löst jetzt die Neuverarbeitung aus
 private void handleRefreshAction(ActionEvent e) { triggerReprocessing("Refresh Button"); }


 // --- Hilfsmethoden ---

 /** Löst Neuverarbeitung des aktuellen PDFs aus. */
 private void triggerReprocessing(String grund) {
      if (view == null) { log.error("View ist null in triggerReprocessing"); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
          Path pdfPath = null;
          try { pdfPath = Paths.get(selectedDoc.getFullPath()); }
          catch (Exception pathEx) { log.error("Ungültiger Pfad: {}", selectedDoc.getFullPath(), pathEx); view.setStatus("Fehler: Ungültiger Pfad."); return; }

          if (pdfPath != null) {
              Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();
              // Hole die explizit VOM USER ausgewählte Bereichs-Konfig (kann null sein)
              ExtractionConfiguration aktiveBereichsKonfig = model.getAktiveKonfiguration();
              // Hole den zuletzt erkannten Rechnungstyp (nur für Logging)
              InvoiceTypeConfig typConfig = selectedDoc.getDetectedInvoiceType() != null ? selectedDoc.getDetectedInvoiceType() : model.getInvoiceTypeService().getDefaultConfig();

              log.info("({}) Starte Neuverarbeitung für PDF: {} mit GUI-Parametern: {}, InvoiceType: {}, Bereichs-Konfig: {}",
                       grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, typConfig.getKeyword(), (aktiveBereichsKonfig != null ? aktiveBereichsKonfig.getName() : "Keine"));
              view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);

              model.ladeUndVerarbeitePdfsMitKonfiguration(
                  Collections.singletonList(pdfPath),
                  aktuelleParameterGui,
                  aktiveBereichsKonfig, // Übergib die aktuell ausgewählte
                  processedDoc -> { /* Status Callback */ if(processedDoc!=null) SwingUtilities.invokeLater(()->view.setStatus("Neu verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));},
                  progress -> { /* Progress Callback */ SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(progress*100)); if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}}); }
              );
          }
      } else { log.debug("Kein PDF ausgewählt für Neuverarbeitung."); view.setStatus("Bitte zuerst ein PDF auswählen."); }
 }

 /** Liest Parameter (Flavor, RowTol) aus GUI. */
 private Map<String, String> getCurrentParametersFromGui() {
      if (view == null) return Collections.emptyMap();
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
      if (view == null) { log.error("View ist null in updateAvailableConfigsInView"); return; }
      log.debug("Aktualisiere Bereichs-Konfigurations-ComboBox in der View...");
      List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations();
      ExtractionConfiguration activeConfig = model.getAktiveKonfiguration();
      view.updateConfigurationComboBox(configs, activeConfig);
  }

  /**
   * Aktualisiert das Invoice Type Panel in der GUI basierend auf dem Typ,
   * der im ausgewählten PdfDokument-Objekt gespeichert ist.
   */
  public void refreshInvoiceTypePanelForCurrentSelection() {
      if (view == null) { log.error("View ist null..."); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      log.debug("Aktualisiere InvoiceTypePanel für: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));

      InvoiceTypeConfig configToShow = null;
      if (selectedDoc != null) {
          // Hole den Typ direkt aus dem Dokumentobjekt (wurde bei der Verarbeitung gesetzt)
          configToShow = selectedDoc.getDetectedInvoiceType();
          if(configToShow == null) {
               // Fallback, falls es aus irgendeinem Grund null ist (sollte nicht sein)
               log.warn("DetectedInvoiceType im ausgewählten Dokument ist null! Verwende Default.");
               configToShow = model.getInvoiceTypeService().getDefaultConfig();
          }
      } else {
           log.debug("Kein Dokument ausgewählt, InvoiceTypePanel wird geleert.");
      }

      // Rufe View-Update auf
      view.updateInvoiceTypeDisplay(configToShow);
      // Setze Refresh-Button Status
      view.setRefreshButtonEnabled(selectedDoc != null);
  }
}