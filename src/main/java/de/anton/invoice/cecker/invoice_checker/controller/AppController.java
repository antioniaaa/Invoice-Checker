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

/**
* Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
* Er reagiert auf Benutzeraktionen. Die Neuverarbeitung wird nur durch den Refresh-Button ausgelöst.
*/
public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private MainFrame view; // Wird in initializeListeners gesetzt
 private JFileChooser dateiAuswahlDialog;
 private InvoiceTypeConfig lastDetectedInvoiceType = null; // Merkt sich den zuletzt erkannten Typ

 /**
  * Konstruktor. Benötigt das Modell. Die View wird später gesetzt.
  * @param model Das Anwendungsmodell. Darf nicht null sein.
  */
 public AppController(AnwendungsModell model) {
     if (model == null) {
          throw new IllegalArgumentException("Modell darf im Controller nicht null sein!");
     }
     this.model = model;
     // View ist hier noch null, Listener werden später registriert
 }

 /**
  * Initialisiert die Listener für die GUI-Elemente in der übergebenen View.
  * Speichert die View-Referenz.
  * Wird von MainApp aufgerufen, nachdem Model und View erstellt wurden.
  * @param view Die MainFrame-Instanz. Darf nicht null sein.
  */
 public void initializeListeners(MainFrame view) {
      log.info("Initialisiere Controller-Listener für View...");
      if (view == null) {
          log.error("View ist null in initializeListeners! Listener können nicht registriert werden.");
          throw new IllegalArgumentException("View darf in initializeListeners nicht null sein!");
      }
      this.view = view; // Speichere die View-Referenz JETZT

      // Registriere alle Listener bei den Komponenten der View
      view.addLadeButtonListener(this::handleLadePdfAktion);
      view.addExportButtonListener(this::handleExportExcelAktion);
      view.addRefreshButtonListener(this::handleRefreshAction); // Bleibt aktiv
      view.addEditCsvButtonListener(this::handleEditCsv);
      view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
      view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
      // Listener für Parameter bleiben, lösen aber KEINE Neuverarbeitung mehr aus
      view.addFlavorComboBoxListener(this::handleFlavorChange);
      view.addRowToleranceSpinnerListener(this::handleRowTolChange);
      // Listener für Konfiguration
      view.addConfigMenuOpenListener(this::handleOpenConfigEditor);
      view.addConfigSelectionListener(this::handleConfigSelectionChange); // Löst KEINE Neuverarbeitung mehr aus

      setupDateiAuswahlDialog();
      updateAvailableConfigsInView(); // Lade Bereichs-Konfigs beim Start
      log.info("Controller-Listener erfolgreich initialisiert.");
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
     log.info("Export nach Excel Button geklickt.");
     if (view == null) { log.error("View ist null in handleExportExcelAktion"); return; }
     dateiAuswahlDialog.setDialogTitle("Excel speichern"); dateiAuswahlDialog.setMultiSelectionEnabled(false); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx")); dateiAuswahlDialog.setSelectedFile(new File("Export.xlsx"));
     if(dateiAuswahlDialog.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
         Path ziel = dateiAuswahlDialog.getSelectedFile().toPath(); if(!ziel.toString().toLowerCase().endsWith(".xlsx")) ziel=ziel.resolveSibling(ziel.getFileName()+".xlsx");
         final Path finalZiel = ziel; view.setStatus("Exportiere..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
         new SwingWorker<Void,Void>(){ boolean ok=false; String err=null; @Override protected Void doInBackground(){try{model.exportiereAlleNachExcel(finalZiel); ok=true;} catch(Exception ex){err=ex.getMessage();log.error("Excel Fehler",ex);} finally{SwingUtilities.invokeLater(()->{view.setProgressBarValue(100); new Timer(1000,ae->view.setProgressBarVisible(false)).start();});} return null;} @Override protected void done(){if(ok)JOptionPane.showMessageDialog(view,"Export erfolgreich:\n"+finalZiel,"Erfolg",1); else JOptionPane.showMessageDialog(view,"Export fehlgeschlagen.\n"+err,"Fehler",0); view.setStatus(ok?"Export fertig.":"Fehler Export.");}}.execute();
     } else { log.info("Export abgebrochen."); view.setStatus("Export abgebrochen."); }
 }

 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
              model.setAusgewaehltesDokument(selectedDoc);
              refreshInvoiceTypePanelForCurrentSelection();
          }
     }
 }

 private void handleTabelleComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
         ExtrahierteTabelle selectedTable = (ExtrahierteTabelle) view.getTabelleComboBox().getSelectedItem();
         if (selectedTable != null && !Objects.equals(model.getAusgewaehlteTabelle(), selectedTable)) {
              log.info("Tabellen ComboBox Auswahl geändert zu: {}", selectedTable);
              model.setAusgewaehlteTabelle(selectedTable);
          }
     }
 }

 /** Behandelt Änderung des Flavor-Parameters (ohne Neuverarbeitung). */
 private void handleFlavorChange(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          String selectedFlavor = (String) view.getFlavorComboBox().getSelectedItem();
          log.info("Flavor manuell geändert auf: {}", selectedFlavor);
          view.setStatus("Flavor auf '" + selectedFlavor + "' geändert. Klicken Sie 'Neu verarbeiten'.");
          // KEIN triggerReprocessing() HIER
     }
 }
 /** Behandelt Änderung des Row Tolerance-Parameters (ohne Neuverarbeitung). */
 private void handleRowTolChange(ChangeEvent e) {
     if (view != null) {
          // Prüfe, ob Quelle der JSpinner ist (um Endlos-Events beim Setzen zu vermeiden)
          if (e.getSource() == view.getRowToleranceSpinner()) {
              Object value = view.getRowToleranceSpinner().getValue();
              log.info("Row Tolerance manuell geändert auf: {}", value);
              view.setStatus("Row Tolerance auf '" + value + "' geändert. Klicken Sie 'Neu verarbeiten'.");
              // KEIN triggerReprocessing() HIER
          }
     }
 }

 /** Öffnet den Konfigurationseditor-Dialog für Bereichsdefinitionen. */
 private void handleOpenConfigEditor(ActionEvent e) {
      log.info("Öffne Bereichs-Konfigurationseditor (Menü)...");
      if (view == null) { log.error("View ist null in handleOpenConfigEditor"); return; }
      ExtractionConfiguration configToEdit = model.getAktiveKonfiguration(); ExtractionConfiguration configForDialog;
      if (configToEdit != null) { configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName()); if (configForDialog == null) { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); } }
      else { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); }
      ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);
      dialog.setVisible(true);
      ExtractionConfiguration savedConfig = dialog.getSavedConfiguration();
      if (savedConfig != null) {
          log.info("Bereichs-Konfiguration '{}' wurde gespeichert.", savedConfig.getName());
          updateAvailableConfigsInView(); // Liste neu laden
          model.setAktiveKonfiguration(savedConfig); // Als aktiv setzen (löst Event in View)
          // triggerReprocessing("Bereichs-Konfiguration gespeichert"); // KEINE Neuverarbeitung mehr hier
          view.setStatus("Konfiguration '" + savedConfig.getName() + "' gespeichert und aktiviert. Ggf. 'Neu verarbeiten' klicken.");
      } else { log.info("Bereichs-Konfigurationsdialog geschlossen ohne Speichern."); updateAvailableConfigsInView();}
 }

  /** Behandelt Änderung in der Bereichs-Konfigurations-ComboBox (ohne Neuverarbeitung). */
  private void handleConfigSelectionChange(ItemEvent e) {
     if (e.getStateChange() == ItemEvent.SELECTED) {
         Object selectedItem = e.getItem(); ExtractionConfiguration selectedConfig = null;
         if (selectedItem instanceof ExtractionConfiguration) { selectedConfig = (ExtractionConfiguration) selectedItem; }
         else if (!"Keine".equals(selectedItem)) { log.warn("Unerwartetes Item: {}", selectedItem); return; }

         log.info("Bereichs-Konfig Auswahl geändert zu: {}", (selectedConfig != null ? selectedConfig.getName() : "Keine"));
         if (!Objects.equals(model.getAktiveKonfiguration(), selectedConfig)) {
              model.setAktiveKonfiguration(selectedConfig); // Löst Event für GUI Combo Update aus
              // triggerReprocessing("Bereichs-Konfiguration geändert"); // KEINE Neuverarbeitung mehr hier
              view.setStatus("Aktive Bereichs-Konfiguration auf '" + (selectedConfig != null ? selectedConfig.getName() : "Keine") + "' geändert. Klicken Sie 'Neu verarbeiten'.");
         }
     }
 }

 /** Behandelt Klick auf "Edit CSV". */
 private void handleEditCsv(ActionEvent e) { log.info("Edit CSV Button geklickt."); model.getInvoiceTypeService().openCsvInEditor(); }

 /** Behandelt Klick auf "Neu verarbeiten". Löst die Verarbeitung aus. */
 private void handleRefreshAction(ActionEvent e) {
     log.info("Refresh Button geklickt.");
     triggerReprocessing("Refresh Button"); // Nur hier wird neu verarbeitet
 }


 // --- Hilfsmethoden ---

 /** Löst Neuverarbeitung aus. */
 private void triggerReprocessing(String grund) {
      if (view == null) { log.error("View ist null in triggerReprocessing"); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
          Path pdfPath = null;
          try { pdfPath = Paths.get(selectedDoc.getFullPath()); }
          catch (Exception pathEx) { log.error("Ungültiger Pfad: {}", selectedDoc.getFullPath(), pathEx); view.setStatus("Fehler: Ungültiger Pfad."); return; }

          if (pdfPath != null) {
              Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();
              ExtractionConfiguration aktiveBereichsKonfig = model.getAktiveKonfiguration(); // Holt die aktuell VOM USER ausgewählte
              InvoiceTypeConfig typConfig = this.lastDetectedInvoiceType != null ? this.lastDetectedInvoiceType : model.getInvoiceTypeService().getDefaultConfig(); // Nur für Info

              log.info("({}) Starte Neuverarbeitung für PDF: {} mit GUI-Parametern: {}, InvoiceType: {}, Aktive Bereichs-Konfig: {}",
                       grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, typConfig.getKeyword(), (aktiveBereichsKonfig != null ? aktiveBereichsKonfig.getName() : "Keine"));
              view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);

              model.ladeUndVerarbeitePdfsMitKonfiguration( // Ruft die Methode auf, die Konfig explizit nimmt
                  Collections.singletonList(pdfPath),
                  aktuelleParameterGui,
                  aktiveBereichsKonfig, // Übergib die aktuell ausgewählte (oder null)
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

  /** Aktualisiert das Invoice Type Panel in der GUI basierend auf PDF-Inhalt. */
  public void refreshInvoiceTypePanelForCurrentSelection() {
      if (view == null) { log.error("View ist null in refreshInvoiceTypePanelForCurrentSelection"); return; }
      this.lastDetectedInvoiceType = null;
      view.updateInvoiceTypeDisplay(null);
      view.setRefreshButtonEnabled(false);

      PdfDokument pdfDoc = model.getAusgewaehltesDokument();
      if (pdfDoc == null || pdfDoc.getFullPath() == null || pdfDoc.getFullPath().isBlank()) { log.debug("Kein gültiges Dokument für Keyword-Suche ausgewählt."); return; }
      final Path pdfPath;
      try { pdfPath = Paths.get(pdfDoc.getFullPath()); if (!Files.exists(pdfPath)) { log.error("PDF für Keyword nicht gefunden: {}", pdfPath); view.updateInvoiceTypeDisplay(null); view.setStatus("Fehler: PDF für Typ nicht gefunden."); return; } }
      catch (Exception e) { log.error("Ungültiger PDF-Pfad: {}", pdfDoc.getFullPath(), e); view.updateInvoiceTypeDisplay(null); view.setStatus("Fehler: Ungültiger PDF-Pfad."); return; }

      view.setStatus("Ermittle Rechnungstyp...");

      SwingWorker<InvoiceTypeConfig, Void> worker = new SwingWorker<>() {
          @Override protected InvoiceTypeConfig doInBackground() throws Exception {
               log.debug("Starte Keyword-Suche für {}", pdfDoc.getSourcePdf());
               PDDocument pdDoc = null;
               try { pdDoc = PDDocument.load(pdfPath.toFile()); return model.getInvoiceTypeService().findConfigForPdf(pdDoc); }
               finally { if (pdDoc != null) try { pdDoc.close(); } catch (IOException e) { log.error("Fehler Schließen Doc nach Keyword", e);} }
          }
          @Override protected void done() {
              try { InvoiceTypeConfig found = get(); lastDetectedInvoiceType = found; log.info("Keyword-Suche fertig. Typ: {}", (found != null ? found.getType() : "Default/Fehler")); view.updateInvoiceTypeDisplay(found); view.setRefreshButtonEnabled(true); view.setStatus("Bereit."); }
              catch (Exception e) { log.error("Fehler Abrufen Keyword-Ergebnis", e); lastDetectedInvoiceType=model.getInvoiceTypeService().getDefaultConfig(); view.updateInvoiceTypeDisplay(lastDetectedInvoiceType); view.setStatus("Fehler Typ-Erkennung."); view.setRefreshButtonEnabled(true); }
          }
      };
      worker.execute();
  }
}