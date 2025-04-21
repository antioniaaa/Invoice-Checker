package de.anton.invoice.cecker.invoice_checker.controller;


import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeConfig;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeService;
import de.anton.invoice.cecker.invoice_checker.model.PdfDokument;
import de.anton.invoice.cecker.invoice_checker.view.MainFrame;
import de.anton.invoice.cecker.invoice_checker.model.ExtractionConfiguration;
import de.anton.invoice.cecker.invoice_checker.view.ConfigurationDialog; // Import für Dialog
import de.anton.invoice.cecker.invoice_checker.view.InvoiceTypeCrudPanel;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.io.IOException;
import java.nio.file.Files;
//PDFBox für Keyword Suche
import org.apache.pdfbox.pdmodel.PDDocument;


import javax.swing.event.ChangeListener;
import java.awt.event.ItemListener;
import java.awt.AWTEvent; // Für gemeinsamen Parameter-Handler
import java.util.Comparator;
import java.util.stream.Collectors;

import java.util.concurrent.ExecutionException; // Für SwingWorker Fehler

import javax.swing.event.ListSelectionEvent; // NEU für JList
import javax.swing.event.ListSelectionListener; // NEU für JList

/**
* Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
* Er reagiert auf Benutzeraktionen. Die Neuverarbeitung wird nur durch den Refresh-Button ausgelöst.
* Enthält Logik zum Verwalten der Invoice-Type-Konfiguration über das CRUD-Panel.
*/
public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private final MainFrame view;
 private JFileChooser dateiAuswahlDialog;
 // lastDetectedInvoiceType wird nicht mehr benötigt, da CRUD-Panel eigene Auswahl hat

 /**
  * Konstruktor. Initialisiert Modell und View und registriert die Listener.
  */
 public AppController(AnwendungsModell model, MainFrame view) {
     this.model = model;
     this.view = view;
     if (this.view == null || this.model == null) throw new IllegalArgumentException("Modell/View dürfen nicht null sein!");
     initializeListeners();
 }

 /**
  * Registriert alle notwendigen Listener bei den GUI-Komponenten der View.
  */
 private void initializeListeners() {
      log.info("Initialisiere Controller-Listener für View...");
      if (view == null) { log.error("View ist null!"); return; }

      // Tab 1
      view.addLadeButtonAbrechnungListener(this::handleLadeAbrechnungAction);

      // Tab 2 - Hauptaktionen
      view.addLadeButtonDetailsListener(this::handleLadePdfAktion);
      view.addExportButtonListener(this::handleExportExcelAktion);
      view.addRefreshButtonListener(this::handleRefreshAction);

      // Tab 2 - Auswahl-ComboBoxen
      view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
      view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
      view.addConfigSelectionListener(this::handleConfigSelectionChange); // Bereichs-Konfig

      // Tab 2 - Manuelle Parameter (lösen keine Aktion mehr aus)
      view.addFlavorComboBoxListener(this::handleFlavorChange);
      view.addRowToleranceSpinnerListener(this::handleRowTolChange);

      // Tab 2 - CRUD Panel für Invoice Types
      view.addCrudListSelectionListener(this::handleCrudListSelection); // NEU
      view.addCrudNewButtonListener(this::handleCrudNewAction);         // NEU
      view.addCrudSaveButtonListener(this::handleCrudSaveAction);       // NEU
      view.addCrudDeleteButtonListener(this::handleCrudDeleteAction);     // NEU
      view.addCrudEditCsvButtonListener(this::handleEditCsv);            // Umbenannt aus MainFrame

      // Menü
      view.addConfigMenuOpenListener(this::handleOpenConfigEditor);

      // Initialisierung
      setupDateiAuswahlDialog();
      updateAvailableConfigsInView(); // Lade Bereichs-Konfigs
      updateAvailableInvoiceTypesInView(); // NEU: Lade Invoice Typen
      log.info("Controller-Listener erfolgreich initialisiert.");
 }

 private void setupDateiAuswahlDialog() {
     dateiAuswahlDialog = new JFileChooser();
     dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
     dateiAuswahlDialog.setAcceptAllFileFilterUsed(false);
 }

 // --- Event Handler ---

 private void handleLadeAbrechnungAction(ActionEvent e) {
     log.info("Lade Button im Abrechnungs-Tab geklickt."); if (view == null) return;
     view.logMessage("Abrechnungs-Ladefunktion noch nicht implementiert.");
     JOptionPane.showMessageDialog(view, "Diese Funktion ist noch nicht implementiert.", "Platzhalter", JOptionPane.INFORMATION_MESSAGE);
 }

 private void handleLadePdfAktion(ActionEvent e) {
      log.info("Lade PDF(s) Button (Details) geklickt."); if (view == null) return;
      view.logMessage("Öffne Dateiauswahl zum Laden für Detailansicht...");
      dateiAuswahlDialog.setDialogTitle("PDF(s) für Detailansicht auswählen"); dateiAuswahlDialog.setMultiSelectionEnabled(true); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
      int ret = dateiAuswahlDialog.showOpenDialog(view);
      if (ret == JFileChooser.APPROVE_OPTION) {
          File[] files = dateiAuswahlDialog.getSelectedFiles();
          if (files != null && files.length > 0) {
              List<Path> pdfPaths = new ArrayList<>(); for(File f : files) pdfPaths.add(f.toPath());
              log.info("Ausgewählte Dateien: {}", Arrays.toString(files)); view.logMessage("Verarbeite " + pdfPaths.size() + " PDF(s) für Detailansicht...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);
              Map<String, String> pGui = getCurrentParametersFromGui();
              model.ladeUndVerarbeitePdfs(pdfPaths, pGui,
                 pDoc -> { if(pDoc!=null) SwingUtilities.invokeLater(()->view.logMessage("Verarbeitet:"+pDoc.getSourcePdf()+(pDoc.getError()!=null?" [F]":""))); },
                 prog -> { SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(prog*100)); if(prog>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}}); }
              );
          } else { log.warn("Keine Dateien ausgewählt."); view.logMessage("Keine Dateien ausgewählt.");}
      } else { log.info("Dateiauswahl abgebrochen."); view.logMessage("Dateiauswahl abgebrochen.");}
 }

 private void handleExportExcelAktion(ActionEvent e) {
     log.info("Export nach Excel Button geklickt."); if (view == null) return;
     dateiAuswahlDialog.setDialogTitle("Excel speichern"); dateiAuswahlDialog.setMultiSelectionEnabled(false); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx")); dateiAuswahlDialog.setSelectedFile(new File("Export.xlsx"));
     if(dateiAuswahlDialog.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
         Path ziel = dateiAuswahlDialog.getSelectedFile().toPath(); if(!ziel.toString().toLowerCase().endsWith(".xlsx")) ziel=ziel.resolveSibling(ziel.getFileName()+".xlsx");
         final Path finalZiel = ziel; view.logMessage("Exportiere..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
         new SwingWorker<Void,Void>(){ boolean ok=false; String err=null; @Override protected Void doInBackground(){try{model.exportiereAlleNachExcel(finalZiel); ok=true;} catch(Exception ex){err=ex.getMessage();log.error("Excel Fehler",ex);} finally{SwingUtilities.invokeLater(()->{view.setProgressBarValue(100); new Timer(1000,ae->view.setProgressBarVisible(false)).start();});} return null;} @Override protected void done(){if(ok)JOptionPane.showMessageDialog(view,"Export erfolgreich:\n"+finalZiel,"Erfolg",1); else JOptionPane.showMessageDialog(view,"Export fehlgeschlagen.\n"+err,"Fehler",0); view.logMessage(ok?"Export fertig.":"Fehler Export.");}}.execute();
     } else { log.info("Export abgebrochen."); view.logMessage("Export abgebrochen."); }
 }

 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
              model.setAusgewaehltesDokument(selectedDoc); // Löst Events aus (Tabellen-Combo, Tabelle, InvoicePanel)
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

 private void handleFlavorChange(ActionEvent e) { if(view!=null) view.logMessage("Flavor geändert. Klicke 'Neu verarbeiten' oder 'Speichern (CSV)'."); }
 private void handleRowTolChange(ChangeEvent e) { if(view!=null && e.getSource()==view.getRowToleranceSpinner()) view.logMessage("Row Tol geändert. Klicke 'Neu verarbeiten' oder 'Speichern (CSV)'."); }
 private void handleOpenConfigEditor(ActionEvent e) { log.info("Öffne Bereichs-Konfig-Editor..."); if (view == null) return; ExtractionConfiguration cfg=model.getAktiveKonfiguration(); ExtractionConfiguration cfgDlg; if(cfg!=null){cfgDlg=model.getConfigurationService().loadConfiguration(cfg.getName()); if(cfgDlg==null)cfgDlg=new ExtractionConfiguration("Neu");}else cfgDlg=new ExtractionConfiguration("Neu"); ConfigurationDialog dlg=new ConfigurationDialog(view,model.getConfigurationService(),cfgDlg); dlg.setVisible(true); ExtractionConfiguration saved=dlg.getSavedConfiguration(); if(saved!=null){log.info("Bereichs-Konfig '{}' gespeichert.",saved.getName()); updateAvailableConfigsInView(); model.setAktiveKonfiguration(saved); view.logMessage("Konfig '"+saved.getName()+"' gespeichert/aktiviert. Ggf. 'Neu verarbeiten'.");} else {log.info("Dialog geschlossen ohne Speichern.");updateAvailableConfigsInView();}}
 private void handleConfigSelectionChange(ItemEvent e) { if(e.getStateChange()==ItemEvent.SELECTED){ Object item=e.getItem(); ExtractionConfiguration cfg=null; if(item instanceof ExtractionConfiguration) cfg=(ExtractionConfiguration)item; else if(!"Keine".equals(item)) return; log.info("Bereichs-Konfig Auswahl: {}", (cfg!=null?cfg.getName():"Keine")); if(!Objects.equals(model.getAktiveKonfiguration(),cfg)){model.setAktiveKonfiguration(cfg); view.logMessage("Bereichs-Konfig auf '"+(cfg!=null?cfg.getName():"Keine")+"' gesetzt. Ggf. 'Neu verarbeiten'.");}}}
 private void handleEditCsv(ActionEvent e) { log.info("Edit CSV geklickt."); model.getInvoiceTypeService().openCsvInEditor(); }
 private void handleRefreshAction(ActionEvent e) { triggerReprocessing("Refresh Button"); }

 // --- NEUE Handler für CRUD Panel ---

 /** Reagiert auf Auswahl in der JList des CRUD-Panels. */
 private void handleCrudListSelection(ListSelectionEvent e) {
     if (!e.getValueIsAdjusting() && view != null) { // Nur auf finales Event reagieren
          InvoiceTypeConfig selectedConfig = view.getInvoiceTypeCrudPanel().getSelectedConfig();
          log.debug("CRUD Liste Auswahl geändert auf: {}", selectedConfig);
          view.getInvoiceTypeCrudPanel().displayConfig(selectedConfig); // Zeige Details im Formular an
     }
 }

 /** Bereitet das CRUD-Formular für einen neuen Eintrag vor. */
 private void handleCrudNewAction(ActionEvent e) {
     log.info("CRUD Neu Button geklickt.");
     if (view != null) {
         view.getInvoiceTypeCrudPanel().prepareNewEntry();
     }
 }

 /** Speichert die Daten aus dem CRUD-Formular (neuer Eintrag oder Update). */
 private void handleCrudSaveAction(ActionEvent e) {
     log.info("CRUD Speichern Button geklickt.");
     if (view == null) return;

     InvoiceTypeCrudPanel crudPanel = view.getInvoiceTypeCrudPanel();
     InvoiceTypeConfig configFromForm = crudPanel.getConfigFromForm(); // Holt Daten aus Feldern

     if (configFromForm == null) {
         log.warn("Konnte keine gültigen Daten aus dem Formular lesen.");
         // Fehlermeldung wurde wahrscheinlich schon im Panel angezeigt
         return;
     }

     boolean success = false;
     if (crudPanel.isNewEntryMode()) {
         // Neuen Eintrag hinzufügen
          log.info("Versuche neuen InvoiceType zu speichern: {}", configFromForm.getIdentifyingKeyword());
         success = model.getInvoiceTypeService().addConfigToCsv(configFromForm);
         if (!success) {
              JOptionPane.showMessageDialog(view, "Fehler beim Hinzufügen des neuen Rechnungstyps.\nPrüfen Sie, ob das Keyword bereits existiert oder die Logs.", "Fehler", JOptionPane.ERROR_MESSAGE);
         }
     } else {
         // Bestehenden Eintrag aktualisieren
          log.info("Versuche InvoiceType zu aktualisieren: {}", configFromForm.getIdentifyingKeyword());
          success = model.getInvoiceTypeService().updateConfigInCsv(configFromForm);
          if (!success) {
               JOptionPane.showMessageDialog(view, "Fehler beim Aktualisieren des Rechnungstyps.\nPrüfen Sie die Logs.", "Fehler", JOptionPane.ERROR_MESSAGE);
          }
     }

     // Wenn Speichern erfolgreich war, Liste aktualisieren und Auswahl wiederherstellen/setzen
     if (success) {
          view.logMessage("Rechnungstyp '" + configFromForm.getIdentifyingKeyword() + "' in CSV gespeichert.");
          updateAvailableInvoiceTypesInView(); // Lade Liste neu
          crudPanel.setSelectedConfig(configFromForm.getIdentifyingKeyword()); // Versuche, den Eintrag wieder auszuwählen
     }
 }

 /** Löscht den im CRUD-Panel ausgewählten Eintrag. */
 private void handleCrudDeleteAction(ActionEvent e) {
     log.info("CRUD Löschen Button geklickt.");
     if (view == null) return;

     InvoiceTypeCrudPanel crudPanel = view.getInvoiceTypeCrudPanel();
     InvoiceTypeConfig configToDelete = crudPanel.getSelectedConfig();

     if (configToDelete == null) {
         JOptionPane.showMessageDialog(view, "Bitte wählen Sie zuerst einen Eintrag aus der Liste aus, der gelöscht werden soll.", "Keine Auswahl", JOptionPane.WARNING_MESSAGE);
         return;
     }
     if (InvoiceTypeService.DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(configToDelete.getIdentifyingKeyword())) {
          JOptionPane.showMessageDialog(view, "Der Default-Eintrag 'Others' kann nicht gelöscht werden.", "Aktion nicht erlaubt", JOptionPane.WARNING_MESSAGE);
          return;
     }

     int confirmation = JOptionPane.showConfirmDialog(view,
             "Möchten Sie den Rechnungstyp '" + configToDelete.getIdentifyingKeyword() + "' wirklich löschen?",
             "Löschen bestätigen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

     if (confirmation == JOptionPane.YES_OPTION) {
         log.info("Lösche InvoiceType: {}", configToDelete.getIdentifyingKeyword());
         boolean success = model.getInvoiceTypeService().deleteConfigFromCsv(configToDelete.getIdentifyingKeyword());
         if (success) {
             view.logMessage("Rechnungstyp '" + configToDelete.getIdentifyingKeyword() + "' aus CSV gelöscht.");
             updateAvailableInvoiceTypesInView(); // Lade Liste neu
             crudPanel.clearForm(); // Leere Formular nach Löschen
             crudPanel.setFormEnabled(false);
         } else {
              log.error("Fehler beim Löschen von '{}' aus CSV.", configToDelete.getIdentifyingKeyword());
              JOptionPane.showMessageDialog(view, "Fehler beim Löschen des Eintrags aus der CSV-Datei.\nPrüfen Sie die Logs.", "Löschen fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
         }
     }
 }


 // --- Hilfsmethoden ---

 /** Löst Neuverarbeitung des aktuellen PDFs aus. */
 private void triggerReprocessing(String grund) {
      if (view == null) { log.error("View ist null..."); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
          Path pdfPath = null; try { pdfPath = Paths.get(selectedDoc.getFullPath()); } catch (Exception pathEx) { log.error("Pfadfehler", pathEx); view.logMessage("Fehler: Ungültiger Pfad."); return; }
          if (pdfPath != null) {
              Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();
              ExtractionConfiguration aktiveBereichsKonfig = model.getAktiveKonfiguration();
              InvoiceTypeConfig typConfig = selectedDoc.getDetectedInvoiceType() != null ? selectedDoc.getDetectedInvoiceType() : model.getInvoiceTypeService().getDefaultConfig();
              log.info("({}) Neuverarbeitung: PDF={}, GUIParams={}, InvType={}, BereichsKonfig={}", grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, typConfig.getIdentifyingKeyword(), (aktiveBereichsKonfig != null ? aktiveBereichsKonfig.getName() : "Keine"));
              view.logMessage("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
              model.ladeUndVerarbeitePdfsMitKonfiguration( Collections.singletonList(pdfPath), aktuelleParameterGui, aktiveBereichsKonfig,
                  processedDoc -> { /* Status */ if(processedDoc!=null) SwingUtilities.invokeLater(()->view.logMessage("Neu verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));},
                  progress -> { /* Progress */ SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(progress*100)); if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}}); }
              );
          }
      } else { log.debug("Kein PDF ausgewählt."); view.logMessage("Bitte zuerst ein PDF auswählen."); }
 }

 /** Liest Parameter (Flavor, RowTol) aus GUI. */
 private Map<String, String> getCurrentParametersFromGui() {
      if (view == null) return Collections.emptyMap(); Map<String,String> p = new HashMap<>();
      try { p.put("flavor", (String)view.getFlavorComboBox().getSelectedItem()); Object v=view.getRowToleranceSpinner().getValue(); p.put("row_tol", String.valueOf(v instanceof Number ? ((Number)v).intValue() : "2")); }
      catch (Exception e) { log.error("Fehler GUI-Params", e); p.putIfAbsent("flavor", "lattice"); p.putIfAbsent("row_tol", "2"); } return p;
 }

  /** Lädt verfügbare Bereichs-Konfigurationen und aktualisiert die View. */
  private void updateAvailableConfigsInView() {
      if (view == null) { log.error("View ist null..."); return; } log.debug("Aktualisiere Bereichs-Konfig-ComboBox...");
      List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations(); ExtractionConfiguration activeConfig = model.getAktiveKonfiguration(); view.updateConfigurationComboBox(configs, activeConfig);
  }

  /** Lädt verfügbare InvoiceType-Konfigurationen und aktualisiert das CRUD-Panel. */
  private void updateAvailableInvoiceTypesInView() {
       if (view == null) { log.error("View ist null..."); return; }
       log.debug("Aktualisiere InvoiceType CRUD Panel Liste...");
       List<InvoiceTypeConfig> configs = model.getInvoiceTypeService().getAllConfigs();
       view.getInvoiceTypeCrudPanel().updateList(configs);
  }

  /** Aktualisiert das Invoice Type Panel basierend auf dem erkannten Typ im Dokument. */
  public void refreshInvoiceTypePanelForCurrentSelection() {
      if (view == null) { log.error("View ist null..."); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      log.debug("Aktualisiere InvoiceTypePanel Anzeige für: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
      InvoiceTypeConfig configToShow = null;
      if (selectedDoc != null) { configToShow = selectedDoc.getDetectedInvoiceType(); if(configToShow == null){ log.warn("DetectedInvoiceType in Doc ist null!"); configToShow = model.getInvoiceTypeService().getDefaultConfig(); } }
      else { log.debug("Kein Dokument ausgewählt."); }
      // Update CRUD Panel Anzeige (zeigt aktuell erkannten Typ oder leert Formular)
      view.getInvoiceTypeCrudPanel().displayConfig(configToShow);
      // Setze Refresh-Button Status
      view.setRefreshButtonEnabled(selectedDoc != null);
  }
}