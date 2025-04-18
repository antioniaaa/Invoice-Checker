package de.anton.invoice.cecker.invoice_checker.controller;

// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.PdfDokument;
import de.anton.invoice.cecker.invoice_checker.view.MainFrame;

// Swing-Komponenten und Event-Handling
import javax.swing.*;
import javax.swing.event.ChangeEvent;     // Für JSpinner
import javax.swing.event.ChangeListener;  // Für JSpinner
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.AWTEvent; // Import für allgemeines AWTEvent
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths; // Import hinzugefügt
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // Für Collections.singletonList
import java.util.HashMap;     // Für Parameter Map
import java.util.List;
import java.util.Map;     // Für Parameter Map
import java.util.Objects; // Für Vergleich

/**
 * Der Controller verbindet die View (MainFrame) mit dem Model (AnwendungsModell).
 * Er reagiert auf Benutzeraktionen (Button-Klicks, ComboBox-Änderungen, Parameter-Änderungen)
 * und delegiert Aufgaben wie Laden, Exportieren und Auswahl-Änderungen an das Modell.
 */
public class AppController {
    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    private final AnwendungsModell model;
    private final MainFrame view;
    private JFileChooser dateiAuswahlDialog;
    // Flag, um zu verhindern, dass programmgesteuerte Änderungen Events auslösen (optional)
    private boolean isProgrammaticChange = false;

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
     */
    private void initController() {
        // Listener für Buttons
        view.addLadeButtonListener(this::handleLadePdfAktion);
        view.addExportButtonListener(this::handleExportExcelAktion);
        // Listener für ComboBoxen
        view.addPdfComboBoxListener(this::handlePdfComboBoxAuswahl);
        view.addTabelleComboBoxListener(this::handleTabelleComboBoxAuswahl);
        // Listener für Parameter-Änderungen
        view.addFlavorComboBoxListener(this::handleParameterChange); // Gleicher Handler für beide
        view.addRowToleranceSpinnerListener(this::handleParameterChange); // Gleicher Handler für beide
        // Dateiauswahldialog initialisieren
        setupDateiAuswahlDialog();
    }

    /**
     * Konfiguriert den JFileChooser.
     */
    private void setupDateiAuswahlDialog() {
        dateiAuswahlDialog = new JFileChooser();
        dateiAuswahlDialog.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
        dateiAuswahlDialog.setAcceptAllFileFilterUsed(false);
    }

    /**
     * Behandelt den Klick auf den "PDF(s) laden"-Button.
     * Öffnet einen Dateiauswahldialog und startet die asynchrone Verarbeitung der gewählten PDFs.
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
                 // Statusmeldung korrekt formatieren
                 view.setStatus("Verarbeite " + pdfPfade.size() + " PDF(s)...");

                 // Aktuelle Parameter aus der GUI lesen
                 Map<String, String> parameter = getCurrentParametersFromGui();
                 log.info("--> Verwende Parameter für initiales Laden: {}", parameter);

                 // Modell aufrufen, um Dateien asynchron zu verarbeiten, Parameter übergeben
                 model.ladeUndVerarbeitePdfs(pdfPfade, parameter, processedDoc -> {
                      // Dieser Callback dient primär dem Update der Statusleiste
                      if (processedDoc != null) {
                          log.info("Callback nach Verarbeitung empfangen für: {}", processedDoc.getSourcePdf());
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
        dateiAuswahlDialog.setMultiSelectionEnabled(false); // Nur eine Datei speichern
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

             // Export im Hintergrund ausführen (SwingWorker), um die GUI nicht zu blockieren
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
                     // Wird im EDT ausgeführt nach Beendigung von doInBackground
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
        // Nur auf tatsächliche Auswahländerungen reagieren
        if (e.getActionCommand().equals("comboBoxChanged")) {
             JComboBox<PdfDokument> comboBox = view.getPdfComboBox();
             PdfDokument selectedDoc = (PdfDokument) comboBox.getSelectedItem();

             // Aktualisiere das Modell nur, wenn sich die Auswahl wirklich geändert hat
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
     * @param e Das auslösende Event (ActionEvent für ComboBox, AWTEvent für Spinner-Wrapper).
     */
    private void handleParameterChange(AWTEvent e) {
         if (isProgrammaticChange) { // Verhindert Auslösung durch Code (optional)
             return;
         }
         // Ermittle Quelle, falls benötigt (optional)
         String sourceName = (e != null && e.getSource() != null) ? e.getSource().getClass().getSimpleName() : "Unbekannt";
         log.debug("Parameteränderung erkannt von: {}", sourceName);

         // Verzögere die Verarbeitung leicht, um mehrere schnelle Events (z.B. vom Spinner) abzufangen.
         // Eine robustere Lösung wäre Debouncing, aber invokeLater reicht oft aus.
         SwingUtilities.invokeLater(this::triggerReprocessing);
    }

     /**
      * Überschriebene Methode für ChangeEvents vom JSpinner.
      * Ruft den allgemeinen Parameter-Handler auf.
      * @param e Das ChangeEvent.
      */
    private void handleParameterChange(ChangeEvent e) {
        // Wir brauchen hier keine spezifische Logik für das ChangeEvent,
        // rufen einfach den allgemeinen Handler auf.
        handleParameterChange((AWTEvent)null); // Übergibt null, da das Event selbst nicht gebraucht wird
    }

    /**
     * Löst die Neuverarbeitung des aktuell im Modell ausgewählten PDF-Dokuments
     * mit den aktuell in der GUI eingestellten Parametern aus.
     */
    private void triggerReprocessing() {
        PdfDokument selectedDoc = model.getAusgewaehltesDokument();
        // Nur ausführen, wenn ein Dokument ausgewählt ist
        if (selectedDoc != null) {
            Path pdfPath = null;
            // Hole den Pfad sicher
            if (selectedDoc.getFullPath() != null && !selectedDoc.getFullPath().isBlank()) {
                try {
                    pdfPath = Paths.get(selectedDoc.getFullPath());
                } catch (Exception pathEx) {
                     log.error("Ungültiger Pfad im ausgewählten Dokument: {}", selectedDoc.getFullPath(), pathEx);
                     view.setStatus("Fehler: Ungültiger Pfad für " + selectedDoc.getSourcePdf());
                     return; // Abbruch
                }
            }

            if (pdfPath != null) {
                // Lies die *aktuellen* Parameter aus der GUI
                Map<String, String> neueParameter = getCurrentParametersFromGui();
                log.info("Parameter geändert. Starte Neuverarbeitung für PDF: {} mit Parametern: {}", selectedDoc.getSourcePdf(), neueParameter);
                view.setStatus("Verarbeite '" + selectedDoc.getSourcePdf() + "' mit neuen Parametern...");

                // Rufe Modell auf, um *nur dieses eine PDF* neu zu verarbeiten
                model.ladeUndVerarbeitePdfs(
                    Collections.singletonList(pdfPath), // Erzeuge Liste mit nur diesem einen Pfad
                    neueParameter,
                    processedDoc -> { // Callback für Status-Update nach Neuverarbeitung
                        if (processedDoc != null) {
                            log.info("Callback nach Neuverarbeitung empfangen für: {}", processedDoc.getSourcePdf());
                            SwingUtilities.invokeLater(() -> view.setStatus("Neu verarbeitet: " + processedDoc.getSourcePdf() + (processedDoc.getError() != null ? " [FEHLER]" : "")));
                            // Die GUI sollte sich durch die vom Modell gefeuerten Events automatisch aktualisieren
                        } else {
                            log.warn("Callback nach Neuverarbeitung: Dokument ist null.");
                            SwingUtilities.invokeLater(() -> view.setStatus("Fehler bei Neuverarbeitung."));
                        }
                    }
                );
            } else {
                log.warn("Kann Neuverarbeitung nicht starten: Pfad für ausgewähltes Dokument ('{}') fehlt oder ist ungültig.", selectedDoc.getSourcePdf());
                view.setStatus("Fehler: Pfad für " + selectedDoc.getSourcePdf() + " fehlt.");
            }
        } else {
            log.debug("Parameter geändert, aber kein PDF ausgewählt. Keine Aktion.");
            // Optional: Statusmeldung, dass kein PDF ausgewählt ist
            // view.setStatus("Parameter geändert. Bitte wählen Sie zuerst ein PDF aus.");
        }
    }

    /**
     * Liest die aktuellen Werte der Parameter-Steuerelemente aus der GUI aus
     * und gibt sie als Map zurück. Beinhaltet Fehlerbehandlung.
     * @return Eine Map mit den aktuellen Parametern (z.B. "flavor", "row_tol").
     */
    private Map<String, String> getCurrentParametersFromGui() {
        Map<String, String> parameter = new HashMap<>();
        try {
            // Lies Flavor aus ComboBox
            Object selectedFlavor = view.getFlavorComboBox().getSelectedItem();
            parameter.put("flavor", selectedFlavor != null ? (String)selectedFlavor : "lattice"); // Default "lattice"

            // Lies Row Tolerance aus Spinner
            Object rowTolValue = view.getRowToleranceSpinner().getValue();
            if (rowTolValue instanceof Number) {
                // Konvertiere den Spinner-Wert (Number) sicher zu String
                parameter.put("row_tol", String.valueOf(((Number) rowTolValue).intValue()));
            } else {
                 // Fallback oder Standardwert, falls der Wert unerwartet ist
                 parameter.put("row_tol", "2"); // Setze einen Default, falls nötig
                 log.warn("Unerwarteter Werttyp vom RowToleranceSpinner: {}. Verwende Default '2'.", rowTolValue);
            }
             // Fügen Sie hier das Auslesen weiterer Parameter hinzu, falls Sie welche implementieren
             // Beispiel: parameter.put("col_tol", String.valueOf(view.getColTolSpinner().getValue()));
        } catch (Exception e) {
             // Fange unerwartete Fehler beim Auslesen der GUI ab
             log.error("Fehler beim Auslesen der GUI-Parameter: {}", e.getMessage(), e);
             // Setze sichere Defaults im Fehlerfall
             parameter.putIfAbsent("flavor", "lattice");
             parameter.putIfAbsent("row_tol", "2");
        }
        return parameter;
    }
}