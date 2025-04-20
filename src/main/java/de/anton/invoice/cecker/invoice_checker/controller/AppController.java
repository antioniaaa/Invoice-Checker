package de.anton.invoice.cecker.invoice_checker.controller;


import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeConfig;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeService;
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

/**
* Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
* Er reagiert auf Benutzeraktionen. Die Neuverarbeitung wird nur durch den Refresh-Button ausgelöst.
* Die View-Referenz wird nach der Instanziierung gesetzt.
*/
public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private MainFrame view; // Wird nach Instanziierung gesetzt
 private JFileChooser dateiAuswahlDialog;
 private InvoiceTypeConfig lastDetectedInvoiceType = null;

 /**
  * Konstruktor. Benötigt das Modell. Die View wird später gesetzt.
  * @param model Das Anwendungsmodell. Darf nicht null sein.
  */
 public AppController(AnwendungsModell model) {
     if (model == null) {
          throw new IllegalArgumentException("Modell darf im Controller nicht null sein!");
     }
     this.model = model;
     // View ist hier noch null, Listener werden später in setViewAndInitializeListeners registriert
 }

 /**
  * Setzt die View-Referenz und initialisiert die Controller-Logik (Listener etc.).
  * Sollte von MainApp aufgerufen werden, nachdem die View erstellt wurde.
  * @param view Die MainFrame-Instanz. Darf nicht null sein.
  */
 public void setViewAndInitializeListeners(MainFrame view) {
     log.info("Setze View und initialisiere Controller-Listener...");
     if (view == null) {
         log.error("Übergebene View ist null! Listener können nicht registriert werden.");
         throw new IllegalArgumentException("View darf in setViewAndInitializeListeners nicht null sein!");
     }
     if (this.view != null) {
          log.warn("View im Controller wird überschrieben! Alte View: {}, Neue View: {}", this.view, view);
     }
     this.view = view; // Speichere die View-Referenz

     // Registriere alle Listener bei den Komponenten der (jetzt bekannten) View
     initializeListeners();
 }


 /**
  * Registriert alle notwendigen Listener bei den GUI-Komponenten der View.
  * Wird von setViewAndInitializeListeners aufgerufen.
  */
 private void initializeListeners() {
      log.info("Initialisiere Controller-Listener für View...");
      if (view == null) {
          log.error("View ist null in initializeListeners!"); // Sollte nicht passieren nach setView...
          return;
      }
      // Listener registrieren
      view.addLadeButtonAbrechnungListener(this::handleLadeAbrechnungAction);
      view.addLadeButtonDetailsListener(this::handleLadePdfAktion);
      view.addExportButtonListener(this::handleExportExcelAktion);
      view.addRefreshButtonListener(this::handleRefreshAction);
      view.addEditCsvButtonListener(this::handleEditCsv);
      view.addUpdateCsvButtonListener(this::handleUpdateCsvAction);
      view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
      view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
      view.addFlavorComboBoxListener(this::handleFlavorChange);
      view.addRowToleranceSpinnerListener(this::handleRowTolChange);
      view.addConfigMenuOpenListener(this::handleOpenConfigEditor);
      view.addConfigSelectionListener(this::handleConfigSelectionChange);

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

 /** NEU: Handler für den Lade-Button im Tab "Abrechnungen" */
 private void handleLadeAbrechnungAction(ActionEvent e) {
     log.info("Lade Button im Abrechnungs-Tab geklickt.");
     if (view == null) return;
     view.logMessage("Abrechnungs-Ladefunktion noch nicht implementiert.");
     JOptionPane.showMessageDialog(view, "Diese Ladefunktion ist noch nicht implementiert.", "Platzhalter", JOptionPane.INFORMATION_MESSAGE);
 }

 /** Handler für den Lade-Button im Tab "Konfiguration & Details" */
 private void handleLadePdfAktion(ActionEvent e) {
      log.info("Lade PDF(s) Button (Details) geklickt.");
      if (view == null) { log.error("View ist null..."); return; }
      view.logMessage("Öffne Dateiauswahl zum Laden für Detailansicht...");
      dateiAuswahlDialog.setDialogTitle("PDF(s) für Detailansicht auswählen"); dateiAuswahlDialog.setMultiSelectionEnabled(true); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
      int ret = dateiAuswahlDialog.showOpenDialog(view);
      if (ret == JFileChooser.APPROVE_OPTION) {
          File[] files = dateiAuswahlDialog.getSelectedFiles();
          if (files != null && files.length > 0) {
              List<Path> pdfPaths = new ArrayList<>(); for(File f : files) pdfPaths.add(f.toPath());
              log.info("Ausgewählte Dateien: {}", Arrays.toString(files));
              view.logMessage("Verarbeite " + pdfPaths.size() + " PDF(s) für Detailansicht...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);
              Map<String, String> parameterGui = getCurrentParametersFromGui();
              model.ladeUndVerarbeitePdfs(pdfPaths, parameterGui,
                 processedDoc -> { /* Status */ if(processedDoc!=null) SwingUtilities.invokeLater(()->view.logMessage("Verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":""))); },
                 progress -> { /* Progress */ SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(progress*100)); if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}}); }
              );
          } else { log.warn("Keine Dateien ausgewählt."); view.logMessage("Keine Dateien ausgewählt.");}
      } else { log.info("Dateiauswahl abgebrochen."); view.logMessage("Dateiauswahl abgebrochen.");}
 }

 /** Behandelt den Klick auf den "Nach Excel exportieren"-Button. */
 private void handleExportExcelAktion(ActionEvent e) {
     log.info("Export nach Excel Button geklickt.");
     if (view == null) { log.error("View ist null..."); return; }
     dateiAuswahlDialog.setDialogTitle("Excel speichern"); dateiAuswahlDialog.setMultiSelectionEnabled(false); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel (*.xlsx)", "xlsx")); dateiAuswahlDialog.setSelectedFile(new File("Export.xlsx"));
     if(dateiAuswahlDialog.showSaveDialog(view) == JFileChooser.APPROVE_OPTION) {
         Path ziel = dateiAuswahlDialog.getSelectedFile().toPath(); if(!ziel.toString().toLowerCase().endsWith(".xlsx")) ziel=ziel.resolveSibling(ziel.getFileName()+".xlsx");
         final Path finalZiel = ziel; view.logMessage("Exportiere nach " + finalZiel.getFileName() + "..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
         new SwingWorker<Void,Void>(){ boolean ok=false; String err=null; @Override protected Void doInBackground(){try{model.exportiereAlleNachExcel(finalZiel); ok=true;} catch(Exception ex){err=ex.getMessage();log.error("Excel Export Fehler",ex);} finally{SwingUtilities.invokeLater(()->{view.setProgressBarValue(100); new Timer(1000,ae->view.setProgressBarVisible(false)).start();});} return null;} @Override protected void done(){if(ok)JOptionPane.showMessageDialog(view,"Export erfolgreich:\n"+finalZiel,"Erfolg",1); else JOptionPane.showMessageDialog(view,"Export fehlgeschlagen.\n"+err,"Fehler",0); view.logMessage(ok?"Export fertig.":"Export fehlgeschlagen.");}}.execute();
     } else { log.info("Export abgebrochen."); view.logMessage("Export abgebrochen."); }
 }

 /** Behandelt eine Änderung in der PDF-Auswahl-ComboBox. */
 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
              model.setAusgewaehltesDokument(selectedDoc); // Löst Events aus
              // Der Aufruf zum Aktualisieren des Invoice Panels erfolgt jetzt im PropertyChangeListener der View
          }
     }
 }

 /** Behandelt eine Änderung in der Tabellen-Auswahl-ComboBox. */
 private void handleTabelleComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
         ExtrahierteTabelle selectedTable = (ExtrahierteTabelle) view.getTabelleComboBox().getSelectedItem();
         if (selectedTable != null && !Objects.equals(model.getAusgewaehlteTabelle(), selectedTable)) {
              log.info("Tabellen ComboBox Auswahl geändert zu: {}", selectedTable);
              model.setAusgewaehlteTabelle(selectedTable); // Löst Event für Tabellenanzeige aus
          }
     }
 }

 /** Behandelt Änderung des Flavor-Parameters (ohne Neuverarbeitung). */
 private void handleFlavorChange(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          String selectedFlavor = (String) view.getFlavorComboBox().getSelectedItem();
          log.info("Flavor manuell geändert auf: {}", selectedFlavor);
          view.logMessage("Flavor auf '" + selectedFlavor + "' geändert. Klicken Sie 'Neu verarbeiten'.");
     }
 }
 /** Behandelt Änderung des Row Tolerance-Parameters (ohne Neuverarbeitung). */
 private void handleRowTolChange(ChangeEvent e) {
     if (view != null && e.getSource() == view.getRowToleranceSpinner()) {
          Object value = view.getRowToleranceSpinner().getValue();
          log.info("Row Tolerance manuell geändert auf: {}", value);
          view.logMessage("Row Tolerance auf '" + value + "' geändert. Klicken Sie 'Neu verarbeiten'.");
     }
 }

 /** Öffnet den Konfigurationseditor-Dialog für Bereichsdefinitionen. */
 private void handleOpenConfigEditor(ActionEvent e) {
      log.info("Öffne Bereichs-Konfigurationseditor (Menü)...");
      if (view == null) { log.error("View ist null..."); return; }
      ExtractionConfiguration configToEdit = model.getAktiveKonfiguration(); ExtractionConfiguration configForDialog;
      if (configToEdit != null) { configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName()); if (configForDialog == null) { log.warn("Konnte Konfig '{}' nicht laden.", configToEdit.getName()); configForDialog = new ExtractionConfiguration("Neue Konfiguration"); } }
      else { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); }
      ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);
      dialog.setVisible(true);
      ExtractionConfiguration savedConfig = dialog.getSavedConfiguration();
      if (savedConfig != null) {
          log.info("Bereichs-Konfiguration '{}' gespeichert.", savedConfig.getName());
          updateAvailableConfigsInView(); // Liste neu laden
          model.setAktiveKonfiguration(savedConfig); // Als aktiv setzen
          view.logMessage("Konfiguration '" + savedConfig.getName() + "' gespeichert/aktiviert. Ggf. 'Neu verarbeiten'.");
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
              view.logMessage("Aktive Bereichs-Konfiguration auf '" + (selectedConfig != null ? selectedConfig.getName() : "Keine") + "' geändert. Klicken Sie 'Neu verarbeiten'.");
         }
     }
 }

 /** Behandelt den Klick auf den "Typdefinitionen (CSV)..."-Button. */
 private void handleEditCsv(ActionEvent e) { log.info("Edit CSV Button geklickt."); model.getInvoiceTypeService().openCsvInEditor(); }

 /** Behandelt den Klick auf den "Neu verarbeiten"-Button. Löst die Verarbeitung aus. */
 private void handleRefreshAction(ActionEvent e) {
     log.info("Refresh Button geklickt.");
     triggerReprocessing("Refresh Button"); // Nur hier wird neu verarbeitet
 }

 /** Behandelt den Klick auf den "Akt. Parameter für Typ speichern"-Button. */
 private void handleUpdateCsvAction(ActionEvent e) {
     log.info("Update CSV Button geklickt.");
     log.debug("--> Aktueller Wert von lastDetectedInvoiceType: {}", this.lastDetectedInvoiceType); // Log hinzufügen!
     if (view == null) { log.error("View ist null..."); return; }

     InvoiceTypeConfig configToUpdate = this.lastDetectedInvoiceType;
     if (configToUpdate == null || InvoiceTypeService.DEFAULT_KEYWORD.equalsIgnoreCase(configToUpdate.getKeyword())) {
         log.warn("Kein spezifischer Typ zum Aktualisieren oder 'Others'.");
         JOptionPane.showMessageDialog(view, "Es muss ein spezifischer Rechnungstyp (nicht 'Others') erkannt sein,\num dessen Werte in der CSV zu aktualisieren.", "Aktion nicht möglich", JOptionPane.WARNING_MESSAGE);
         return;
     }
     String keyword = configToUpdate.getKeyword();

     Map<String, String> params = getCurrentParametersFromGui();
     String newFlavor = params.get("flavor"); String newRowTol = params.get("row_tol");
     Object selectedConfigItem = view.getConfigComboBox().getSelectedItem();
     String newAreaType = "Keine";
     if (selectedConfigItem instanceof ExtractionConfiguration) newAreaType = ((ExtractionConfiguration) selectedConfigItem).getName();
     else if (selectedConfigItem != null && !"Keine".equals(selectedConfigItem.toString())) newAreaType = configToUpdate.getAreaType(); // Behalte alten Wert

     log.debug("Werte für CSV-Update: K='{}', Area='{}', Fl='{}', Tol='{}'", keyword, newAreaType, newFlavor, newRowTol);
     boolean success = model.getInvoiceTypeService().updateConfigInCsv(keyword, newAreaType, newFlavor, newRowTol);

     if (success) {
         log.info("CSV aktualisiert für '{}'.", keyword);
         JOptionPane.showMessageDialog(view, "Standardwerte für Typ '" + keyword + "' in CSV aktualisiert.", "Update erfolgreich", JOptionPane.INFORMATION_MESSAGE);
         refreshInvoiceTypePanelForCurrentSelection(); // Anzeige aktualisieren
     } else { log.error("Fehler Update CSV für '{}'.", keyword); JOptionPane.showMessageDialog(view, "Fehler beim Aktualisieren der CSV-Datei.", "Update fehlgeschlagen", JOptionPane.ERROR_MESSAGE); }
 }

 // --- Hilfsmethoden ---

 /** Löst Neuverarbeitung des aktuellen PDFs aus. */
 private void triggerReprocessing(String grund) {
      if (view == null) { log.error("View ist null..."); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
          Path pdfPath = null; try { pdfPath = Paths.get(selectedDoc.getFullPath()); } catch (Exception pathEx) { log.error("Ungültiger Pfad: {}", selectedDoc.getFullPath(), pathEx); view.logMessage("Fehler: Ungültiger Pfad."); return; }
          if (pdfPath != null) {
              Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();
              ExtractionConfiguration aktiveBereichsKonfig = model.getAktiveKonfiguration();
              InvoiceTypeConfig typConfig = selectedDoc.getDetectedInvoiceType() != null ? selectedDoc.getDetectedInvoiceType() : model.getInvoiceTypeService().getDefaultConfig();
              log.info("({}) Starte Neuverarbeitung: PDF={}, GUIParams={}, InvType={}, BereichsKonfig={}", grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, typConfig.getKeyword(), (aktiveBereichsKonfig != null ? aktiveBereichsKonfig.getName() : "Keine"));
              view.logMessage("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
              model.ladeUndVerarbeitePdfsMitKonfiguration( Collections.singletonList(pdfPath), aktuelleParameterGui, aktiveBereichsKonfig,
                  processedDoc -> { /* Status */ if(processedDoc!=null) SwingUtilities.invokeLater(()->view.logMessage("Neu verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));},
                  progress -> { /* Progress */ SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(progress*100)); if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}}); }
              );
          }
      } else { log.debug("Kein PDF ausgewählt für Neuverarbeitung."); view.logMessage("Bitte zuerst ein PDF auswählen."); }
 }

 /** Liest Parameter (Flavor, RowTol) aus GUI. */
 private Map<String, String> getCurrentParametersFromGui() {
      if (view == null) return Collections.emptyMap();
      Map<String,String> p = new HashMap<>();
      try { p.put("flavor", (String)view.getFlavorComboBox().getSelectedItem()); Object v=view.getRowToleranceSpinner().getValue(); p.put("row_tol", String.valueOf(v instanceof Number ? ((Number)v).intValue() : "2")); }
      catch (Exception e) { log.error("Fehler GUI-Parameter Lesen", e); p.putIfAbsent("flavor", "lattice"); p.putIfAbsent("row_tol", "2"); }
      return p;
 }

  /** Lädt verfügbare Bereichs-Konfigurationen und aktualisiert die View. */
  private void updateAvailableConfigsInView() {
      if (view == null) { log.error("View ist null..."); return; }
      log.debug("Aktualisiere Bereichs-Konfigurations-ComboBox...");
      List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations();
      ExtractionConfiguration activeConfig = model.getAktiveKonfiguration();
      view.updateConfigurationComboBox(configs, activeConfig);
  }

  /** Aktualisiert das Invoice Type Panel (startet Hintergrundsuche). */
  public void refreshInvoiceTypePanelForCurrentSelection() {
      if (view == null) { log.error("View ist null..."); return; }
      this.lastDetectedInvoiceType = null; view.updateInvoiceTypeDisplay(null); view.setRefreshButtonEnabled(false);
      PdfDokument pdfDoc = model.getAusgewaehltesDokument();
      if (pdfDoc == null || pdfDoc.getFullPath() == null || pdfDoc.getFullPath().isBlank()) { log.debug("Kein gültiges Dokument für Keyword-Suche."); return; }
      final Path pdfPath;
      try { pdfPath = Paths.get(pdfDoc.getFullPath()); if (!Files.exists(pdfPath)) { log.error("PDF nicht gefunden: {}", pdfPath); view.logMessage("Fehler: PDF für Typ nicht gefunden."); return; } }
      catch (Exception e) { log.error("Ungültiger PDF-Pfad: {}", pdfDoc.getFullPath(), e); view.logMessage("Fehler: Ungültiger PDF-Pfad."); return; }

      // Prüfe, ob Typ schon im Objekt ist
      InvoiceTypeConfig detectedType = pdfDoc.getDetectedInvoiceType();
      if(detectedType != null){
           log.info("Verwende bereits erkannten Rechnungstyp: {}", detectedType.getType());
           this.lastDetectedInvoiceType = detectedType;
           view.updateInvoiceTypeDisplay(detectedType);
           view.setRefreshButtonEnabled(true);
           return; // Keine neue Suche nötig
      }

      // Typ noch nicht bekannt -> Starte Suche im Hintergrund
      view.logMessage("Ermittle Rechnungstyp..."); view.setRefreshButtonEnabled(false);
      SwingWorker<InvoiceTypeConfig, Void> worker = new SwingWorker<>() {
    	  @Override protected InvoiceTypeConfig doInBackground() throws Exception {
    		  log.debug("BG> Starte Keyword-Suche für {}", pdfDoc.getSourcePdf()); PDDocument pdDoc = null; InvoiceTypeConfig result = null;
//    		  pdDoc = null;
//    		  result = null;
    		  try { 
    			  pdDoc = PDDocument.load(pdfPath.toFile()); 
    			  log.trace("BG> PDDocument geladen."); 
    			  result = model.getInvoiceTypeService().findConfigForPdf(pdDoc); 
    			  log.debug("BG> Keyword-Suche Ergebnis: {}", result); 
    		  }catch(IOException ioEx){
    			  log.error("BG> IO Fehler Laden/Suchen: {}", ioEx.getMessage()); throw ioEx;
    		  }catch(Exception ex){
    			  log.error("BG> Unerwarteter Fehler in doInBackground", ex); 
    			  throw ex;
    		  } catch (Throwable t) { // Fange ALLES ab
                  log.error("BG> Kritischer Fehler in doInBackground für {}: {}", pdfDoc.getSourcePdf(), t.getMessage(), t);
                  throw t; // Wirf weiter
    		  }finally { 
    			  if (pdDoc != null) { 
    				  try { 
    					  pdDoc.close(); 
    					  log.trace("BG> PDDocument geschlossen.");
    				  } catch (IOException e) { 
    					  log.error("BG> Fehler Schließen Doc", e);
    				  } 
    			  }
    			  log.info("BG> doInBackground BEENDET für {}", pdfDoc.getSourcePdf());
    		  }
    		  return result; // Gib Ergebnis oder null zurück
    	  }
    	// In AppController.java -> refreshInvoiceTypePanelForCurrentSelection -> SwingWorker -> done()
          @Override protected void done() {
              InvoiceTypeConfig foundConfig = null;
              boolean executionOk = true;
              try {
                  foundConfig = get();
                  // *** WICHTIG: Erst hier den Typ setzen ***
                  lastDetectedInvoiceType = foundConfig;
                  log.info("EDT> Keyword-Suche fertig. Typ: {}", (foundConfig != null ? foundConfig.getType() : "Default/Fehler"));
              } catch (InterruptedException e) { /*...*/ executionOk=false; lastDetectedInvoiceType = model.getInvoiceTypeService().getDefaultConfig();}
                catch (java.util.concurrent.ExecutionException e) { /*...*/ executionOk=false; lastDetectedInvoiceType = model.getInvoiceTypeService().getDefaultConfig();}
                catch (Exception e) { /*...*/ executionOk=false; lastDetectedInvoiceType = model.getInvoiceTypeService().getDefaultConfig();}
              finally {
                  log.info("EDT> Rufe view.updateInvoiceTypeDisplay auf mit: {}", lastDetectedInvoiceType);
                  view.updateInvoiceTypeDisplay(lastDetectedInvoiceType); // GUI aktualisieren

                  // *** NEU: Update-Button Status hier setzen ***
                  boolean enableUpdate = (lastDetectedInvoiceType != null && !InvoiceTypeService.DEFAULT_KEYWORD.equalsIgnoreCase(lastDetectedInvoiceType.getKeyword()));
                  view.setUpdateCsvButtonEnabled(enableUpdate);
                  // --- Ende NEU ---

                  view.setRefreshButtonEnabled(model.getAusgewaehltesDokument() != null);
                  if(executionOk) view.logMessage("Bereit."); // Status nur bei Erfolg zurücksetzen
              }
          }
      };
      worker.execute();
  }
}