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

/**
* Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
* Er reagiert auf Benutzeraktionen. Die Neuverarbeitung wird nur durch den Refresh-Button ausgelöst.
* Enthält Logik zum Aktualisieren der Invoice-Type-Konfiguration in der CSV-Datei.
*/
public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private final MainFrame view; // View wird im Konstruktor übergeben
 private JFileChooser dateiAuswahlDialog;
 private InvoiceTypeConfig lastDetectedInvoiceType = null; // Merkt sich den zuletzt erkannten Typ

 /**
  * Konstruktor. Initialisiert Modell und View und registriert die Listener.
  * @param model Das Anwendungsmodell. Darf nicht null sein.
  * @param view Das Hauptfenster (GUI). Darf nicht null sein.
  */
 public AppController(AnwendungsModell model, MainFrame view) {
     this.model = model;
     this.view = view; // Referenz speichern
     if (this.view == null || this.model == null) {
          throw new IllegalArgumentException("Modell und View dürfen im Controller nicht null sein!");
     }
     initializeListeners(); // Listener registrieren
 }

 /**
  * Registriert alle notwendigen Listener bei den GUI-Komponenten der View.
  * Wird vom Konstruktor aufgerufen.
  */
 private void initializeListeners() {
      log.info("Initialisiere Controller-Listener für View...");
      if (view == null) { // Redundanter Check, aber sicher
          log.error("View ist null in initializeListeners! Listener können nicht registriert werden.");
          return;
      }
      // Registriere alle Listener bei den Komponenten der View
      view.addLadeButtonAbrechnungListener(this::handleLadeAbrechnungAction); // Neuer Button Tab 1
      view.addLadeButtonDetailsListener(this::handleLadePdfAktion);          // Alter Button Tab 2
      view.addExportButtonListener(this::handleExportExcelAktion);
      view.addRefreshButtonListener(this::handleRefreshAction);             // Refresh Button
      view.addEditCsvButtonListener(this::handleEditCsv);                   // Edit CSV Button
      view.addUpdateCsvButtonListener(this::handleUpdateCsvAction);         // Update CSV Button
      view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
      view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
      view.addFlavorComboBoxListener(this::handleFlavorChange);             // Parameter (ohne Aktion)
      view.addRowToleranceSpinnerListener(this::handleRowTolChange);         // Parameter (ohne Aktion)
      view.addConfigMenuOpenListener(this::handleOpenConfigEditor);         // Menü für Bereichs-Editor
      view.addConfigSelectionListener(this::handleConfigSelectionChange);     // Bereichs-Konfig-Auswahl (ohne Aktion)

      setupDateiAuswahlDialog();
      updateAvailableConfigsInView(); // Lade Bereichs-Konfigs beim Start
      log.info("Controller-Listener erfolgreich initialisiert.");
 }

 /**
  * Konfiguriert den JFileChooser für PDF- und Excel-Dateien.
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
     // TODO: Implementiere die Logik für diesen Button
 }


 /** Handler für den Lade-Button im Tab "Konfiguration & Details" */
 private void handleLadePdfAktion(ActionEvent e) {
      log.info("Lade PDF(s) Button (Details) geklickt.");
      if (view == null) { log.error("View ist null in handleLadePdfAktion"); return; }

      view.logMessage("Öffne Dateiauswahl zum Laden für Detailansicht...");
      dateiAuswahlDialog.setDialogTitle("PDF(s) für Detailansicht auswählen");
      dateiAuswahlDialog.setMultiSelectionEnabled(true);
      dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));

      int rueckgabeWert = dateiAuswahlDialog.showOpenDialog(view);
      if (rueckgabeWert == JFileChooser.APPROVE_OPTION) {
          File[] ausgewaehlteDateien = dateiAuswahlDialog.getSelectedFiles();
          if (ausgewaehlteDateien != null && ausgewaehlteDateien.length > 0) {
              List<Path> pdfPfade = new ArrayList<>();
              for (File datei : ausgewaehlteDateien) { pdfPfade.add(datei.toPath()); }
              log.info("Ausgewählte Dateien: {}", Arrays.toString(ausgewaehlteDateien));
              view.logMessage("Verarbeite " + pdfPfade.size() + " PDF(s) für Detailansicht...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);

              Map<String, String> parameterGui = getCurrentParametersFromGui();
              // Rufe Modell auf (überladene Methode). Das Modell holt die aktive Konfig selbst.
              model.ladeUndVerarbeitePdfs(pdfPfade, parameterGui,
                 processedDoc -> { // Status Callback
                     if (processedDoc != null) {
                          log.info("Callback nach Verarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                          SwingUtilities.invokeLater(() -> view.logMessage("Verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));
                     }
                 },
                 progress -> { // Progress Callback
                      SwingUtilities.invokeLater(()->{
                           view.setProgressBarValue((int)(progress*100));
                           if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}
                      });
                 }
              );
          } else { log.warn("Keine Dateien ausgewählt."); view.logMessage("Keine Dateien ausgewählt.");}
      } else { log.info("Dateiauswahl abgebrochen."); view.logMessage("Dateiauswahl abgebrochen.");}
 }

 /** Behandelt den Klick auf den "Nach Excel exportieren"-Button. */
 private void handleExportExcelAktion(ActionEvent e) {
     log.info("Export nach Excel Button geklickt.");
     if (view == null) { log.error("View ist null in handleExportExcelAktion"); return; }
     dateiAuswahlDialog.setDialogTitle("Excel-Datei speichern unter..."); dateiAuswahlDialog.setMultiSelectionEnabled(false); dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel Arbeitsmappe (*.xlsx)", "xlsx")); dateiAuswahlDialog.setSelectedFile(new File("Extrahierte_Tabellen.xlsx"));
     int ret = dateiAuswahlDialog.showSaveDialog(view);
     if (ret == JFileChooser.APPROVE_OPTION) {
          File file = dateiAuswahlDialog.getSelectedFile(); Path ziel = file.toPath(); if(!ziel.toString().toLowerCase().endsWith(".xlsx")) ziel = ziel.resolveSibling(ziel.getFileName()+".xlsx");
          final Path finalZiel = ziel; view.logMessage("Exportiere nach " + finalZiel.getFileName() + "..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
          new SwingWorker<Void,Void>(){ boolean erfolg=false; String fehler=null; @Override protected Void doInBackground(){try{model.exportiereAlleNachExcel(finalZiel); erfolg=true;} catch(Exception ex){fehler=ex.getMessage();log.error("Excel Export Fehler",ex);} finally{SwingUtilities.invokeLater(()->{view.setProgressBarValue(100); new Timer(1000,ae->view.setProgressBarVisible(false)).start();});} return null;} @Override protected void done(){if(erfolg)JOptionPane.showMessageDialog(view,"Export erfolgreich nach\n"+finalZiel,"Erfolg",JOptionPane.INFORMATION_MESSAGE); else JOptionPane.showMessageDialog(view,"Export fehlgeschlagen.\n"+fehler,"Fehler",JOptionPane.ERROR_MESSAGE); view.logMessage(erfolg?"Export fertig.":"Export fehlgeschlagen.");}}.execute();
     } else { log.info("Export abgebrochen."); view.logMessage("Export abgebrochen."); }
 }

 /** Behandelt eine Änderung in der PDF-Auswahl-ComboBox. */
 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
              model.setAusgewaehltesDokument(selectedDoc); // Löst Events aus
              refreshInvoiceTypePanelForCurrentSelection(); // Starte Keyword-Suche für neues PDF
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
      if (view == null) { log.error("View ist null in handleOpenConfigEditor"); return; }
      ExtractionConfiguration configToEdit = model.getAktiveKonfiguration(); ExtractionConfiguration configForDialog;
      if (configToEdit != null) { configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName()); if (configForDialog == null) { log.warn("Konnte Konfig '{}' nicht laden, erstelle neue.", configToEdit.getName()); configForDialog = new ExtractionConfiguration("Neue Konfiguration"); } }
      else { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); }
      ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);
      dialog.setVisible(true); // Zeigt Dialog modal
      ExtractionConfiguration savedConfig = dialog.getSavedConfiguration();
      if (savedConfig != null) {
          log.info("Bereichs-Konfiguration '{}' wurde gespeichert.", savedConfig.getName());
          updateAvailableConfigsInView(); // Liste neu laden
          model.setAktiveKonfiguration(savedConfig); // Als aktiv setzen (löst Event in View)
          view.logMessage("Konfiguration '" + savedConfig.getName() + "' gespeichert/aktiviert. Ggf. 'Neu verarbeiten'.");
      } else { log.info("Bereichs-Konfigurationsdialog geschlossen ohne Speichern."); updateAvailableConfigsInView();}
 }

  /** Behandelt Änderung in der Bereichs-Konfigurations-ComboBox (ohne Neuverarbeitung). */
  private void handleConfigSelectionChange(ItemEvent e) {
     if (e.getStateChange() == ItemEvent.SELECTED) {
         Object selectedItem = e.getItem(); ExtractionConfiguration selectedConfig = null;
         if (selectedItem instanceof ExtractionConfiguration) { selectedConfig = (ExtractionConfiguration) selectedItem; }
         else if (!"Keine".equals(selectedItem)) { log.warn("Unerwartetes Item in Konfig-Combo: {}", selectedItem); return; }

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
     if (view == null) { log.error("View ist null in handleUpdateCsvAction"); return; }

     // Hole den zuletzt erkannten Rechnungstyp (Keyword ist der Schlüssel)
     InvoiceTypeConfig configToUpdate = this.lastDetectedInvoiceType;
     if (configToUpdate == null || InvoiceTypeService.DEFAULT_KEYWORD.equalsIgnoreCase(configToUpdate.getKeyword())) {
         log.warn("Kein spezifischer Rechnungstyp zum Aktualisieren ausgewählt oder 'Others' ausgewählt.");
         JOptionPane.showMessageDialog(view, "Es muss ein spezifischer Rechnungstyp (nicht 'Others') erkannt sein,\num dessen Standardwerte in der CSV zu aktualisieren.", "Aktion nicht möglich", JOptionPane.WARNING_MESSAGE);
         return;
     }
     String keyword = configToUpdate.getKeyword();

     // Hole die NEUEN Werte aus den oberen GUI-Elementen
     Map<String, String> params = getCurrentParametersFromGui(); // Holt Flavor und RowTol
     String newFlavor = params.get("flavor");
     String newRowTol = params.get("row_tol");

     Object selectedConfigItem = view.getConfigComboBox().getSelectedItem();
     String newAreaType = "Konfig"; // Default, falls "Keine" oder ungültig
     if (selectedConfigItem instanceof ExtractionConfiguration) {
         newAreaType = ((ExtractionConfiguration) selectedConfigItem).getName(); // Nimm Namen der Konfig
     } else if ("Keine".equals(selectedConfigItem)) {
         newAreaType = "Keine"; // Speichere "Keine" explizit
          log.info("Bereichs-Konfiguration 'Keine' wird für CSV-Update verwendet.");
     } else {
          log.warn("Unerwarteter Typ in Bereichs-Konfig ComboBox beim Speichern: {}", selectedConfigItem);
          newAreaType = configToUpdate.getAreaType(); // Behalte alten Wert als Fallback
     }
     log.debug("Werte für CSV-Update: Keyword='{}', AreaType='{}', Flavor='{}', RowTol='{}'",
               keyword, newAreaType, newFlavor, newRowTol);

     // Rufe Service auf, um CSV zu aktualisieren
     boolean success = model.getInvoiceTypeService().updateConfigInCsv(keyword, newAreaType, newFlavor, newRowTol);

     // Gib Feedback und aktualisiere Anzeige
     if (success) {
         log.info("CSV-Datei erfolgreich aktualisiert für Keyword '{}'.", keyword);
         JOptionPane.showMessageDialog(view, "Standardwerte für Rechnungstyp '" + keyword + "'\nin der CSV-Datei aktualisiert.", "Update erfolgreich", JOptionPane.INFORMATION_MESSAGE);
         // Lade die Daten für das Panel neu, um die gespeicherten Werte anzuzeigen
         refreshInvoiceTypePanelForCurrentSelection();
     } else {
         log.error("Fehler beim Aktualisieren der CSV-Datei für Keyword '{}'.", keyword);
         JOptionPane.showMessageDialog(view, "Fehler beim Aktualisieren der CSV-Datei.\nPrüfen Sie die Logs oder Dateiberechtigungen.", "Update fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
     }
 }


 // --- Hilfsmethoden ---

 /**
  * Löst die Neuverarbeitung des aktuell ausgewählten PDFs aus.
  * Ermittelt Parameter aus GUI und die passende Bereichs-Konfig (basierend auf der *aktuellen* Auswahl).
  * @param grund Grund für die Neuverarbeitung (für Logging).
  */
 private void triggerReprocessing(String grund) {
      if (view == null) { log.error("View ist null in triggerReprocessing"); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
          Path pdfPath = null;
          try { pdfPath = Paths.get(selectedDoc.getFullPath()); }
          catch (Exception pathEx) { log.error("Ungültiger Pfad: {}", selectedDoc.getFullPath(), pathEx); view.logMessage("Fehler: Ungültiger Pfad."); return; }

          if (pdfPath != null) {
              Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();
              // Hole die explizit VOM USER ausgewählte Bereichs-Konfig (kann null sein)
              ExtractionConfiguration aktiveBereichsKonfig = model.getAktiveKonfiguration();
              // Hole den zuletzt erkannten Rechnungstyp nur für Logging
              InvoiceTypeConfig typConfig = selectedDoc.getDetectedInvoiceType() != null ? selectedDoc.getDetectedInvoiceType() : model.getInvoiceTypeService().getDefaultConfig();

              log.info("({}) Starte Neuverarbeitung für PDF: {} mit GUI-Parametern: {}, InvoiceType: {}, Aktive Bereichs-Konfig: {}",
                       grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, typConfig.getKeyword(), (aktiveBereichsKonfig != null ? aktiveBereichsKonfig.getName() : "Keine"));
              view.logMessage("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);

              // Rufe Modell auf und übergib die explizit ausgewählte Bereichs-Konfiguration
              model.ladeUndVerarbeitePdfsMitKonfiguration(
                  Collections.singletonList(pdfPath),
                  aktuelleParameterGui,
                  aktiveBereichsKonfig, // Übergib die aktuell ausgewählte (oder null)
                  processedDoc -> { // Status Callback
                      if(processedDoc!=null) SwingUtilities.invokeLater(()->view.logMessage("Neu verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));
                      else log.warn("Callback nach Neuverarbeitung: Dokument ist null.");
                  },
                  progress -> { // Progress Callback
                      SwingUtilities.invokeLater(()->{view.setProgressBarValue((int)(progress*100)); if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}});
                  }
              );
          }
      } else { log.debug("Kein PDF ausgewählt für Neuverarbeitung."); view.logMessage("Bitte zuerst ein PDF auswählen."); }
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
   * der im ausgewählten PdfDokument-Objekt gespeichert ist. Startet keine neue Suche.
   */
  public void refreshInvoiceTypePanelForCurrentSelection() {
      if (view == null) { log.error("View ist null in refreshInvoiceTypePanelForCurrentSelection"); return; }
      PdfDokument selectedDoc = model.getAusgewaehltesDokument();
      log.debug("Aktualisiere InvoiceTypePanel für: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));

      InvoiceTypeConfig configToShow = null;
      if (selectedDoc != null) {
          // Hole den Typ direkt aus dem Dokumentobjekt
          configToShow = selectedDoc.getDetectedInvoiceType();
          if(configToShow == null){
               log.warn("DetectedInvoiceType im ausgewählten Dokument ist null! Verwende Default.");
               configToShow = model.getInvoiceTypeService().getDefaultConfig();
          }
          // Speichere für Refresh-Button Logik
          this.lastDetectedInvoiceType = configToShow;
      } else {
           log.debug("Kein Dokument ausgewählt, leere Invoice Panel.");
           this.lastDetectedInvoiceType = null; // Reset
      }

      // Rufe View-Update auf
      view.updateInvoiceTypeDisplay(configToShow);
      // Setze Refresh-Button Status
      view.setRefreshButtonEnabled(selectedDoc != null);
      // Setze Update-CSV-Button Status
      view.setUpdateCsvButtonEnabled(configToShow != null && !InvoiceTypeService.DEFAULT_KEYWORD.equalsIgnoreCase(configToShow.getKeyword()));
  }
}