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

/**
* Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
* Er reagiert auf Benutzeraktionen und delegiert Aufgaben an das Modell.
* Lädt die korrekte Bereichs-Konfiguration basierend auf dem erkannten Rechnungstyp.
* Die Listener werden nach der Instanziierung über initializeListeners() registriert.
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
      view.addRefreshButtonListener(this::handleRefreshAction);
      view.addEditCsvButtonListener(this::handleEditCsv);
      view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
      view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
      view.addFlavorComboBoxListener(this::handleParameterChange);
      view.addRowToleranceSpinnerListener(this::handleParameterChange);
      view.addConfigMenuOpenListener(this::handleOpenConfigEditor);
      view.addConfigSelectionListener(this::handleConfigSelectionChange);

      // Initialisiere abhängige Komponenten
      setupDateiAuswahlDialog();
      updateAvailableConfigsInView(); // Lade Bereichs-Konfigs beim Start
      log.info("Controller-Listener erfolgreich initialisiert.");
 }

 /**
  * Konfiguriert den JFileChooser für PDF- und Excel-Dateien.
  */
 private void setupDateiAuswahlDialog() {
     dateiAuswahlDialog = new JFileChooser();
     // Standardfilter ist PDF, wird bei Bedarf geändert
     dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
     dateiAuswahlDialog.setAcceptAllFileFilterUsed(false); // Verhindert "Alle Dateien"
 }

 // --- Event Handler ---

 /**
  * Behandelt den Klick auf den "PDF(s) laden"-Button.
  * Öffnet einen Dateiauswahldialog und startet die asynchrone Verarbeitung der gewählten PDFs
  * unter Verwendung der aktuell eingestellten Parameter und der aktiven Konfiguration.
  * @param e Das ActionEvent (wird nicht direkt verwendet).
  */
 private void handleLadePdfAktion(ActionEvent e) {
      log.info("Lade PDF(s) Button geklickt.");
      if (view == null) { log.error("View ist null in handleLadePdfAktion"); return; }

      view.setStatus("Öffne Dateiauswahl zum Laden...");
      dateiAuswahlDialog.setDialogTitle("PDF-Dateien auswählen");
      dateiAuswahlDialog.setMultiSelectionEnabled(true);
      dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));

      int rueckgabeWert = dateiAuswahlDialog.showOpenDialog(view);
      if (rueckgabeWert == JFileChooser.APPROVE_OPTION) {
          File[] ausgewaehlteDateien = dateiAuswahlDialog.getSelectedFiles();
          if (ausgewaehlteDateien != null && ausgewaehlteDateien.length > 0) {
              List<Path> pdfPfade = new ArrayList<>();
              for (File datei : ausgewaehlteDateien) { pdfPfade.add(datei.toPath()); }
              log.info("Ausgewählte Dateien: {}", Arrays.toString(ausgewaehlteDateien));
              view.setStatus("Verarbeite " + pdfPfade.size() + " PDF(s)...");
              view.setProgressBarVisible(true); view.setProgressBarValue(0);

              Map<String, String> parameterGui = getCurrentParametersFromGui();
              // Rufe Modell auf (überladene Methode). Das Modell holt die aktive Konfig selbst.
              model.ladeUndVerarbeitePdfs(pdfPfade, parameterGui,
                 processedDoc -> { // Callback für Status
                     if (processedDoc != null) {
                          log.info("Callback nach Verarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                          SwingUtilities.invokeLater(() -> view.setStatus("Verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));
                     } else { log.info("Callback empfangen (Dokument war null)."); }
                 },
                 progress -> { // Callback für Fortschritt
                      SwingUtilities.invokeLater(()->{
                           view.setProgressBarValue((int)(progress*100));
                           if(progress>=1.0){ Timer t=new Timer(2000,ae->view.setProgressBarVisible(false)); t.setRepeats(false);t.start();}
                      });
                 }
              );
          } else { log.warn("Keine Dateien ausgewählt."); view.setStatus("Keine Dateien ausgewählt.");}
      } else { log.info("Dateiauswahl abgebrochen."); view.setStatus("Dateiauswahl abgebrochen.");}
 }

 /**
  * Behandelt den Klick auf den "Nach Excel exportieren"-Button.
  * @param e Das ActionEvent.
  */
 private void handleExportExcelAktion(ActionEvent e) {
     log.info("Export nach Excel Button geklickt.");
     if (view == null) { log.error("View ist null in handleExportExcelAktion"); return; }

     dateiAuswahlDialog.setDialogTitle("Excel-Datei speichern unter...");
     dateiAuswahlDialog.setMultiSelectionEnabled(false);
     dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel Arbeitsmappe (*.xlsx)", "xlsx"));
     dateiAuswahlDialog.setSelectedFile(new File("Extrahierte_Tabellen.xlsx")); // Vorschlag

     int ret = dateiAuswahlDialog.showSaveDialog(view);
     if (ret == JFileChooser.APPROVE_OPTION) {
          File file = dateiAuswahlDialog.getSelectedFile(); Path ziel = file.toPath(); if(!ziel.toString().toLowerCase().endsWith(".xlsx")) ziel = ziel.resolveSibling(ziel.getFileName()+".xlsx");
          final Path finalZiel = ziel; view.setStatus("Exportiere..."); view.setProgressBarVisible(true); view.setProgressBarValue(0);
          // SwingWorker für den Export im Hintergrund
          new SwingWorker<Void,Void>(){
              boolean erfolg=false; String fehler=null;
              @Override protected Void doInBackground(){try{model.exportiereAlleNachExcel(finalZiel); erfolg=true;} catch(Exception ex){fehler=ex.getMessage();log.error("Excel Export Fehler",ex);} finally{SwingUtilities.invokeLater(()->{view.setProgressBarValue(100); new Timer(1000,ae->view.setProgressBarVisible(false)).start();});} return null;}
              @Override protected void done(){if(erfolg)JOptionPane.showMessageDialog(view,"Export erfolgreich nach\n"+finalZiel,"Erfolg",JOptionPane.INFORMATION_MESSAGE); else JOptionPane.showMessageDialog(view,"Export fehlgeschlagen.\n"+fehler,"Fehler",JOptionPane.ERROR_MESSAGE); view.setStatus(erfolg?"Export fertig.":"Export fehlgeschlagen.");}
          }.execute();
     } else { log.info("Export abgebrochen."); view.setStatus("Export abgebrochen."); }
 }

 /**
  * Behandelt eine Änderung in der PDF-Auswahl-ComboBox.
  * Informiert das Modell über das neu ausgewählte Dokument und stößt die
  * Aktualisierung des InvoiceType-Panels an.
  * @param e Das ActionEvent.
  */
 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
          PdfDokument selectedDoc = (PdfDokument) view.getPdfComboBox().getSelectedItem();
          // Aktualisiere das Modell nur, wenn sich die Auswahl wirklich geändert hat
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", (selectedDoc != null ? selectedDoc.getSourcePdf() : "null"));
              // Setze das neue Dokument im Modell (löst SELECTED_DOCUMENT_PROPERTY aus)
              model.setAusgewaehltesDokument(selectedDoc);
              // Aktualisiere das InvoiceType Panel basierend auf dem neuen Dokument
              refreshInvoiceTypePanelForCurrentSelection(); // Rufe die Methode direkt auf
          }
     }
 }

 /**
  * Behandelt eine Änderung in der Tabellen-Auswahl-ComboBox.
  * Informiert das Modell über die neu ausgewählte Tabelle.
  * @param e Das ActionEvent.
  */
 private void handleTabelleComboBoxAuswahl(ActionEvent e) {
     if ("comboBoxChanged".equals(e.getActionCommand()) && view != null) {
         ExtrahierteTabelle selectedTable = (ExtrahierteTabelle) view.getTabelleComboBox().getSelectedItem();
         // Aktualisiere das Modell nur, wenn eine Tabelle ausgewählt ist und sie sich unterscheidet
         if (selectedTable != null && !Objects.equals(model.getAusgewaehlteTabelle(), selectedTable)) {
              log.info("Tabellen ComboBox Auswahl geändert zu: {}", selectedTable);
              model.setAusgewaehlteTabelle(selectedTable); // Löst Event für Tabellenanzeige aus
          }
     }
 }

 /**
  * Gemeinsamer Listener für Änderungen an den Parameter-Steuerelementen (Flavor, Row Tol).
  * Löst die Neuverarbeitung des aktuell ausgewählten PDFs aus.
  * @param e Das auslösende Event (ActionEvent oder AWTEvent).
  */
 private void handleParameterChange(AWTEvent e) {
      String sourceName = (e != null && e.getSource() != null) ? e.getSource().getClass().getSimpleName() : "Unbekannt";
      log.debug("Parameteränderung erkannt von: {}", sourceName);
      // Verzögere leicht, um mehrfache Events zu bündeln
      SwingUtilities.invokeLater(() -> triggerReprocessing("Parameter geändert"));
 }
 /** Überschriebene Methode für ChangeEvents vom JSpinner. */
 private void handleParameterChange(ChangeEvent e) { handleParameterChange((AWTEvent)null); } // Ruft allgemeinen Handler auf

 /**
  * Öffnet den Konfigurationseditor-Dialog für Bereichsdefinitionen.
  * Wird aufgerufen, wenn der entsprechende Menüpunkt geklickt wird.
  */
 private void handleOpenConfigEditor(ActionEvent e) {
      log.info("Öffne Bereichs-Konfigurationseditor (Menü)...");
      if (view == null) { log.error("View ist null in handleOpenConfigEditor"); return; }

      ExtractionConfiguration configToEdit = model.getAktiveKonfiguration();
      ExtractionConfiguration configForDialog;
      // Lade eine frische Kopie der Konfiguration, um Änderungen zu isolieren
      if (configToEdit != null) {
           configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName());
           if (configForDialog == null) { log.warn("Konnte Konfig '{}' nicht laden, erstelle neue.", configToEdit.getName()); configForDialog = new ExtractionConfiguration("Neue Konfiguration"); }
      } else { configForDialog = new ExtractionConfiguration("Neue Konfiguration"); }

      ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);
      dialog.setVisible(true); // Anzeigen und warten bis geschlossen

      // Nach dem Schließen des Dialogs:
      ExtractionConfiguration savedConfig = dialog.getSavedConfiguration();
      if (savedConfig != null) {
          log.info("Bereichs-Konfiguration '{}' wurde im Dialog gespeichert.", savedConfig.getName());
          updateAvailableConfigsInView(); // Liste neu laden
          model.setAktiveKonfiguration(savedConfig); // Als aktiv setzen (löst Event in View)
          triggerReprocessing("Bereichs-Konfiguration gespeichert"); // Neu verarbeiten
      } else { log.info("Bereichs-Konfigurationsdialog geschlossen ohne Speichern."); updateAvailableConfigsInView(); } // Nur Auswahl syncen
 }

  /**
   * Wird aufgerufen, wenn in der Bereichs-Konfigurations-ComboBox eine Auswahl getroffen wird.
   * Setzt die ausgewählte Konfiguration im Modell als aktiv und löst Neuverarbeitung aus.
   * @param e Das ItemEvent.
   */
  private void handleConfigSelectionChange(ItemEvent e) {
     if (e.getStateChange() == ItemEvent.SELECTED) { // Nur auf Auswahl reagieren
         Object selectedItem = e.getItem();
         ExtractionConfiguration selectedConfig = null;
         if (selectedItem instanceof ExtractionConfiguration) { selectedConfig = (ExtractionConfiguration) selectedItem; }
         else if (!"Keine".equals(selectedItem)) { log.warn("Unerwartetes Item in Konfig-Combo: {}", selectedItem); return; } // "Keine" -> null

         log.info("Bereichs-Konfig Auswahl geändert zu: {}", (selectedConfig != null ? selectedConfig.getName() : "Keine"));
         // Aktualisiere Modell nur, wenn Auswahl sich geändert hat
         if (!Objects.equals(model.getAktiveKonfiguration(), selectedConfig)) {
              model.setAktiveKonfiguration(selectedConfig); // Löst ACTIVE_CONFIG_PROPERTY Event aus
              triggerReprocessing("Bereichs-Konfiguration geändert"); // Löse Neuverarbeitung aus
         }
     }
 }

 /** Behandelt den Klick auf den "Typdefinitionen (CSV)..."-Button. */
 private void handleEditCsv(ActionEvent e) { log.info("Edit CSV Button geklickt."); model.getInvoiceTypeService().openCsvInEditor(); }

 /** Behandelt den Klick auf den "Neu verarbeiten"-Button. */
 private void handleRefreshAction(ActionEvent e) { triggerReprocessing("Refresh Button"); }


 // --- Hilfsmethoden ---

 /**
  * Löst die Neuverarbeitung des aktuell ausgewählten PDFs aus.
  * Ermittelt Parameter aus GUI und die passende Bereichs-Konfig (basierend auf InvoiceType).
  * @param grund Grund für die Neuverarbeitung (für Logging).
  */
 private void triggerReprocessing(String grund) {
     if (view == null) { log.error("View ist null in triggerReprocessing"); return; }
     PdfDokument selectedDoc = model.getAusgewaehltesDokument();
     if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
         Path pdfPath = null;
         try { pdfPath = Paths.get(selectedDoc.getFullPath()); }
         catch (Exception pathEx) { log.error("Ungültiger Pfad: {}", selectedDoc.getFullPath(), pathEx); view.setStatus("Fehler: Ungültiger Pfad."); return; }

         if (pdfPath != null) {
             // 1. Parameter aus GUI holen
             Map<String, String> aktuelleParameterGui = getCurrentParametersFromGui();

             // 2. Rechnungstyp holen (nehme den zuletzt erkannten)
             InvoiceTypeConfig typConfig = this.lastDetectedInvoiceType;
             if (typConfig == null) {
                  log.warn("Kein Rechnungstyp für Neuverarbeitung bekannt, verwende Default-Typ.");
                  typConfig = model.getInvoiceTypeService().getDefaultConfig();
             }

             // 3. Passende Bereichs-Konfiguration laden (basierend auf Area-Type aus CSV)
             ExtractionConfiguration bereichsKonfig = null;
             String configNameFromCsv = typConfig.getAreaType();
             if (configNameFromCsv != null && !configNameFromCsv.isBlank() && !configNameFromCsv.endsWith("*")) { // Ignoriere Fallback "*"
                 bereichsKonfig = model.getConfigurationService().loadConfiguration(configNameFromCsv.trim());
                 if (bereichsKonfig != null) { log.info("--> Verwende Bereichs-Konfig '{}' aus CSV.", bereichsKonfig.getName());}
                 else { log.warn("--> Bereichs-Konfig '{}' aus CSV nicht geladen. Ohne Bereiche.", configNameFromCsv); }
             } else { log.info("--> Kein spezifischer Bereichs-Konfig-Name in CSV für '{}' oder Fallback ('{}'). Ohne Bereiche.", typConfig.getKeyword(), configNameFromCsv); }

             // 4. Verarbeitung im Modell anstoßen
             log.info("({}) Starte Neuverarbeitung für PDF: {} mit GUI-Parametern: {}, InvoiceType: {}, Bereichs-Konfig: {}",
                      grund, selectedDoc.getSourcePdf(), aktuelleParameterGui, typConfig.getKeyword(), (bereichsKonfig != null ? bereichsKonfig.getName() : "Keine"));
             view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");
             view.setProgressBarVisible(true); view.setProgressBarValue(0);

             model.ladeUndVerarbeitePdfsMitKonfiguration(
                 Collections.singletonList(pdfPath), // Nur dieses eine PDF
                 aktuelleParameterGui,
                 bereichsKonfig, // Übergib die spezifische (oder null)
                 processedDoc -> { // Callback für Status nach Neuverarbeitung
                     if (processedDoc != null) {
                         log.info("Callback nach Neuverarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                         // GUI Updates werden jetzt durch das SINGLE_DOCUMENT_REPROCESSED Event im MainFrame ausgelöst
                         // Stattdessen hier nur den Status setzen
                         SwingUtilities.invokeLater(()->view.setStatus("Neu verarbeitet:"+processedDoc.getSourcePdf()+(processedDoc.getError()!=null?" [FEHLER]":"")));
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
     } else { log.debug("Kein PDF ausgewählt für Neuverarbeitung."); view.setStatus("Bitte zuerst ein PDF auswählen."); }
 }

 /** Liest Parameter (Flavor, RowTol) aus GUI. */
 private Map<String, String> getCurrentParametersFromGui() {
      if (view == null) return Collections.emptyMap(); // Sicherheitscheck
      Map<String,String> p = new HashMap<>();
      try {
          p.put("flavor", (String)view.getFlavorComboBox().getSelectedItem());
          Object v=view.getRowToleranceSpinner().getValue();
          p.put("row_tol", String.valueOf(v instanceof Number ? ((Number)v).intValue() : "2")); // Default 2
      } catch (Exception e) { log.error("Fehler GUI-Parameter Lesen", e); p.putIfAbsent("flavor", "lattice"); p.putIfAbsent("row_tol", "2"); }
      return p;
 }

  /** Lädt verfügbare Bereichs-Konfigurationen und aktualisiert die View. */
  private void updateAvailableConfigsInView() {
      if (view == null) { log.error("View ist null in updateAvailableConfigsInView"); return; }
      log.debug("Aktualisiere Bereichs-Konfigurations-ComboBox in der View...");
      List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations();
      ExtractionConfiguration activeConfig = model.getAktiveKonfiguration(); // Holt die im Modell aktuell gesetzte
      view.updateConfigurationComboBox(configs, activeConfig); // Übergibt beides an die View
  }

  /**
   * Aktualisiert das Invoice Type Panel in der GUI basierend auf PDF-Inhalt.
   * Startet die Keyword-Suche in einem Hintergrundthread.
   * Speichert den erkannten Typ für die Neuverarbeitung.
   * Wird vom Controller aufgerufen (z.B. nach PDF-Auswahl).
   */
  public void refreshInvoiceTypePanelForCurrentSelection() {
      if (view == null) { log.error("View ist null in refreshInvoiceTypePanelForCurrentSelection"); return; }
      this.lastDetectedInvoiceType = null; // Reset beim Start der Suche
      view.updateInvoiceTypeDisplay(null); // Panel erstmal leeren oder "Suche..." anzeigen
      view.setRefreshButtonEnabled(false); // Refresh deaktivieren während Suche

      PdfDokument pdfDoc = model.getAusgewaehltesDokument(); // Hole das aktuell ausgewählte
      if (pdfDoc == null || pdfDoc.getFullPath() == null || pdfDoc.getFullPath().isBlank()) {
          log.debug("Kein gültiges Dokument für Keyword-Suche ausgewählt.");
          return; // Nichts zu tun
      }
      final Path pdfPath;
      try {
           pdfPath = Paths.get(pdfDoc.getFullPath());
           if (!Files.exists(pdfPath)) { log.error("PDF für Keyword nicht gefunden: {}", pdfPath); view.updateInvoiceTypeDisplay(null); view.setStatus("Fehler: PDF für Typ nicht gefunden."); return; }
      }
      catch (Exception e) { log.error("Ungültiger PDF-Pfad: {}", pdfDoc.getFullPath(), e); view.updateInvoiceTypeDisplay(null); view.setStatus("Fehler: Ungültiger PDF-Pfad."); return; }

      view.setStatus("Ermittle Rechnungstyp..."); // Status während Suche

      SwingWorker<InvoiceTypeConfig, Void> worker = new SwingWorker<>() {
          @Override protected InvoiceTypeConfig doInBackground() throws Exception {
               log.debug("Starte Keyword-Suche für {}", pdfDoc.getSourcePdf());
               PDDocument pdDoc = null;
               try { pdDoc = PDDocument.load(pdfPath.toFile()); return model.getInvoiceTypeService().findConfigForPdf(pdDoc); }
               // IOException wird gefangen und als Fehler behandelt
               catch(IOException ioEx) { log.error("IO Fehler beim Laden von {} für Keyword-Suche: {}", pdfDoc.getSourcePdf(), ioEx.getMessage()); throw ioEx;} // Gib Fehler weiter
               finally { if (pdDoc != null) try { pdDoc.close(); } catch (IOException e) { log.error("Fehler Schließen Doc nach Keyword", e);} }
          }
          @Override protected void done() {
              try {
                  InvoiceTypeConfig found = get(); // Hole Ergebnis
                  lastDetectedInvoiceType = found; // Erkannten Typ merken für Refresh
                  log.info("Keyword-Suche fertig. Typ: {}", (found != null ? found.getType() : "Default/Fehler"));
                  view.updateInvoiceTypeDisplay(found); // GUI im EDT aktualisieren
                  view.setRefreshButtonEnabled(true); // Aktiviere Refresh nach Suche
                  view.setStatus("Bereit."); // Status zurücksetzen
              } catch (Exception e) {
                  // Fange Fehler ab, die während doInBackground oder get() auftreten
                  log.error("Fehler beim Abrufen des Ergebnisses der Keyword-Suche", e);
                  // Zeige Default/leere Felder im Fehlerfall an
                  lastDetectedInvoiceType = model.getInvoiceTypeService().getDefaultConfig(); // Setze auf Default
                  view.updateInvoiceTypeDisplay(lastDetectedInvoiceType); // Zeige Default
                  view.setStatus("Fehler bei der Rechnungstyp-Erkennung.");
                  view.setRefreshButtonEnabled(true); // Refresh trotzdem aktivieren? Oder false? Hier: true
              }
          }
      };
      worker.execute(); // Starte den SwingWorker
  }
}