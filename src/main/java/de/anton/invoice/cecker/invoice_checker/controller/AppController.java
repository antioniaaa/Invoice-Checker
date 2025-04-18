package de.anton.invoice.cecker.invoice_checker.controller;


import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.PdfDokument;
import de.anton.invoice.cecker.invoice_checker.view.MainFrame;
import de.anton.invoice.cecker.invoice_checker.model.ExtractionConfiguration;
import de.anton.invoice.cecker.invoice_checker.model.ConfigurationService;
import de.anton.invoice.cecker.invoice_checker.view.ConfigurationDialog; // Import für Dialog

//Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Swing-Komponenten und Event-Handling
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;     // Für ComboBox Item Listener
import java.awt.event.ItemListener; // Für ComboBox Item Listener
import java.awt.AWTEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator; // Für Sortierung
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private final MainFrame view;
 private JFileChooser dateiAuswahlDialog;
 private boolean isProgrammaticChange = false; // Verhindert Event-Schleifen

 public AppController(AnwendungsModell model, MainFrame view) {
     this.model = model;
     this.view = view;
     initController();
 }

 private void initController() {
     // Buttons
     view.addLadeButtonListener(this::handleLadePdfAktion);
     view.addExportButtonListener(this::handleExportExcelAktion);
     // ComboBoxen
     view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
     view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
     // Parameter
     view.addFlavorComboBoxListener(this::handleParameterChange);
     view.addRowToleranceSpinnerListener(this::handleParameterChange);
     // Konfiguration
     view.addConfigMenuOpenListener(this::handleOpenConfigEditor); // Listener für Menüpunkt
     view.addConfigSelectionListener(this::handleConfigSelectionChange); // Listener für Konfig-ComboBox

     setupDateiAuswahlDialog();
     // Lade verfügbare Konfigurationen beim Start und zeige sie an
     loadAndDisplayAvailableConfigs();
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

              // Aktuelle Parameter und die AKTIVE Konfiguration aus dem Modell verwenden
              Map<String, String> parameter = getCurrentParametersFromGui();
              ExtractionConfiguration aktiveConfig = model.getAktiveKonfiguration();
              log.info("--> Verwende Parameter: {} und aktive Konfig: {}", parameter, (aktiveConfig != null ? aktiveConfig.getName() : "Keine"));

              // Rufe Modell auf (Methode, die die Konfig selbst holt oder wir übergeben sie)
              // Hier übergeben wir die Konfig explizit
              model.ladeUndVerarbeitePdfsMitKonfiguration(pdfPfade, parameter, aktiveConfig, processedDoc -> {
                   if (processedDoc != null) {
                       log.info("Callback nach Verarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                       SwingUtilities.invokeLater(() -> view.setStatus("Verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : "")));
                   } else { log.info("Callback empfangen (Dokument war null)."); }
               });
          } else { /* Keine Dateien ausgewählt */ log.warn("Keine Dateien ausgewählt."); view.setStatus("Keine Dateien ausgewählt."); }
      } else { /* Abbruch */ log.info("Dateiauswahl abgebrochen."); view.setStatus("Dateiauswahl abgebrochen."); }
 }

 private void handleExportExcelAktion(ActionEvent e) { /* ... unverändert ... */ }
 private void handlePdfComboBoxAuswahl(ActionEvent e) { /* ... unverändert ... */ }
 private void handleTabelleComboBoxAuswahl(ActionEvent e) { /* ... unverändert ... */ }
 private void handleParameterChange(AWTEvent e) { triggerReprocessing("Parameter geändert"); }
 private void handleParameterChange(ChangeEvent e) { triggerReprocessing("Parameter geändert"); }



 // Handler für Konfigurationsauswahl-ComboBox
  private void handleConfigSelectionChange(ItemEvent e) {
     if (e.getStateChange() == ItemEvent.SELECTED) {
         Object selectedItem = e.getItem();
         ExtractionConfiguration selectedConfig = null;
         if (selectedItem instanceof ExtractionConfiguration) {
             selectedConfig = (ExtractionConfiguration) selectedItem;
             log.info("Konfigurationsauswahl geändert zu: {}", selectedConfig.getName());
         } else if ("Keine".equals(selectedItem)) {
              log.info("Konfigurationsauswahl auf 'Keine' gesetzt.");
              selectedConfig = null; // Setze auf null
         }

         // Setze die Auswahl im Modell, auch wenn es null ist
         if (!Objects.equals(model.getAktiveKonfiguration(), selectedConfig)) {
              model.setAktiveKonfiguration(selectedConfig);
              // Neuverarbeitung auslösen, wenn eine Konfiguration (oder keine) ausgewählt wurde
              triggerReprocessing("Konfiguration geändert");
         }
     }
 }

 // --- Hilfsmethoden ---

 /**
  * Löst die Neuverarbeitung des aktuell ausgewählten PDFs aus.
  * Holt aktuelle Parameter und Konfiguration und ruft das Modell auf.
  * @param grund Grund für die Neuverarbeitung (für Logging).
  */
 private void triggerReprocessing(String grund) {
     PdfDokument selectedDoc = model.getAusgewaehltesDokument();
     if (selectedDoc != null && selectedDoc.getFullPath() != null) {
         Path pdfPath = null;
         try {
             pdfPath = Paths.get(selectedDoc.getFullPath());
         } catch (Exception pathEx) {
              log.error("Ungültiger Pfad für Neuverarbeitung: {}", selectedDoc.getFullPath(), pathEx);
              view.setStatus("Fehler: Ungültiger Pfad für " + selectedDoc.getSourcePdf());
              return;
         }

         if (pdfPath != null) {
             Map<String, String> aktuelleParameter = getCurrentParametersFromGui();
             ExtractionConfiguration aktiveConfig = model.getAktiveKonfiguration(); // Hole aktuelle Konfig

             log.info("({}) Starte Neuverarbeitung für PDF: {} mit Parametern: {}, Konfig: {}",
                      grund, selectedDoc.getSourcePdf(), aktuelleParameter, (aktiveConfig != null ? aktiveConfig.getName() : "Keine"));
             view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");

             // Rufe Modell auf (Methode, die Konfiguration akzeptiert)
             model.ladeUndVerarbeitePdfsMitKonfiguration(
                 Collections.singletonList(pdfPath),
                 aktuelleParameter,
                 aktiveConfig, // Übergebe die aktive Konfiguration
                 processedDoc -> { // Callback für Status nach Neuverarbeitung
                     if (processedDoc != null) {
                         log.info("Callback nach Neuverarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                         SwingUtilities.invokeLater(() -> view.setStatus("Neu verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : "")));
                     } else {
                         log.warn("Callback nach Neuverarbeitung: Dokument ist null.");
                         SwingUtilities.invokeLater(() -> view.setStatus("Fehler bei Neuverarbeitung."));
                     }
                 }
             );
         }
     } else {
         log.debug("({}) aber kein PDF ausgewählt oder Pfad fehlt. Keine Aktion.", grund);
     }
 }

 // Liest Parameter aus GUI (unverändert)
 private Map<String, String> getCurrentParametersFromGui() {
      Map<String,String> p = new HashMap<>();
      try {
          p.put("flavor", (String)view.getFlavorComboBox().getSelectedItem());
          Object v=view.getRowToleranceSpinner().getValue();
          p.put("row_tol", String.valueOf(v instanceof Number ? ((Number)v).intValue() : "2")); // Default 2
      } catch (Exception e) {
          log.error("Fehler beim Lesen der GUI-Parameter: {}", e.getMessage(), e);
          p.putIfAbsent("flavor", "lattice"); p.putIfAbsent("row_tol", "2");
      }
      return p;
 }

  // Lädt verfügbare Konfigurationen und zeigt sie in der ComboBox an
 private void loadAndDisplayAvailableConfigs() {
     log.debug("Lade verfügbare Konfigurationen...");
     List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations();
     configs.sort(Comparator.comparing(ExtractionConfiguration::getName, String.CASE_INSENSITIVE_ORDER));

     JComboBox<Object> configCombo = view.getConfigComboBox();
     if (configCombo == null) { log.error("ConfigComboBox in MainFrame nicht gefunden!"); return; }

     isProgrammaticChange = true; // Verhindere Events während des Befüllens
     try {
         ItemListener[] listeners = configCombo.getItemListeners(); for(ItemListener l : listeners) configCombo.removeItemListener(l);
         configCombo.removeAllItems();
         configCombo.addItem("Keine"); // Option für keine Konfiguration
         for (ExtractionConfiguration config : configs) { configCombo.addItem(config); } // Füge Objekte hinzu

         // Auswahl basierend auf Modell setzen
         ExtractionConfiguration activeConfig = model.getAktiveKonfiguration();
         if (activeConfig != null && configs.contains(activeConfig)) {
              configCombo.setSelectedItem(activeConfig);
         } else {
              configCombo.setSelectedItem("Keine");
              if (activeConfig != null) { model.setAktiveKonfiguration(null); } // Modell synchronisieren
         }
         for(ItemListener l : listeners) configCombo.addItemListener(l); // Listener wieder hinzufügen
     } finally {
         isProgrammaticChange = false; // Event-Handling wieder aktivieren
     }
      log.debug("Verfügbare Konfigurationen in ComboBox geladen.");
 }

 /**
  * Wählt eine Konfiguration anhand ihres Namens in der ComboBox aus.
  * @param configName Der Name der auszuwählenden Konfiguration.
  */
  private void selectConfigurationInView(String configName) {
      JComboBox<Object> configCombo = view.getConfigComboBox();
      if (configCombo == null) return;

      isProgrammaticChange = true;
      try {
          for (int i = 0; i < configCombo.getItemCount(); i++) {
              Object item = configCombo.getItemAt(i);
              if (item instanceof ExtractionConfiguration && ((ExtractionConfiguration) item).getName().equals(configName)) {
                  configCombo.setSelectedIndex(i);
                  log.debug("Konfiguration '{}' programmatisch in ComboBox ausgewählt.", configName);
                  return; // Gefunden und gesetzt
              }
          }
          // Wenn nicht gefunden, setze auf "Keine"
          configCombo.setSelectedItem("Keine");
          log.warn("Gespeicherte Konfiguration '{}' nicht in ComboBox gefunden, setze auf 'Keine'.", configName);
      } finally {
          isProgrammaticChange = false;
      }
  }
  
  /**
   * Öffnet den Konfigurationseditor-Dialog.
   * Wird aufgerufen, wenn der entsprechende Menüpunkt geklickt wird.
   */
  private void handleOpenConfigEditor(ActionEvent e) {
      log.info("Öffne Konfigurationseditor...");
      // Hole Kopie der aktiven Konfig zum Bearbeiten oder erstelle neue
      ExtractionConfiguration configToEdit = model.getAktiveKonfiguration();
      ExtractionConfiguration configForDialog;
      // Erstelle eine Kopie zum Bearbeiten, um das Original nicht direkt zu ändern
      // (Verwendet hier der Einfachheit halber Laden/Speichern als Klon-Mechanismus)
      if (configToEdit != null) {
           configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName());
           if (configForDialog == null) {
               log.warn("Konnte Konfiguration '{}' nicht neu laden, erstelle neue.", configToEdit.getName());
               configForDialog = new ExtractionConfiguration("Neue Konfiguration");
           }
      } else {
           configForDialog = new ExtractionConfiguration("Neue Konfiguration");
      }

      // Erstelle den Dialog
      ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);

      // --- KORREKTUR: Dialog sichtbar machen ---
      dialog.setVisible(true); // Zeigt den Dialog an und blockiert, bis er geschlossen wird

      // --- Code NACHDEM der Dialog geschlossen wurde ---
      ExtractionConfiguration savedConfig = dialog.getSavedConfiguration(); // Hole das Ergebnis
      if (savedConfig != null) {
          // Wenn der Benutzer gespeichert hat
          log.info("Konfiguration '{}' wurde im Dialog gespeichert/aktualisiert.", savedConfig.getName());
          // Lade die Liste der verfügbaren Konfigs neu und aktualisiere die ComboBox im Hauptfenster
          loadAndDisplayAvailableConfigs();
          // Versuche, die neue/gespeicherte Konfiguration in der ComboBox auszuwählen
          selectConfigurationInView(savedConfig.getName());
          // Setze die gespeicherte Konfiguration als die neue aktive Konfiguration im Modell
          model.setAktiveKonfiguration(savedConfig); // Löst Event aus
          // Optional: Direkte Neuverarbeitung nach Speichern auslösen
          triggerReprocessing("Konfiguration gespeichert");
      } else {
           // Wenn der Benutzer abgebrochen oder ohne Speichern geschlossen hat
           log.info("Konfigurationsdialog geschlossen ohne zu speichern.");
           // Stelle sicher, dass die Auswahl in der ComboBox mit dem Modell übereinstimmt
           synchronizeConfigComboBoxSelectionInView(); // Eigene Hilfsmethode dafür erstellen? Oder loadAndDisplay... reicht.
      }
  }

  // Hilfsmethode zum Synchronisieren (könnte auch Teil von loadAndDisplay... sein)
  private void synchronizeConfigComboBoxSelectionInView() {
       ExtractionConfiguration activeConfig = model.getAktiveKonfiguration();
       selectConfigurationInView(activeConfig != null ? activeConfig.getName() : null); // Wählt "Keine" aus, wenn Name null ist
  }
  

}