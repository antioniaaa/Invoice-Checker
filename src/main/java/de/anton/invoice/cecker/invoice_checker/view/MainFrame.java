package de.anton.invoice.cecker.invoice_checker.view;

// Benötigte Swing und AWT Klassen
import javax.swing.*;
import javax.swing.event.ChangeListener; // Für JSpinner Listener
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

// Für PropertyChangeListener (MVC)
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.PdfDokument;

// Hilfsklassen und Java Util
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.Objects;



/**
 * Das Hauptfenster (View) der Anwendung.
 * Zeigt die Bedienelemente (Laden, Export, PDF-/Tabellenauswahl, Parameter)
 * und die extrahierte Tabelle an. Lauscht auf Änderungen im Modell.
 */
public class MainFrame extends JFrame implements PropertyChangeListener {
    private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

    private final AnwendungsModell model;
    // GUI Elemente
    private JButton ladePdfButton;
    private JButton exportExcelButton;
    private JComboBox<PdfDokument> pdfComboBox;
    private JComboBox<ExtrahierteTabelle> tabelleComboBox;
    private JTable datenTabelle;
    private DefaultTableModel tabellenModell;
    private JLabel statusLabel;
    // Parameter Elemente
    private JComboBox<String> flavorComboBox;
    private JSpinner rowToleranceSpinner;
    private JLabel rowToleranceLabel;


    public MainFrame(AnwendungsModell model) {
        this.model = model;
        this.model.addPropertyChangeListener(this); // Auf Modelländerungen lauschen

        setTitle("PDF Tabellen Extraktor");
        setSize(1100, 700); // Breite angepasst für Parameter
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Schließen im Listener behandeln
        setLocationRelativeTo(null); // Fenster zentrieren

        initKomponenten();
        layoutKomponenten();

        // Listener zum sauberen Behandeln des Fenster-Schließens
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log.info("Fenster-Schließen-Ereignis erkannt.");
                model.shutdownExecutor(); // Sicherstellen, dass Hintergrund-Threads gestoppt werden
                dispose(); // Fenster schließen
                System.exit(0); // Anwendung beenden
            }
        });
    }

    /**
     * Initialisiert alle Swing-Komponenten des Fensters.
     */
    private void initKomponenten() {
        ladePdfButton = new JButton("PDF(s) laden");
        exportExcelButton = new JButton("Nach Excel exportieren");
        exportExcelButton.setEnabled(false); // Initial deaktiviert

        pdfComboBox = new JComboBox<>();
        tabelleComboBox = new JComboBox<>();
        tabelleComboBox.setEnabled(false); // Initial deaktiviert

        // Parameter Komponenten initialisieren
        flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"}); // Optionen für Flavor
        flavorComboBox.setSelectedItem("lattice"); // Default
        // Spinner für numerische Eingabe (row_tol), erlaubt nur positive Zahlen >= 0
        // Default 2, Min 0, Max z.B. 100 (anpassbar), Step 1
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1);
        rowToleranceSpinner = new JSpinner(spinnerModel);
        rowToleranceLabel = new JLabel("Row Tol (Stream):"); // Label angepasst

        // Tabelle initialisieren
        tabellenModell = new DefaultTableModel();
        datenTabelle = new JTable(tabellenModell);
        datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Horizontales Scrollen erlauben

        // Statusleiste
        statusLabel = new JLabel("Bereit. Laden Sie PDFs, um zu starten.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    }

    /**
     * Ordnet die initialisierten Komponenten im Fenster an (Layout).
     */
    private void layoutKomponenten() {
        // Top Panel für die Hauptsteuerlemente
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS)); // Horizontale Anordnung
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // Außenabstand

        // Linke Steuerlemente
        topPanel.add(ladePdfButton);
        topPanel.add(Box.createHorizontalStrut(10)); // Abstand
        topPanel.add(exportExcelButton);
        topPanel.add(Box.createHorizontalStrut(20)); // Größerer Abstand
        topPanel.add(new JLabel("PDF:"));
        topPanel.add(pdfComboBox);
        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(new JLabel("Tabelle:"));
        topPanel.add(tabelleComboBox);

        // Platzhalter, der sich ausdehnt und Parameter nach rechts schiebt
        topPanel.add(Box.createHorizontalGlue());

        // Rechtes Panel für die Parameter
        JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); // Links ausgerichtet, wenig vertikaler Abstand
        parameterPanel.setBorder(BorderFactory.createTitledBorder("Parameter")); // Rahmen mit Titel
        parameterPanel.add(new JLabel("Flavor:"));
        parameterPanel.add(flavorComboBox);
        parameterPanel.add(Box.createHorizontalStrut(10));
        parameterPanel.add(rowToleranceLabel);
        // Setze bevorzugte Größe für Spinner, damit er nicht zu breit wird
        rowToleranceSpinner.setPreferredSize(new Dimension(60, rowToleranceSpinner.getPreferredSize().height));
        parameterPanel.add(rowToleranceSpinner);
        topPanel.add(parameterPanel);

        // Hauptbereich für die Tabelle
        JScrollPane tableScrollPane = new JScrollPane(datenTabelle);

        // Gesamtlayout des Frames
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH); // Obere Leiste
        add(tableScrollPane, BorderLayout.CENTER); // Tabelle in der Mitte
        add(statusLabel, BorderLayout.SOUTH); // Statusleiste unten
    }

    // --- Methoden für den Controller (um Listener zu registrieren) ---
    public void addLadeButtonListener(ActionListener listener) { ladePdfButton.addActionListener(listener); }
    public void addExportButtonListener(ActionListener listener) { exportExcelButton.addActionListener(listener); }
    public void addPdfComboBoxListener(ActionListener listener) { pdfComboBox.addActionListener(listener); }
    public void addTabelleComboBoxListener(ActionListener listener) { tabelleComboBox.addActionListener(listener); }
    // Methoden zum Hinzufügen von Listenern für Parameter-Komponenten
    public void addFlavorComboBoxListener(ActionListener listener) { flavorComboBox.addActionListener(listener); }
    public void addRowToleranceSpinnerListener(ChangeListener listener) { rowToleranceSpinner.addChangeListener(listener); }

    // --- Getter für Komponenten (damit Controller darauf zugreifen kann) ---
    public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
    public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
    public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
    public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }

    // --- Methoden zur Aktualisierung der UI-Komponenten (werden vom PropertyChangeListener aufgerufen) ---

    /**
     * Aktualisiert die PDF-ComboBox mit der Liste der geladenen Dokumente aus dem Modell.
     * Wählt automatisch das erste Element aus, wenn die Liste nicht leer ist.
     */
    private void updatePdfComboBox() {
        log.info("MainFrame.updatePdfComboBox wird aufgerufen.");
        Object selectedPdf = pdfComboBox.getSelectedItem(); // Aktuelle Auswahl merken

        // Listener temporär entfernen
        ActionListener[] pdfListeners = pdfComboBox.getActionListeners();
        for (ActionListener l : pdfListeners) pdfComboBox.removeActionListener(l);

        pdfComboBox.removeAllItems(); // Leeren
        List<PdfDokument> dokumente = model.getDokumente(); // Aktuelle Liste holen
        log.debug("--> Fülle PDF ComboBox mit {} Dokumenten.", (dokumente != null ? dokumente.size() : 0));
        boolean hatPdfElemente = false;
        if (dokumente != null && !dokumente.isEmpty()) {
            for (PdfDokument doc : dokumente) {
                pdfComboBox.addItem(doc);
            }
            exportExcelButton.setEnabled(true); // Export ermöglichen
            hatPdfElemente = true;
        } else {
            exportExcelButton.setEnabled(false); // Export nicht möglich
        }

        // Auswahl wiederherstellen oder erste Auswahl treffen
        boolean selectionSet = false;
        if (selectedPdf instanceof PdfDokument && dokumente != null && dokumente.contains(selectedPdf)) {
            // Wenn das vorher ausgewählte Element noch existiert, wähle es wieder aus
            pdfComboBox.setSelectedItem(selectedPdf);
            log.debug("--> PDF Auswahl wiederhergestellt: {}", selectedPdf);
            selectionSet = true;
        } else if (hatPdfElemente) {
            // Wenn es Elemente gibt, aber die alte Auswahl weg ist (oder es keine gab), wähle das erste
            PdfDokument erstesElement = dokumente.get(0);
            pdfComboBox.setSelectedItem(erstesElement);
            log.info("--> Setze erstes PDF '{}' als ausgewählt in ComboBox.", erstesElement.getSourcePdf());
            // Das Modell wird über den Listener oder das initale Setzen im Modell selbst aktualisiert.
            selectionSet = true;
        }

        // Wenn keine Auswahl gesetzt wurde (weil Liste leer), stelle sicher, dass Modellauswahl auch null ist
        if (!selectionSet && model.getAusgewaehltesDokument() != null) {
             log.info("--> Keine PDFs mehr in ComboBox, lösche Auswahl im Modell.");
             model.setAusgewaehltesDokument(null); // Löst Event aus -> updateTabelleComboBox etc.
        }

        // Listener wieder hinzufügen
        for (ActionListener l : pdfListeners) pdfComboBox.addActionListener(l);
        log.debug("PDF ComboBox Update abgeschlossen.");
    }

    /**
     * Aktualisiert die Tabellen-ComboBox basierend auf dem aktuell im Modell ausgewählten PDF.
     */
    private void updateTabelleComboBox() {
        log.info("MainFrame.updateTabelleComboBox wird aufgerufen.");
        Object selectedTable = tabelleComboBox.getSelectedItem(); // Aktuelle Tabellenauswahl merken

        // Listener entfernen
        ActionListener[] tableListeners = tabelleComboBox.getActionListeners();
        for(ActionListener l : tableListeners) tabelleComboBox.removeActionListener(l);

        tabelleComboBox.removeAllItems(); // Leeren
        tabelleComboBox.setEnabled(false); // Standardmäßig deaktivieren

        // Prüfe, ob ein PDF im Modell ausgewählt ist
        PdfDokument currentPdf = model.getAusgewaehltesDokument();
        if (currentPdf != null) {
            List<ExtrahierteTabelle> tabellen = model.getVerfuegbareTabellen(); // Hole verfügbare Tabellen
            log.debug("--> Fülle Tabellen ComboBox mit {} Tabellen für PDF '{}'.", tabellen.size(), currentPdf.getSourcePdf());
            if (!tabellen.isEmpty()) {
                // Füge alle gefundenen Tabellen hinzu
                for (ExtrahierteTabelle tabelle : tabellen) {
                    tabelleComboBox.addItem(tabelle); // Verwendet tabelle.toString() für die Anzeige
                }
                tabelleComboBox.setEnabled(true); // Aktiviere ComboBox

                // Setze die Auswahl in der ComboBox basierend auf dem Modellzustand
                ExtrahierteTabelle modelSelectedTable = model.getAusgewaehlteTabelle();
                if (modelSelectedTable != null && tabellen.contains(modelSelectedTable)) {
                     // Wenn das Modell eine gültige Auswahl hat, setze sie in der ComboBox
                     tabelleComboBox.setSelectedItem(modelSelectedTable);
                     log.debug("--> Setze Tabellen-ComboBox Auswahl auf Tabelle aus Modell: {}", modelSelectedTable);
                } else if (!tabellen.isEmpty()){
                     // Fallback: Wähle das erste Element aus, wenn das Modell keine (gültige) Auswahl hat
                     ExtrahierteTabelle erstesElement = tabellen.get(0);
                     tabelleComboBox.setSelectedItem(erstesElement); // Setze Auswahl in der GUI
                     log.warn("--> Modell hatte keine gültige Tabellenauswahl, setze erstes Element '{}' in Tabellen-ComboBox.", erstesElement);
                     // Informiere das Modell über diese Auswahl, falls es abweicht (sollte nicht passieren)
                     if (!Objects.equals(model.getAusgewaehlteTabelle(), erstesElement)) {
                          model.setAusgewaehlteTabelle(erstesElement);
                     }
                }
            } else {
                 log.debug("--> Keine Tabellen für das ausgewählte PDF gefunden.");
                 // ComboBox bleibt leer und deaktiviert
            }
        } else {
            log.debug("--> Kein PDF ausgewählt, Tabellen ComboBox bleibt leer/deaktiviert.");
             // Stelle sicher, dass auch im Modell keine Tabelle ausgewählt ist
             if (model.getAusgewaehlteTabelle() != null) {
                 model.setAusgewaehlteTabelle(null);
             }
        }

         // Listener wieder hinzufügen
        for(ActionListener l : tableListeners) tabelleComboBox.addActionListener(l);
        log.debug("Tabellen ComboBox Update abgeschlossen.");
    }


    /**
     * Aktualisiert die JTable (datenTabelle) mit den Daten der aktuell im Modell ausgewählten Tabelle.
     * Beinhaltet auch die automatische Anpassung und Verdopplung der Spaltenbreiten.
     */
 // In MainFrame.java -> updateDatenTabelle

    private void updateDatenTabelle() {
        log.info("MainFrame.updateDatenTabelle wird aufgerufen für ausgewählte Tabelle: {}", model.getAusgewaehlteTabelle());
        Optional<List<List<String>>> tabellenDatenOpt = model.getAusgewaehlteTabellenDaten();

        if (tabellenDatenOpt.isPresent()) {
            List<List<String>> tabellenDaten = tabellenDatenOpt.get();
            log.debug("--> Tabellendaten erhalten ({} Zeilen)", tabellenDaten.size());
            if (!tabellenDaten.isEmpty() && tabellenDaten.size() > 0) { // Zeige auch an, wenn nur Header da ist
                Vector<String> headers = new Vector<>(tabellenDaten.get(0));
                Vector<Vector<Object>> datenVektor = new Vector<>();
                for (int i = 1; i < tabellenDaten.size(); i++) {
                    datenVektor.add(new Vector<>(tabellenDaten.get(i)));
                }
                log.info("---> Setze Daten für Tabelle: {} Datenzeilen, {} Spalten (Header: {})",
                         datenVektor.size(), headers.size(), headers);
                // Setze Daten UND Header neu
                tabellenModell.setDataVector(datenVektor, headers);
                setStatus("Zeige Tabelle: " + model.getAusgewaehlteTabelle());

                TabellenSpaltenAnpasser tca = new TabellenSpaltenAnpasser(datenTabelle);
                tca.adjustColumns();
                TableColumnModel columnModel = datenTabelle.getColumnModel();
                for (int i = 0; i < columnModel.getColumnCount(); i++) {
                    TableColumn column = columnModel.getColumn(i);
                    int aktuelleBreite = column.getPreferredWidth();
                    int neueBreite = aktuelleBreite * 2;
                    column.setPreferredWidth(neueBreite);
                }
                log.debug("---> Alle Spaltenbreiten nach Verdopplung angepasst.");

            } else {
                 log.warn("--> Tabellendaten sind komplett leer. Leere Tabelle.");
                 // KORREKTUR: Explizit leere Daten und leere Header setzen
                 tabellenModell.setDataVector(new Vector<>(), new Vector<>());
                 setStatus("Ausgewählte Tabelle hat keine Daten oder Header: " + model.getAusgewaehlteTabelle());
            }
        } else {
            log.warn("--> Keine Tabellendaten vom Modell erhalten (Optional ist leer). Leere Tabelle.");
            // KORREKTUR: Explizit leere Daten und leere Header setzen
            tabellenModell.setDataVector(new Vector<>(), new Vector<>());
            // Setze passende Statusmeldung
            if (model.getAusgewaehltesDokument() != null && model.getAusgewaehlteTabelle() != null) {
                 setStatus("Keine Daten verfügbar für Tabelle: " + model.getAusgewaehlteTabelle());
            } else if (model.getAusgewaehltesDokument()!= null) {
                 setStatus("Keine Tabelle für '" + model.getAusgewaehltesDokument().getSourcePdf() + "' ausgewählt oder gefunden.");
            }
            else {
                 setStatus("Kein PDF ausgewählt.");
            }
        }
    }

    /**
     * Setzt den Text im StatusLabel (stellt sicher, dass dies im EDT geschieht).
     * @param nachricht Die anzuzeigende Nachricht.
     */
    public void setStatus(String nachricht) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(nachricht));
    }

    // --- PropertyChangeListener Implementierung ---
    /**
     * Reagiert auf PropertyChangeEvents vom Modell (AnwendungsModell).
     * Aktualisiert die entsprechenden GUI-Komponenten im Event Dispatch Thread.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        log.debug("MainFrame.propertyChange empfangen: Eigenschaft='{}'", propertyName);

        // Führe GUI-Updates immer im Event Dispatch Thread aus
        SwingUtilities.invokeLater(() -> {
             switch (propertyName) {
                 case AnwendungsModell.DOCUMENTS_UPDATED_PROPERTY:
                     // Die Liste der Dokumente wurde geändert (hinzugefügt, entfernt, neu sortiert)
                     log.info("-> propertyChange: Aktualisiere PDF ComboBox wegen '{}'.", propertyName);
                     updatePdfComboBox(); // Aktualisiert die PDF-Auswahl
                     break;
                 case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                     // Das im Modell ausgewählte PDF-Dokument hat sich geändert
                     log.info("-> propertyChange: Aktualisiere Tabellen ComboBox wegen '{}'.", propertyName);
                     updateTabelleComboBox(); // Aktualisiert die Liste der verfügbaren Tabellen
                     // Die Tabelle selbst wird durch das nachfolgende SELECTED_TABLE_PROPERTY Event aktualisiert
                     break;
                 case AnwendungsModell.SELECTED_TABLE_PROPERTY:
                     // Die im Modell ausgewählte Tabelle hat sich geändert
                     log.info("-> propertyChange: Aktualisiere Daten Tabelle wegen '{}'.", propertyName);
                     updateDatenTabelle(); // Zeichnet die Haupttabelle mit den neuen Daten neu
                     // Stelle sicher, dass die Tabellen-ComboBox synchronisiert ist
                     ExtrahierteTabelle modelSelection = model.getAusgewaehlteTabelle();
                     if (!Objects.equals(tabelleComboBox.getSelectedItem(), modelSelection)) {
                          log.debug("--> Synchronisiere Tabellen ComboBox mit Modell-Auswahl: {}", modelSelection);
                          ActionListener[] listeners = tabelleComboBox.getActionListeners(); for(ActionListener l:listeners)tabelleComboBox.removeActionListener(l);
                          tabelleComboBox.setSelectedItem(modelSelection); // Setze Auswahl in der GUI
                          for(ActionListener l:listeners)tabelleComboBox.addActionListener(l);
                     }
                     break;
                 default:
                     // Ignoriere andere Events
                     log.debug("-> propertyChange: Ignoriere Event '{}'", propertyName);
                     break;
            }
        });
    }
}