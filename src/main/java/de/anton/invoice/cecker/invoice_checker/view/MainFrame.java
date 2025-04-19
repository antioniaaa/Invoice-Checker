package de.anton.invoice.cecker.invoice_checker.view;

import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtractionConfiguration;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeConfig;
import de.anton.invoice.cecker.invoice_checker.model.PdfDokument;

//Benötigte Swing und AWT Klassen
import javax.swing.*;
import javax.swing.event.ChangeListener; // Für JSpinner Listener
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener; // Für Konfig-ComboBox Listener
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

//Für PropertyChangeListener (MVC)
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

//Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


//Hilfsklassen und Java Util
import java.util.Comparator; // Für Sortierung
import java.util.List;
import java.util.Objects; // Für Vergleich
import java.util.Optional;
import java.util.Vector;


import javax.swing.border.EmptyBorder;


/**
* Das Hauptfenster (View) der Anwendung.
* Zeigt die Bedienelemente (Laden, Export, PDF-/Tabellenauswahl, Parameter, Konfiguration)
* und die extrahierte Tabelle an. Lauscht auf Änderungen im Modell. Enthält Fortschrittsanzeige
* und Panel für erkannte Rechnungstypen.
*/
public class MainFrame extends JFrame implements PropertyChangeListener {
 private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

 private final AnwendungsModell model;
 // GUI Elemente - Steuerung Oben
 private JButton ladePdfButton;
 private JButton exportExcelButton;
 private JButton btnRefresh;
 private JButton btnEditCsv;
 private JComboBox<PdfDokument> pdfComboBox;
 private JComboBox<ExtrahierteTabelle> tabelleComboBox;
 private JComboBox<Object> configComboBox; // Kann String("Keine") oder ExtractionConfiguration enthalten
 // GUI Elemente - Parameter Oben Rechts
 private JComboBox<String> flavorComboBox;
 private JSpinner rowToleranceSpinner;
 private JLabel rowToleranceLabel;
 // GUI Elemente - Tabelle Mitte
 private JTable datenTabelle;
 private DefaultTableModel tabellenModell;
 // GUI Elemente - Rechnungstyp Panel Unten
 private JPanel tableDefinitionPanel;
 private JTextField txtDetectedKeyword;
 private JTextField txtDetectedType;
 private JTextField txtDetectedAreaType;
 private JTextField txtDetectedFlavor;
 private JTextField txtDetectedRowTol;
 // GUI Elemente - Statusleiste Unten
 private JLabel statusLabel;
 private JProgressBar progressBar;
 // GUI Elemente - Menü
 private JMenuBar menuBar;
 private JMenuItem openConfigEditorMenuItem; // Referenz für Listener-Registrierung
 private JMenu configMenu; // Feld für das Menü selbst


 /**
  * Konstruktor für das Hauptfenster.
  * @param model Das Anwendungsmodell.
  */
 public MainFrame(AnwendungsModell model) {
     this.model = model;
     this.model.addPropertyChangeListener(this); // Auf Modelländerungen lauschen

     setTitle("PDF Tabellen Extraktor");
     setSize(1250, 850); // Breite/Höhe angepasst
     setMinimumSize(new Dimension(1000, 600)); // Mindestgröße
     setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Schließen im Listener behandeln
     setLocationRelativeTo(null); // Fenster zentrieren

     initKomponenten(); // GUI-Elemente erstellen
     layoutKomponenten(); // GUI-Elemente anordnen

     // Listener zum sauberen Behandeln des Fenster-Schließens
     addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
             log.info("Fenster schließt.");
             model.shutdownExecutor(); // Hintergrund-Threads stoppen
             dispose(); // Fenster schließen
             System.exit(0); // Anwendung beenden
         }
     });
 }

 /**
  * Initialisiert alle Swing-Komponenten des Fensters.
  */
 private void initKomponenten() {
     // Buttons
     ladePdfButton = new JButton("PDF(s) laden");
     exportExcelButton = new JButton("Nach Excel exportieren");
     exportExcelButton.setEnabled(false); // Initial deaktiviert
     btnRefresh = new JButton("Neu verarbeiten");
     btnRefresh.setToolTipText("Verarbeitet das aktuell ausgewählte PDF erneut mit den eingestellten Parametern/Konfigs.");
     btnRefresh.setEnabled(false); // Aktivieren, wenn PDF ausgewählt ist
     btnEditCsv = new JButton("Typdefinitionen (CSV)...");
     btnEditCsv.setToolTipText("Öffnet die invoice-config.csv im Standardeditor.");

     // ComboBoxen
     pdfComboBox = new JComboBox<>();
     tabelleComboBox = new JComboBox<>();
     tabelleComboBox.setEnabled(false); // Initial deaktiviert
     configComboBox = new JComboBox<>(); // Wird später befüllt
     configComboBox.setPreferredSize(new Dimension(160, configComboBox.getPreferredSize().height)); // Breite begrenzen
     configComboBox.setToolTipText("Aktive Bereichs-Konfiguration auswählen ('Keine' = ohne Bereiche)");

     // Parameter Komponenten initialisieren
     flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"});
     flavorComboBox.setSelectedItem("lattice"); // Default
     flavorComboBox.setToolTipText("Manuelle Auswahl der Camelot Extraktionsmethode.");
     SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1); // Default 2, Min 0, Max 100, Step 1
     rowToleranceSpinner = new JSpinner(spinnerModel);
     rowToleranceSpinner.setToolTipText("Manuelle Zeilentoleranz für 'stream'-Methode (höher=toleranter).");
     rowToleranceLabel = new JLabel("Row Tol (Stream):");

     // Tabelle initialisieren
     tabellenModell = new DefaultTableModel();
     datenTabelle = new JTable(tabellenModell);
     datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Horizontales Scrollen erlauben

     // Menüleiste initialisieren
     menuBar = new JMenuBar();
     configMenu = new JMenu("Konfiguration"); // Menü erstellen
     menuBar.add(configMenu); // Menü zur Leiste hinzufügen
     openConfigEditorMenuItem = new JMenuItem("Bereichsdefinition Editor..."); // Menüpunkt erstellen
     openConfigEditorMenuItem.setToolTipText("Öffnet einen Dialog zum Definieren von Tabellenbereichen für verschiedene Konfigurationen.");
     configMenu.add(openConfigEditorMenuItem); // Menüpunkt zum Menü hinzufügen

     // Statusleiste & Fortschritt
     statusLabel = new JLabel("Bereit. Laden Sie PDFs, um zu starten.");
     statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5)); // Rand links/rechts
     progressBar = new JProgressBar(0, 100);
     progressBar.setStringPainted(true); // Zeigt Prozentzahl an
     progressBar.setVisible(false); // Startet unsichtbar
     progressBar.setPreferredSize(new Dimension(150, progressBar.getPreferredSize().height)); // Breite begrenzen

     // Komponenten für das "Table Definition Panel"
     tableDefinitionPanel = new JPanel(new GridBagLayout());
     tableDefinitionPanel.setBorder(BorderFactory.createTitledBorder("Erkannter Rechnungstyp & Parameter (aus CSV)"));
     // Textfelder als nicht editierbar initialisieren
     txtDetectedKeyword = createReadOnlyTextField(15);
     txtDetectedType = createReadOnlyTextField(15);
     txtDetectedAreaType = createReadOnlyTextField(10);
     txtDetectedFlavor = createReadOnlyTextField(8);
     txtDetectedRowTol = createReadOnlyTextField(4);
 }

 /** Hilfsmethode für read-only Textfelder mit Tooltip. */
 private JTextField createReadOnlyTextField(int columns) {
     JTextField tf = new JTextField(columns);
     tf.setEditable(false);
     // Farbe leicht anders als normale Textfelder, um Inaktivität zu signalisieren
     tf.setBackground(UIManager.getColor("TextField.inactiveBackground")); // Systemfarbe verwenden
     tf.setToolTipText("Dieser Wert wird automatisch aus der invoice-config.csv ermittelt.");
     return tf;
 }

 /** Ordnet die initialisierten Komponenten im Fenster an (Layout). */
 private void layoutKomponenten() {
     // --- Top Panel ---
     JPanel topPanel = new JPanel();
     topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
     topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
     // Füge Komponenten von links nach rechts hinzu
     topPanel.add(ladePdfButton); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(exportExcelButton); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(btnRefresh); topPanel.add(Box.createHorizontalStrut(20));
     topPanel.add(new JLabel("PDF:")); topPanel.add(pdfComboBox); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(new JLabel("Tabelle:")); topPanel.add(tabelleComboBox); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(new JLabel("Bereichs-Konfig:")); topPanel.add(configComboBox);
     // Dehnbarer Platzhalter, schiebt Parameter nach rechts
     topPanel.add(Box.createHorizontalGlue());
     // Parameter Panel rechtsbündig
     JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
     parameterPanel.setBorder(BorderFactory.createTitledBorder("Manuelle Parameter"));
     parameterPanel.add(new JLabel("Flavor:")); parameterPanel.add(flavorComboBox); parameterPanel.add(Box.createHorizontalStrut(10));
     parameterPanel.add(rowToleranceLabel); rowToleranceSpinner.setPreferredSize(new Dimension(60, rowToleranceSpinner.getPreferredSize().height)); parameterPanel.add(rowToleranceSpinner);
     topPanel.add(parameterPanel);

     // --- Center Panel (Tabelle + Invoice Type Panel) ---
     JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
     JScrollPane tableScrollPane = new JScrollPane(datenTabelle);
     centerPanel.add(tableScrollPane, BorderLayout.CENTER); // Tabelle oben/mittig

     // Layout für das Invoice Type Panel (GridBagLayout)
     GridBagConstraints gbcT = new GridBagConstraints();
     gbcT.insets = new Insets(3, 5, 3, 5); gbcT.anchor = GridBagConstraints.WEST;
     // Zeile 0
     gbcT.gridx = 0; gbcT.gridy = 0; tableDefinitionPanel.add(new JLabel("Erk. Keyword:"), gbcT);
     gbcT.gridx = 1; tableDefinitionPanel.add(txtDetectedKeyword, gbcT);
     gbcT.gridx = 2; tableDefinitionPanel.add(new JLabel("Typ:"), gbcT);
     gbcT.gridx = 3; tableDefinitionPanel.add(txtDetectedType, gbcT);
     // Zeile 1
     gbcT.gridx = 0; gbcT.gridy = 1; tableDefinitionPanel.add(new JLabel("Bereichs-Typ:"), gbcT);
     gbcT.gridx = 1; tableDefinitionPanel.add(txtDetectedAreaType, gbcT);
     gbcT.gridx = 2; tableDefinitionPanel.add(new JLabel("Def. Flavor:"), gbcT);
     gbcT.gridx = 3; tableDefinitionPanel.add(txtDetectedFlavor, gbcT);
     gbcT.gridx = 4; tableDefinitionPanel.add(new JLabel("Def. RowTol:"), gbcT);
     gbcT.gridx = 5; gbcT.weightx = 0.0; tableDefinitionPanel.add(txtDetectedRowTol, gbcT); // Kein horizontales Wachsen
     // Button rechts über 2 Zeilen
     gbcT.gridx = 6; gbcT.gridy = 0; gbcT.gridheight = 2; gbcT.weightx = 1.0; // Nimmt restlichen Platz
     gbcT.anchor = GridBagConstraints.EAST; gbcT.fill = GridBagConstraints.NONE; // Nicht füllen
     tableDefinitionPanel.add(btnEditCsv, gbcT);
     centerPanel.add(tableDefinitionPanel, BorderLayout.SOUTH); // Füge Panel unten hinzu

     // --- Bottom Panel (Statusleiste + Fortschritt) ---
     JPanel bottomPanel = new JPanel(new BorderLayout(5,0)); // Etwas Abstand
     bottomPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
     bottomPanel.add(statusLabel, BorderLayout.CENTER);
     bottomPanel.add(progressBar, BorderLayout.EAST); // Fortschritt rechts

     // --- Gesamtlayout ---
     setLayout(new BorderLayout());
     setJMenuBar(menuBar); // Menüleiste setzen
     add(topPanel, BorderLayout.NORTH); // Obere Leiste
     add(centerPanel, BorderLayout.CENTER); // Tabelle und Info in der Mitte
     add(bottomPanel, BorderLayout.SOUTH); // Statusleiste unten
 }

 // --- Methoden für den Controller (Listener registrieren) ---
 public void addLadeButtonListener(ActionListener listener) { ladePdfButton.addActionListener(listener); }
 public void addExportButtonListener(ActionListener listener) { exportExcelButton.addActionListener(listener); }
 public void addRefreshButtonListener(ActionListener listener) { btnRefresh.addActionListener(listener); }
 public void addEditCsvButtonListener(ActionListener listener) { btnEditCsv.addActionListener(listener); }
 public void addPdfComboBoxListener(ActionListener listener) { pdfComboBox.addActionListener(listener); }
 public void addTabelleComboBoxListener(ActionListener listener) { tabelleComboBox.addActionListener(listener); }
 public void addFlavorComboBoxListener(ActionListener listener) { flavorComboBox.addActionListener(listener); }
 public void addRowToleranceSpinnerListener(ChangeListener listener) { rowToleranceSpinner.addChangeListener(listener); }
 public void addConfigMenuOpenListener(ActionListener listener) {
     if (openConfigEditorMenuItem != null) {
         openConfigEditorMenuItem.addActionListener(listener); // Korrekt den Listener am Menüpunkt registrieren
     } else { log.error("Menüpunkt zum Öffnen des Konfig-Editors ist null!"); }
 }
 public void addConfigSelectionListener(ItemListener listener) { configComboBox.addItemListener(listener); }


 // --- Getter für Komponenten (damit Controller darauf zugreifen kann) ---
 public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
 public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
 public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
 public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }
 public JComboBox<Object> getConfigComboBox() { return configComboBox; } // Gibt Konfig-ComboBox zurück


 // --- Methoden zur Aktualisierung der UI-Komponenten ---

 /**
  * Aktualisiert die Konfigurations-ComboBox mit den übergebenen Konfigurationen
  * und wählt die aktuell im Modell aktive Konfiguration aus.
  * Wird vom AppController aufgerufen.
  *
  * @param availableConfigs Eine Liste der verfügbaren ExtractionConfiguration-Objekte.
  * @param activeConfig Die aktuell im Modell aktive Konfiguration (kann null sein).
  */
 public void updateConfigurationComboBox(List<ExtractionConfiguration> availableConfigs, ExtractionConfiguration activeConfig) {
     log.debug("MainFrame.updateConfigurationComboBox wird aufgerufen mit {} Konfigs. Aktiv: {}",
               (availableConfigs != null ? availableConfigs.size() : 0),
               (activeConfig != null ? activeConfig.getName() : "Keine"));

     // Listener temporär entfernen, um Events während des Befüllens zu vermeiden
     ItemListener[] listeners = configComboBox.getItemListeners();
     for(ItemListener l : listeners) configComboBox.removeItemListener(l);

     // ComboBox leeren und neu befüllen
     configComboBox.removeAllItems();
     configComboBox.addItem("Keine"); // Standardoption hinzufügen ("Keine Konfiguration")

     // Füge geladene Konfigurationen hinzu (wenn Liste nicht null ist)
     if (availableConfigs != null) {
         // Sortiere nach Namen (optional, aber benutzerfreundlich)
         availableConfigs.sort(Comparator.comparing(ExtractionConfiguration::getName, String.CASE_INSENSITIVE_ORDER));
         for (ExtractionConfiguration config : availableConfigs) {
             configComboBox.addItem(config); // Füge Konfigurationsobjekte hinzu
         }
     }

     // Auswahl basierend auf dem übergebenen aktiven Konfigurationsobjekt setzen
     if (activeConfig != null && availableConfigs != null && availableConfigs.contains(activeConfig)) {
          configComboBox.setSelectedItem(activeConfig); // Setze das Objekt als ausgewählt
          log.debug("--> Aktive Konfiguration '{}' in ComboBox ausgewählt.", activeConfig.getName());
     } else {
          configComboBox.setSelectedItem("Keine"); // Wähle "Keine" aus
          log.debug("--> Keine aktive Konfiguration oder aktive nicht in Liste, 'Keine' ausgewählt.");
     }

     // Listener wieder hinzufügen
     for(ItemListener l : listeners) configComboBox.addItemListener(l);
     log.debug("Konfigurations-ComboBox Update abgeschlossen.");
 }

 /**
  * Aktualisiert die PDF-ComboBox mit der Liste der geladenen Dokumente aus dem Modell.
  * Wählt automatisch das erste Element aus, wenn die Liste nicht leer ist und
  * informiert das Modell darüber (falls nötig), um die abhängigen Updates anzustoßen.
  */
 private void updatePdfComboBox() {
     log.info("MainFrame.updatePdfComboBox START");
     Object selectedPdf = pdfComboBox.getSelectedItem(); // Aktuelle Auswahl merken

     // Listener temporär entfernen
     log.trace("--> Entferne PDF ComboBox Listener...");
     ActionListener[] pdfListeners = pdfComboBox.getActionListeners();
     for (ActionListener l : pdfListeners) pdfComboBox.removeActionListener(l);

     // ComboBox leeren und neu befüllen
     pdfComboBox.removeAllItems();
     List<PdfDokument> dokumente = model.getDokumente(); // Aktuelle Liste holen
     log.debug("--> Befülle PDF ComboBox mit {} Dokumenten.", (dokumente != null ? dokumente.size() : 0));
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
         log.trace("--> Versuche, vorherige PDF Auswahl wiederherzustellen: {}", selectedPdf);
         pdfComboBox.setSelectedItem(selectedPdf);
         log.debug("--> PDF Auswahl wiederhergestellt: {}", selectedPdf);
         selectionSet = true;
     } else if (hatPdfElemente) {
         PdfDokument erstesElement = dokumente.get(0);
         log.trace("--> Versuche, erstes PDF Element auszuwählen: {}", erstesElement.getSourcePdf());
         pdfComboBox.setSelectedItem(erstesElement);
         selectionSet = true;
         log.info("--> Erstes PDF '{}' als ausgewählt in ComboBox gesetzt.", erstesElement.getSourcePdf());
         if (!Objects.equals(model.getAusgewaehltesDokument(), erstesElement)) {
              log.info("---> Rufe model.setAusgewaehltesDokument auf, da Modellauswahl abweicht (Modell hat: {}).", model.getAusgewaehltesDokument());
              model.setAusgewaehltesDokument(erstesElement); // Löst Events aus
         } else {
             log.debug("---> Modell hatte bereits das korrekte erste Element ausgewählt. Kein setAusgewaehltesDokument nötig.");
             log.debug("---> Triggere Updates für Tabellen-ComboBox und Daten-Tabelle manuell (Sicherheitsmaßnahme).");
             updateTabelleComboBox(); // Stellt sicher, dass die Tabellenliste stimmt
             updateDatenTabelle();   // Stellt sicher, dass die Tabelle angezeigt wird
             // Invoice Panel wird durch den Controller aktualisiert, nachdem das Dokument im Modell gesetzt ist
         }
     }

     // Wenn keine Auswahl gesetzt wurde (weil Liste leer), stelle sicher, dass Modellauswahl auch null ist
     if (!selectionSet) {
          log.debug("--> ComboBox leer oder keine Auswahl gesetzt.");
          if (model.getAusgewaehltesDokument() != null) {
               log.info("--> Keine PDFs mehr in ComboBox, lösche Auswahl im Modell.");
               model.setAusgewaehltesDokument(null); // Löst Event aus
          }
          // Wenn die Box leer ist, explizit auch die abhängigen Elemente leeren
          updateTabelleComboBox();
          updateDatenTabelle();
          updateInvoiceTypeDisplay(null); // Auch Invoice Panel leeren
     }

     // Listener wieder hinzufügen
     log.trace("--> Füge PDF ComboBox Listener wieder hinzu...");
     for (ActionListener l : pdfListeners) pdfComboBox.addActionListener(l);
     log.info("MainFrame.updatePdfComboBox ENDE");
 }

 /**
  * Aktualisiert die Tabellen-ComboBox basierend auf dem aktuell im Modell ausgewählten PDF.
  */
 public void updateTabelleComboBox() {
     log.info("MainFrame.updateTabelleComboBox aufgerufen.");
     Object selectedTable = tabelleComboBox.getSelectedItem();
     ActionListener[] tableListeners = tabelleComboBox.getActionListeners(); for(ActionListener l : tableListeners) tabelleComboBox.removeActionListener(l);
     tabelleComboBox.removeAllItems(); tabelleComboBox.setEnabled(false);
     PdfDokument currentPdf = model.getAusgewaehltesDokument();
     if (currentPdf != null) {
         List<ExtrahierteTabelle> tabellen = model.getVerfuegbareTabellen();
         log.debug("--> Fülle Tabellen Combo mit {} Tabellen für PDF '{}'.", tabellen.size(), currentPdf.getSourcePdf());
         if (!tabellen.isEmpty()) { for (ExtrahierteTabelle t : tabellen) tabelleComboBox.addItem(t); tabelleComboBox.setEnabled(true); ExtrahierteTabelle modelSel = model.getAusgewaehlteTabelle(); boolean selSet = false; if (selectedTable instanceof ExtrahierteTabelle && tabellen.contains(selectedTable)) { tabelleComboBox.setSelectedItem(selectedTable); selSet=true; if(!Objects.equals(modelSel, selectedTable)) model.setAusgewaehlteTabelle((ExtrahierteTabelle)selectedTable); } else if (modelSel != null && tabellen.contains(modelSel)) { tabelleComboBox.setSelectedItem(modelSel); selSet=true; } else { ExtrahierteTabelle first = tabellen.get(0); tabelleComboBox.setSelectedItem(first); log.warn("--> Setze erstes Element '{}' in Tabellen-Combo als Fallback.", first); if(!Objects.equals(modelSel, first)) model.setAusgewaehlteTabelle(first); selSet=true; } if (!selSet && modelSel != null) model.setAusgewaehlteTabelle(null); }
         else { log.debug("--> Keine Tabellen für PDF gefunden."); if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null); }
     } else { log.debug("--> Kein PDF ausgewählt."); if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null); }
     for(ActionListener l : tableListeners) tabelleComboBox.addActionListener(l); log.debug("Tabellen ComboBox Update abgeschlossen.");
 }

 /**
  * Aktualisiert die JTable (datenTabelle) mit den Daten der aktuell im Modell ausgewählten Tabelle.
  * Beinhaltet auch die automatische Anpassung und Verdopplung der Spaltenbreiten.
  */
 public void updateDatenTabelle() {
     log.info("MainFrame.updateDatenTabelle aufgerufen für: {}", model.getAusgewaehlteTabelle());
     Optional<List<List<String>>> dataOpt = model.getAusgewaehlteTabellenDaten();
     if (dataOpt.isPresent()) {
         List<List<String>> data = dataOpt.get();
         log.debug("--> Daten erhalten ({} Zeilen)", data.size());
         if (!data.isEmpty()) {
             Vector<String> headers = new Vector<>(data.get(0)); Vector<Vector<Object>> dv = new Vector<>();
             for (int i = 1; i < data.size(); i++) dv.add(new Vector<>(data.get(i)));
             log.info("---> Setze Daten: {} Zeilen, {} Spalten (Header: {})", dv.size(), headers.size(), headers);
             tabellenModell.setDataVector(dv, headers); // Setze Daten und Header
             setStatus("Zeige: " + model.getAusgewaehlteTabelle());
             // Spaltenbreiten anpassen (verzögert im EDT)
             SwingUtilities.invokeLater(() -> { if (datenTabelle.getColumnCount() > 0) { TabellenSpaltenAnpasser tca=new TabellenSpaltenAnpasser(datenTabelle); tca.adjustColumns(); TableColumnModel cm=datenTabelle.getColumnModel(); for(int i=0;i<cm.getColumnCount();i++){ TableColumn c=cm.getColumn(i); int w=c.getPreferredWidth(); c.setPreferredWidth(w*2); } log.debug("---> Spaltenbreiten verdoppelt."); }});
         } else { log.warn("--> Tabellendaten leer."); tabellenModell.setDataVector(new Vector<>(), new Vector<>()); setStatus("Tabelle ist leer: " + model.getAusgewaehlteTabelle()); } // Leere Tabelle setzen
     } else { log.warn("--> Keine Tabellendaten vom Modell."); tabellenModell.setDataVector(new Vector<>(), new Vector<>()); /* Status setzen */ if(model.getAusgewaehltesDokument()!=null&&model.getAusgewaehlteTabelle()!=null)setStatus("Keine Daten verfügbar für: "+model.getAusgewaehlteTabelle()); else if(model.getAusgewaehltesDokument()!=null)setStatus("Keine Tabelle ausgewählt."); else setStatus("Kein PDF ausgewählt.");} // Leere Tabelle setzen
 }

 /** Setzt den Text in der Statusleiste (stellt sicher, dass dies im EDT geschieht). */
 public void setStatus(String nachricht) { SwingUtilities.invokeLater(() -> statusLabel.setText(nachricht)); }

 /** Setzt die Sichtbarkeit des Fortschrittsbalkens (im EDT). */
 public void setProgressBarVisible(boolean visible) { SwingUtilities.invokeLater(() -> progressBar.setVisible(visible)); }

 /** Setzt den Wert (0-100) des Fortschrittsbalkens (im EDT). */
 public void setProgressBarValue(int value) { SwingUtilities.invokeLater(() -> { progressBar.setValue(value); progressBar.setString(value + "%"); }); }

 /** Aktualisiert die Felder im "Table Definition Panel" (im EDT). */
 public void updateInvoiceTypeDisplay(InvoiceTypeConfig config) {
     SwingUtilities.invokeLater(() -> {
         if (config != null) {
             txtDetectedKeyword.setText(config.getKeyword());
             txtDetectedType.setText(config.getType());
             txtDetectedAreaType.setText(config.getAreaType());
             txtDetectedFlavor.setText(config.getDefaultFlavor());
             txtDetectedRowTol.setText(config.getDefaultRowTol());
             // Refresh Button Status wird jetzt durch SELECTED_DOCUMENT Event gesetzt
         } else {
             txtDetectedKeyword.setText("-"); txtDetectedType.setText("-"); txtDetectedAreaType.setText("-"); txtDetectedFlavor.setText("-"); txtDetectedRowTol.setText("-");
             btnRefresh.setEnabled(false); // Sicherstellen, dass Refresh aus ist
         }
     });
 }

 // --- PropertyChangeListener Implementierung ---
 /** Reagiert auf Änderungen im Modell. */
 @Override
 public void propertyChange(PropertyChangeEvent evt) {
     String propertyName = evt.getPropertyName();
     log.debug("MainFrame.propertyChange empfangen: Eigenschaft='{}'", propertyName);
     SwingUtilities.invokeLater(() -> {
          switch (propertyName) {
              case AnwendungsModell.DOCUMENTS_UPDATED_PROPERTY:
                  log.info("-> propertyChange: DOCUMENTS_UPDATED");
                  updatePdfComboBox(); // Aktualisiert PDF Liste -> löst ggf. weitere Updates aus
                  if (model.getDokumente().isEmpty()) updateInvoiceTypeDisplay(null);
                  break;
              case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                  log.info("-> propertyChange: SELECTED_DOCUMENT auf '{}'", evt.getNewValue());
                  updateTabelleComboBox(); // Aktualisiert Tabellenliste
                  btnRefresh.setEnabled(model.getAusgewaehltesDokument() != null); // Aktualisiert Refresh Button
                  // Invoice Panel wird durch Controller aktualisiert (via handlePdfComboBoxAuswahl)
                  break;
              case AnwendungsModell.SELECTED_TABLE_PROPERTY:
                  log.info("-> propertyChange: SELECTED_TABLE auf '{}'", evt.getNewValue());
                  updateDatenTabelle(); // Aktualisiert JTable Anzeige
                  synchronizeTableComboBoxSelection(); // Synchronisiert Tabellen ComboBox
                  break;
              case AnwendungsModell.ACTIVE_CONFIG_PROPERTY:
                  log.info("-> propertyChange: ACTIVE_CONFIG auf '{}'", evt.getNewValue());
                  // Aktualisiere die Konfig ComboBox in der View
                  updateConfigurationComboBox(model.getConfigurationService().loadAllConfigurations(), model.getAktiveKonfiguration());
                  break;
              // Event SINGLE_DOCUMENT_REPROCESSED wird nicht mehr explizit behandelt,
              // da der Controller die Updates direkt nach dem Callback anstößt.
              default:
                  log.debug("-> propertyChange: Ignoriere Event '{}'", propertyName);
                  break;
         }
     });
 }

  /** Stellt sicher, dass die Auswahl in der Tabellen-ComboBox mit dem Modell übereinstimmt. */
 private void synchronizeTableComboBoxSelection() {
      ExtrahierteTabelle modelSelection = model.getAusgewaehlteTabelle();
      if (!Objects.equals(tabelleComboBox.getSelectedItem(), modelSelection)) {
           log.debug("--> Synchronisiere Tabellen ComboBox mit Modell: {}", modelSelection);
           ActionListener[] listeners = tabelleComboBox.getActionListeners(); for (ActionListener l : listeners) tabelleComboBox.removeActionListener(l);
           tabelleComboBox.setSelectedItem(modelSelection);
           for (ActionListener l : listeners) tabelleComboBox.addActionListener(l);
      }
 }
}