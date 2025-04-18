package de.anton.invoice.cecker.invoice_checker.controller;


import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
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

/**
* Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
* Er reagiert auf Benutzeraktionen (Button-Klicks, ComboBox-Änderungen, Parameter-Änderungen, Menüauswahl)
* und delegiert Aufgaben wie Laden, Exportieren, Konfigurationsmanagement und Auswahl-Änderungen an das Modell.
*/
public class AppController {
 private static final Logger log = LoggerFactory.getLogger(AppController.class);

 private final AnwendungsModell model;
 private final MainFrame view;
 private JFileChooser dateiAuswahlDialog;
 // Flag wird nicht mehr benötigt, da Event-Handling angepasst wurde
 // private boolean isProgrammaticChange = false;

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
     // Listener für Buttons
     view.addLadeButtonListener(this::handleLadePdfAktion);
     view.addExportButtonListener(this::handleExportExcelAktion);
     // Listener für ComboBoxen
     view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
     view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
     // Listener für Parameter-Änderungen
     view.addFlavorComboBoxListener(this::handleParameterChange);
     view.addRowToleranceSpinnerListener(this::handleParameterChange);
     // Listener für Konfiguration
     view.addConfigMenuOpenListener(this::handleOpenConfigEditor); // Listener für Menüpunkt
     view.addConfigSelectionListener(this::handleConfigSelectionChange); // Listener für Konfig-ComboBox

     // Dateiauswahldialog initialisieren
     setupDateiAuswahlDialog();
     // Lade verfügbare Konfigurationen beim Start und zeige sie in der GUI an
     updateAvailableConfigsInView();
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

 /**
  * Behandelt den Klick auf den "PDF(s) laden"-Button.
  * Öffnet einen Dateiauswahldialog und startet die asynchrone Verarbeitung der gewählten PDFs
  * unter Verwendung der aktuell eingestellten Parameter und der aktiven Konfiguration.
  * @param e Das ActionEvent (wird nicht direkt verwendet).
  */
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
              ExtractionConfiguration aktiveConfig = model.getAktiveKonfiguration(); // Hole aktuelle Konfig
              log.info("--> Verwende Parameter: {} und aktive Konfig: {}", parameter, (aktiveConfig != null ? aktiveConfig.getName() : "Keine"));

              // Rufe Modell auf und übergebe die Konfiguration explizit
              model.ladeUndVerarbeitePdfsMitKonfiguration(pdfPfade, parameter, aktiveConfig, processedDoc -> {
                   // Dieser Callback dient primär dem Update der Statusleiste
                   if (processedDoc != null) {
                       log.info("Callback nach Verarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                       // Statusmeldung im EDT aktualisieren
                       SwingUtilities.invokeLater(() -> view.setStatus("Verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : "")));
                   } else {
                        log.info("Callback empfangen (Dokument war null - evtl. übersprungen).");
                   }
               });

          } else {
               log.warn("Dateiauswahl bestätigt, aber keine Dateien ausgewählt.");
               view.setStatus("Keine Dateien ausgewählt.");
          }
      } else {
           log.info("Dateiauswahl abgebrochen.");
           view.setStatus("Dateiauswahl abgebrochen.");
      }
 }

 /**
  * Behandelt den Klick auf den "Nach Excel exportieren"-Button.
  * Öffnet einen Speichern-Dialog und startet den Export im Hintergrund.
  * @param e Das ActionEvent (wird nicht direkt verwendet).
  */
 private void handleExportExcelAktion(ActionEvent e) {
     log.info("Export nach Excel Button geklickt.");

     dateiAuswahlDialog.setDialogTitle("Excel-Datei speichern unter...");
     dateiAuswahlDialog.setMultiSelectionEnabled(false);
     dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("Excel Arbeitsmappe (*.xlsx)", "xlsx"));
     dateiAuswahlDialog.setSelectedFile(new File("Extrahierte_Tabellen.xlsx")); // Vorschlag

     int rueckgabeWert = dateiAuswahlDialog.showSaveDialog(view);
     if (rueckgabeWert == JFileChooser.APPROVE_OPTION) {
          File ausgewaehlteDatei = dateiAuswahlDialog.getSelectedFile();
          Path zielPfad = ausgewaehlteDatei.toPath();

          // Sicherstellen, dass die Dateiendung .xlsx ist
          if (!zielPfad.toString().toLowerCase().endsWith(".xlsx")) {
              zielPfad = zielPfad.resolveSibling(zielPfad.getFileName() + ".xlsx");
          }

          final Path finalZielPfad = zielPfad;
          view.setStatus("Exportiere nach Excel... Bitte warten.");
          log.info("Versuche zu exportieren nach: {}", finalZielPfad);

          // Export im Hintergrund ausführen (SwingWorker)
          SwingWorker<Void, Void> exportWorker = new SwingWorker<>() {
              private boolean erfolg = false;
              private String fehlerMeldung = null;

              @Override
              protected Void doInBackground() throws Exception {
                  try {
                      model.exportiereAlleNachExcel(finalZielPfad);
                      erfolg = true;
                  } catch (IOException ioException) {
                      log.error("Fehler beim Excel-Export: {}", ioException.getMessage(), ioException);
                      fehlerMeldung = "Fehler beim Schreiben der Datei: " + ioException.getMessage();
                      erfolg = false;
                  } catch (Exception ex) {
                      log.error("Unerwarteter Fehler beim Excel-Export: {}", ex.getMessage(), ex);
                      fehlerMeldung = "Unerwarteter Fehler: " + ex.getMessage();
                      erfolg = false;
                  }
                  return null;
              }

              @Override
              protected void done() {
                  if (erfolg) {
                      view.setStatus("Export nach " + finalZielPfad.getFileName() + " erfolgreich abgeschlossen.");
                      JOptionPane.showMessageDialog(view,
                              "Daten erfolgreich nach\n" + finalZielPfad + "\nexportiert.",
                              "Export erfolgreich",
                              JOptionPane.INFORMATION_MESSAGE);
                  } else {
                      view.setStatus("Excel-Export fehlgeschlagen. Details siehe Log.");
                       JOptionPane.showMessageDialog(view,
                              "Export fehlgeschlagen.\n" + (fehlerMeldung != null ? fehlerMeldung : "Unbekannter Fehler."),
                              "Export Fehler",
                              JOptionPane.ERROR_MESSAGE);
                  }
              }
          };
          exportWorker.execute(); // Startet den SwingWorker

     } else {
         log.info("Excel-Export abgebrochen.");
         view.setStatus("Excel-Export abgebrochen.");
     }
 }


 /**
  * Behandelt eine Änderung in der PDF-Auswahl-ComboBox.
  * Informiert das Modell über das neu ausgewählte Dokument.
  * @param e Das ActionEvent.
  */
 private void handlePdfComboBoxAuswahl(ActionEvent e) {
     // Nur auf tatsächliche Auswahländerungen durch Benutzer reagieren
     if (e.getActionCommand().equals("comboBoxChanged")) {
          JComboBox<PdfDokument> comboBox = view.getPdfComboBox();
          PdfDokument selectedDoc = (PdfDokument) comboBox.getSelectedItem();

          // Aktualisiere das Modell nur, wenn sich die Auswahl wirklich geändert hat
          // (verhindert unnötige Events beim Befüllen)
          if (!Objects.equals(model.getAusgewaehltesDokument(), selectedDoc)) {
              log.info("PDF ComboBox Auswahl geändert zu: {}", selectedDoc);
              model.setAusgewaehltesDokument(selectedDoc); // Update Modell -> löst Events aus
          }
     }
 }

 /**
  * Behandelt eine Änderung in der Tabellen-Auswahl-ComboBox.
  * Informiert das Modell über die neu ausgewählte Tabelle.
  * @param e Das ActionEvent.
  */
 private void handleTabelleComboBoxAuswahl(ActionEvent e) {
     if (e.getActionCommand().equals("comboBoxChanged")) {
         JComboBox<ExtrahierteTabelle> comboBox = view.getTabelleComboBox();
         ExtrahierteTabelle selectedTable = (ExtrahierteTabelle) comboBox.getSelectedItem();

          // Aktualisiere das Modell nur, wenn eine Tabelle ausgewählt ist und sie sich von der im Modell unterscheidet
         if (selectedTable != null && !Objects.equals(model.getAusgewaehlteTabelle(), selectedTable)) {
              log.info("Tabellen ComboBox Auswahl geändert zu: {}", selectedTable);
              model.setAusgewaehlteTabelle(selectedTable); // Update Modell -> löst Event für View aus
          } else if (selectedTable == null && model.getAusgewaehlteTabelle() != null) {
              // Falls Auswahl auf "nichts" geht (sollte nicht passieren bei ComboBox ohne leeren Eintrag)
              log.warn("Tabellen ComboBox Auswahl ist null, setze Modell zurück.");
              model.setAusgewaehlteTabelle(null);
          }
     }
 }

 /**
  * Gemeinsamer Listener für Änderungen an den Parameter-Steuerelementen (Flavor, Row Tol).
  * Löst die Neuverarbeitung des aktuell ausgewählten PDFs aus.
  * @param e Das auslösende Event (ActionEvent oder AWTEvent).
  */
 private void handleParameterChange(AWTEvent e) {
     // if (isProgrammaticChange) return; // Optional: Schutz vor programmatischen Änderungen
     String sourceName = (e != null && e.getSource() != null) ? e.getSource().getClass().getSimpleName() : "Unbekannt";
     log.debug("Parameteränderung erkannt von: {}", sourceName);
     // Verzögere die Verarbeitung leicht, um mehrfache Events (Spinner) zu bündeln
     SwingUtilities.invokeLater(() -> triggerReprocessing("Parameter geändert"));
 }

 /**
   * Überschriebene Methode für ChangeEvents vom JSpinner.
   * Ruft den allgemeinen Parameter-Handler auf.
   * @param e Das ChangeEvent.
   */
 private void handleParameterChange(ChangeEvent e) {
     // Rufe den allgemeinen Handler auf (Event selbst wird nicht benötigt)
     handleParameterChange((AWTEvent)null);
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
     // Lade eine frische Kopie über den Service, um Änderungen zu isolieren
     if (configToEdit != null) {
          configForDialog = model.getConfigurationService().loadConfiguration(configToEdit.getName());
          if (configForDialog == null) { // Fallback, falls Laden fehlschlägt
              log.warn("Konnte Konfiguration '{}' nicht neu laden, erstelle neue.", configToEdit.getName());
              configForDialog = new ExtractionConfiguration("Neue Konfiguration");
          }
     } else {
          configForDialog = new ExtractionConfiguration("Neue Konfiguration");
     }

     // Erstelle und zeige den Dialog
     ConfigurationDialog dialog = new ConfigurationDialog(view, model.getConfigurationService(), configForDialog);
     dialog.setVisible(true); // Blockiert bis Dialog geschlossen

     // Nach dem Schließen des Dialogs:
     ExtractionConfiguration savedConfig = dialog.getSavedConfiguration(); // Hole das Ergebnis
     if (savedConfig != null) {
         // Wenn der Benutzer gespeichert hat
         log.info("Konfiguration '{}' wurde im Dialog gespeichert/aktualisiert.", savedConfig.getName());
         // Konfigurationsliste und Auswahl in der Haupt-GUI neu laden/aktualisieren
         updateAvailableConfigsInView();
         // Setze die gespeicherte Konfiguration als die neue aktive Konfiguration im Modell
         model.setAktiveKonfiguration(savedConfig); // Löst Event aus
         // Optional: Direkte Neuverarbeitung nach Speichern auslösen
         triggerReprocessing("Konfiguration gespeichert");
     } else {
          // Wenn der Benutzer abgebrochen oder ohne Speichern geschlossen hat
          log.info("Konfigurationsdialog geschlossen ohne zu speichern.");
          // Stelle sicher, dass die ComboBox-Auswahl noch mit dem (unveränderten) Modell übereinstimmt
          updateAvailableConfigsInView(); // Synchronisiert die Auswahl
     }
 }

 /**
   * Wird aufgerufen, wenn in der Konfigurations-ComboBox eine Auswahl getroffen wird.
   * Setzt die ausgewählte Konfiguration im Modell als aktiv und löst Neuverarbeitung aus.
   * @param e Das ItemEvent.
   */
 private void handleConfigSelectionChange(ItemEvent e) {
     // Nur auf Auswahl-Events reagieren
     if (e.getStateChange() == ItemEvent.SELECTED) {
         Object selectedItem = e.getItem();
         ExtractionConfiguration selectedConfig = null; // Wird auf null gesetzt, wenn "Keine" gewählt wird

         if (selectedItem instanceof ExtractionConfiguration) {
             selectedConfig = (ExtractionConfiguration) selectedItem;
             log.info("Konfigurationsauswahl geändert zu: {}", selectedConfig.getName());
         } else if ("Keine".equals(selectedItem)) {
              log.info("Konfigurationsauswahl auf 'Keine' gesetzt.");
              selectedConfig = null;
         } else {
              log.warn("Unerwartetes Objekt in Konfig-ComboBox ausgewählt: {}", selectedItem);
              return; // Keine Aktion bei unerwarteten Objekten
         }

         // Setze die Auswahl im Modell, nur wenn sie sich geändert hat
         if (!Objects.equals(model.getAktiveKonfiguration(), selectedConfig)) {
              model.setAktiveKonfiguration(selectedConfig); // Löst ACTIVE_CONFIG_PROPERTY Event aus
              // Löse Neuverarbeitung des aktuellen PDFs mit der neuen Konfiguration aus
              triggerReprocessing("Konfiguration geändert");
         } else {
              log.debug("Ausgewählte Konfiguration ist bereits aktiv. Keine Modelländerung.");
         }
     }
 }


 // --- Hilfsmethoden ---

 /**
  * Löst die Neuverarbeitung des aktuell im Modell ausgewählten PDF-Dokuments
  * mit den aktuell in der GUI eingestellten Parametern und der im Modell aktiven Konfiguration aus.
  * @param grund Ein String, der den Grund für die Neuverarbeitung beschreibt (für Logging).
  */
 private void triggerReprocessing(String grund) {
     PdfDokument selectedDoc = model.getAusgewaehltesDokument();
     // Nur ausführen, wenn ein Dokument ausgewählt ist und einen gültigen Pfad hat
     if (selectedDoc != null && selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
         Path pdfPath = null;
         try {
             pdfPath = Paths.get(selectedDoc.getFullPath());
         } catch (Exception pathEx) {
              log.error("Ungültiger Pfad für Neuverarbeitung: {}", selectedDoc.getFullPath(), pathEx);
              view.setStatus("Fehler: Ungültiger Pfad für " + selectedDoc.getSourcePdf());
              return; // Abbruch bei ungültigem Pfad
         }

         if (pdfPath != null) {
             // Lies die *aktuellen* Parameter aus der GUI
             Map<String, String> aktuelleParameter = getCurrentParametersFromGui();
             // Hole die *aktuell aktive* Konfiguration aus dem Modell
             ExtractionConfiguration aktiveConfig = model.getAktiveKonfiguration();

             log.info("({}) Starte Neuverarbeitung für PDF: {} mit Parametern: {}, Konfig: {}",
                      grund, selectedDoc.getSourcePdf(), aktuelleParameter, (aktiveConfig != null ? aktiveConfig.getName() : "Keine"));
             view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' neu...");

             // Rufe Modell auf, um *nur dieses eine PDF* neu zu verarbeiten,
             // übergib die explizite Konfiguration (könnte null sein)
             model.ladeUndVerarbeitePdfsMitKonfiguration(
                 Collections.singletonList(pdfPath), // Erzeuge Liste mit nur diesem einen Pfad
                 aktuelleParameter,
                 aktiveConfig, // Übergib die aktuell aktive Konfig
                 processedDoc -> { // Callback für Status-Update nach Neuverarbeitung
                     if (processedDoc != null) {
                         log.info("Callback nach Neuverarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                         SwingUtilities.invokeLater(() -> view.setStatus("Neu verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : "")));
                         // Die GUI sollte sich durch die vom Modell gefeuerten Events (DOCUMENTS_UPDATED, SELECTED_TABLE) automatisch aktualisieren
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

 /**
  * Liest die aktuellen Werte der Parameter-Steuerelemente aus der GUI aus
  * und gibt sie als Map zurück. Beinhaltet Fehlerbehandlung und Defaults.
  * @return Eine Map mit den aktuellen Parametern (z.B. "flavor", "row_tol").
  */
 private Map<String, String> getCurrentParametersFromGui() {
     Map<String, String> parameter = new HashMap<>();
     try {
         // Lies Flavor aus ComboBox
         Object selectedFlavor = view.getFlavorComboBox().getSelectedItem();
         parameter.put("flavor", selectedFlavor instanceof String ? (String)selectedFlavor : "lattice"); // Default "lattice"

         // Lies Row Tolerance aus Spinner
         Object rowTolValue = view.getRowToleranceSpinner().getValue();
         if (rowTolValue instanceof Number) {
             parameter.put("row_tol", String.valueOf(((Number) rowTolValue).intValue()));
         } else {
              // Fallback oder Standardwert, falls der Wert unerwartet ist
              parameter.put("row_tol", "2"); // Setze einen Default
              log.warn("Unerwarteter Werttyp vom RowToleranceSpinner: {}. Verwende Default '2'.", rowTolValue);
         }
          // Fügen Sie hier das Auslesen weiterer Parameter hinzu, falls Sie welche implementieren
          // Beispiel: parameter.put("col_tol", String.valueOf(((Number)view.getColTolSpinner().getValue()).intValue()));
     } catch (Exception e) {
          // Fange unerwartete Fehler beim Auslesen der GUI ab
          log.error("Fehler beim Auslesen der GUI-Parameter: {}", e.getMessage(), e);
          // Setze sichere Defaults im Fehlerfall
          parameter.putIfAbsent("flavor", "lattice");
          parameter.putIfAbsent("row_tol", "2");
     }
     return parameter;
 }

  /**
   * Lädt die Liste der verfügbaren Konfigurationen vom Service und weist die View an,
   * ihre Konfigurations-ComboBox zu aktualisieren.
   */
  private void updateAvailableConfigsInView() {
      log.debug("Aktualisiere Konfigurations-ComboBox in der View...");
      // Hole alle gespeicherten Konfigurationen
      List<ExtractionConfiguration> configs = model.getConfigurationService().loadAllConfigurations();
      // Hole die aktuell im Modell aktive Konfiguration
      ExtractionConfiguration activeConfig = model.getAktiveKonfiguration();
      // Rufe die Update-Methode der View auf und übergebe die Daten
      view.updateConfigurationComboBox(configs, activeConfig);
  }
}