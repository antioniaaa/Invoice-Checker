package de.anton.invoice.cecker.invoice_checker.view;

import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtractionConfiguration;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeConfig;
import de.anton.invoice.cecker.invoice_checker.model.InvoiceTypeService;
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


//Util Imports
import java.time.LocalDateTime; // Für Zeitstempel im Log-Bereich
import java.time.format.DateTimeFormatter; // Für Zeitstempel im Log-Bereich


/**
* Das Hauptfenster (View) der Anwendung mit Tab-basierter Oberfläche.
* Tab 1: Laden von Abrechnungs-PDFs und Anzeige von Log-Meldungen.
* Tab 2: Laden von PDFs zur Detailansicht, Konfiguration, Parameteranpassung und Tabellenanzeige.
* Lauscht auf Änderungen im Modell. Enthält Fortschrittsanzeige
* und Panel für erkannte Rechnungstypen.
*/
public class MainFrame extends JFrame implements PropertyChangeListener {
 private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

 private final AnwendungsModell model; // Referenz auf das Modell

 // --- GUI Elemente ---
 // Tabbed Pane
 private JTabbedPane tabbedPane;

 // Tab 1: Abrechnungen
 private JPanel abrechnungenPanel;
 private JButton ladePdfButtonAbrechnung; // Button für Tab 1
 private JTextArea logTextArea;           // Log-Bereich für Tab 1

 // Tab 2: Konfiguration & Details
 private JPanel configDetailPanel;
 private JButton ladePdfButtonDetails;  // Button für Tab 2
 private JButton exportExcelButton;
 private JButton btnRefresh;
 private JButton btnEditCsv;
 private JButton btnUpdateCsv;
 private JComboBox<PdfDokument> pdfComboBox;
 private JComboBox<ExtrahierteTabelle> tabelleComboBox;
 private JComboBox<Object> configComboBox; // Bereichs-Konfigs
 private JComboBox<String> flavorComboBox;
 private JSpinner rowToleranceSpinner;
 private JLabel rowToleranceLabel;
 private JTable datenTabelle;
 private DefaultTableModel tabellenModell;
 private JPanel tableDefinitionPanel;
 private JTextField txtDetectedKeyword;
 private JTextField txtDetectedType;
 private JTextField txtDetectedAreaType;
 private JTextField txtDetectedFlavor;
 private JTextField txtDetectedRowTol;
 private JProgressBar progressBar; // Gemeinsamer Fortschrittsbalken unten

 // Menü
 private JMenuBar menuBar;
 private JMenuItem openConfigEditorMenuItem;
 private JMenu configMenu;

 // Für Log Zeitstempel
 private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");


 /**
  * Konstruktor für das Hauptfenster.
  * @param model Das Anwendungsmodell. Darf nicht null sein.
  */
 public MainFrame(AnwendungsModell model) { // Nimmt KEINEN Controller mehr entgegen
     if (model == null) {
          throw new IllegalArgumentException("Modell darf im MainFrame nicht null sein!");
     }
     this.model = model;
     this.model.addPropertyChangeListener(this); // Auf Modelländerungen lauschen

     setTitle("PDF Tabellen Extraktor");
     setSize(1250, 850); // Breite/Höhe angepasst
     setMinimumSize(new Dimension(1000, 600)); // Mindestgröße
     setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Schließen im Listener behandeln
     setLocationRelativeTo(null); // Fenster zentrieren

     initKomponenten(); // GUI-Elemente erstellen
     layoutKomponenten(); // GUI-Elemente anordnen

     // Listener werden vom Controller in dessen initializeListeners Methode registriert

     // Listener zum sauberen Behandeln des Fenster-Schließens
     addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
             log.info("Fenster schließt.");
             if (model != null) {
                 model.shutdownExecutor(); // Hintergrund-Threads stoppen
             }
             dispose(); // Fenster schließen
             System.exit(0); // Anwendung beenden
         }
     });
 }

 /**
  * Initialisiert alle Swing-Komponenten des Fensters.
  */
 private void initKomponenten() {
     // --- Komponenten für Tab 1: Abrechnungen ---
     ladePdfButtonAbrechnung = new JButton("Abrechnungs-PDFs auswählen...");
     ladePdfButtonAbrechnung.setToolTipText("Wählt PDFs für eine zukünftige Abrechnungsfunktion aus.");
     logTextArea = new JTextArea(15, 80); // Etwas größer für mehr Logs
     logTextArea.setEditable(false);
     logTextArea.setLineWrap(true); // Automatischer Zeilenumbruch
     logTextArea.setWrapStyleWord(true); // Umbruch nach Worten
     logTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); // Monospaced Font für Logs

     // --- Komponenten für Tab 2: Konfiguration & Details ---
     ladePdfButtonDetails = new JButton("PDF(s) laden (Details)");
     ladePdfButtonDetails.setToolTipText("Lädt PDFs zur Detailansicht, Konfiguration und Parameteranpassung.");
     exportExcelButton = new JButton("Tabelle exportieren"); exportExcelButton.setEnabled(false); exportExcelButton.setToolTipText("Exportiert die aktuell angezeigte Tabelle nach Excel.");
     btnRefresh = new JButton("Neu verarbeiten"); btnRefresh.setToolTipText("Verarbeitet das aktuell ausgewählte PDF erneut mit den eingestellten Parametern/Konfigs."); btnRefresh.setEnabled(false); // Aktivieren, wenn PDF ausgewählt ist
     btnEditCsv = new JButton("Typdefinitionen (CSV)..."); btnEditCsv.setToolTipText("Öffnet die invoice-config.csv im Standardeditor.");
     btnUpdateCsv = new JButton("Params für Typ speichern"); btnUpdateCsv.setToolTipText("Speichert manuelle Parameter für erkannten Typ in CSV."); btnUpdateCsv.setEnabled(false); // Initial deaktiviert
     pdfComboBox = new JComboBox<>(); pdfComboBox.setToolTipText("Verarbeitetes PDF auswählen");
     tabelleComboBox = new JComboBox<>(); tabelleComboBox.setEnabled(false); tabelleComboBox.setToolTipText("Extrahierte Tabelle auswählen");
     configComboBox = new JComboBox<>(); configComboBox.setPreferredSize(new Dimension(160, configComboBox.getPreferredSize().height)); configComboBox.setToolTipText("Aktive Bereichs-Konfiguration auswählen ('Keine' = ohne Bereiche)");
     flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"}); flavorComboBox.setSelectedItem("lattice"); flavorComboBox.setToolTipText("Manuelle Extraktionsmethode");
     SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1); // Default 2, Min 0, Max 100, Step 1
     rowToleranceSpinner = new JSpinner(spinnerModel); rowToleranceSpinner.setToolTipText("Manuelle Zeilentoleranz für 'stream' (höher=toleranter).");
     rowToleranceLabel = new JLabel("Row Tol (Stream):");
     tabellenModell = new DefaultTableModel(); datenTabelle = new JTable(tabellenModell); datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Wichtig für viele/breite Spalten
     tableDefinitionPanel = new JPanel(new GridBagLayout()); tableDefinitionPanel.setBorder(BorderFactory.createTitledBorder("Erkannter Rechnungstyp & Parameter"));
     txtDetectedKeyword = createReadOnlyTextField(15); txtDetectedType = createReadOnlyTextField(15); txtDetectedAreaType = createReadOnlyTextField(10); txtDetectedFlavor = createReadOnlyTextField(8); txtDetectedRowTol = createReadOnlyTextField(4);

     // --- Gemeinsame Komponenten ---
     menuBar = new JMenuBar(); configMenu = new JMenu("Konfiguration"); menuBar.add(configMenu); openConfigEditorMenuItem = new JMenuItem("Bereichsdefinition Editor..."); openConfigEditorMenuItem.setToolTipText("Definiert Bereiche für die Extraktion"); configMenu.add(openConfigEditorMenuItem);
     progressBar = new JProgressBar(0, 100); progressBar.setStringPainted(true); progressBar.setVisible(false); progressBar.setPreferredSize(new Dimension(150, progressBar.getPreferredSize().height));

     // --- Tabbed Pane ---
     tabbedPane = new JTabbedPane();
 }

 /** Hilfsmethode für read-only Textfelder mit Tooltip. */
 private JTextField createReadOnlyTextField(int columns) {
     JTextField tf = new JTextField(columns);
     tf.setEditable(false);
     tf.setBackground(UIManager.getColor("TextField.inactiveBackground")); // Systemfarbe verwenden
     tf.setToolTipText("Dieser Wert wird automatisch aus der invoice-config.csv ermittelt.");
     return tf;
 }

 /** Ordnet die Komponenten im Fenster mit Tabs an. */
 private void layoutKomponenten() {
     // === Tab 1: Abrechnungen ===
     abrechnungenPanel = new JPanel(new BorderLayout(10, 10));
     abrechnungenPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
     // Button oben zentriert
     JPanel loadButtonPanelAbrechnung = new JPanel(new FlowLayout(FlowLayout.CENTER));
     loadButtonPanelAbrechnung.add(ladePdfButtonAbrechnung); // Button für Tab 1
     abrechnungenPanel.add(loadButtonPanelAbrechnung, BorderLayout.NORTH);
     // Log-Textbereich in der Mitte (mit Scrollbalken)
     JScrollPane logScrollPane = new JScrollPane(logTextArea);
     logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
     abrechnungenPanel.add(logScrollPane, BorderLayout.CENTER);

     // === Tab 2: Konfiguration & Details ===
     configDetailPanel = new JPanel(new BorderLayout(5, 5));
     configDetailPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

     // Oberes Panel für Auswahl, Parameter und Lade-Button (für Details)
     JPanel topControlPanel = new JPanel();
     topControlPanel.setLayout(new BoxLayout(topControlPanel, BoxLayout.X_AXIS)); // Horizontal
     topControlPanel.setBorder(BorderFactory.createEmptyBorder(0,0,5,0)); // Abstand nach unten

     // Linke Seite der oberen Leiste
     JPanel selectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
     selectionPanel.add(ladePdfButtonDetails); // Button für Details hier
     selectionPanel.add(Box.createHorizontalStrut(15));
     selectionPanel.add(new JLabel("PDF:"));
     selectionPanel.add(pdfComboBox);
     selectionPanel.add(Box.createHorizontalStrut(10));
     selectionPanel.add(new JLabel("Tabelle:"));
     selectionPanel.add(tabelleComboBox);
     selectionPanel.add(Box.createHorizontalStrut(10));
     selectionPanel.add(new JLabel("Bereichs-Konfig:"));
     selectionPanel.add(configComboBox);
     topControlPanel.add(selectionPanel); // Linke Seite zum TopPanel hinzufügen

     topControlPanel.add(Box.createHorizontalGlue()); // Dehnbarer Platz dazwischen

     // Rechte Seite der oberen Leiste (Parameter)
     JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
     parameterPanel.setBorder(BorderFactory.createTitledBorder("Manuelle Parameter"));
     parameterPanel.add(new JLabel("Flavor:"));
     parameterPanel.add(flavorComboBox);
     parameterPanel.add(Box.createHorizontalStrut(10));
     parameterPanel.add(rowToleranceLabel);
     rowToleranceSpinner.setPreferredSize(new Dimension(60, rowToleranceSpinner.getPreferredSize().height)); // Feste Breite für Spinner
     parameterPanel.add(rowToleranceSpinner);
     topControlPanel.add(parameterPanel); // Rechte Seite zum TopPanel hinzufügen

     // Füge die obere Leiste zum Detail-Tab hinzu
     configDetailPanel.add(topControlPanel, BorderLayout.NORTH);

     // Mittleres Panel für die Tabelle (nimmt den meisten Platz ein)
     JScrollPane tableScrollPane = new JScrollPane(datenTabelle);
     configDetailPanel.add(tableScrollPane, BorderLayout.CENTER);

     // Unteres Panel für InvoiceType-Info und Aktionsbuttons
     JPanel bottomPanelCombined = new JPanel(new BorderLayout(5, 5));
     // InvoiceType Panel (Layout mit GridBagLayout)
     GridBagConstraints gbcT = new GridBagConstraints();
     gbcT.insets = new Insets(3, 5, 3, 5); // Innenabstände
     gbcT.anchor = GridBagConstraints.WEST; // Links ausrichten
     // Zeile 0
     gbcT.gridx = 0; gbcT.gridy = 0; tableDefinitionPanel.add(new JLabel("Erk. Keyword:"), gbcT);
     gbcT.gridx = 1; tableDefinitionPanel.add(txtDetectedKeyword, gbcT);
     gbcT.gridx = 2; tableDefinitionPanel.add(new JLabel("Typ:"), gbcT);
     gbcT.gridx = 3; tableDefinitionPanel.add(txtDetectedType, gbcT);
     // Zeile 1
     gbcT.gridx = 0; gbcT.gridy = 1; tableDefinitionPanel.add(new JLabel("Bereichs-Konfig:"), gbcT); // Angepasstes Label
     gbcT.gridx = 1; tableDefinitionPanel.add(txtDetectedAreaType, gbcT);
     gbcT.gridx = 2; tableDefinitionPanel.add(new JLabel("Def. Flavor:"), gbcT);
     gbcT.gridx = 3; tableDefinitionPanel.add(txtDetectedFlavor, gbcT);
     gbcT.gridx = 4; tableDefinitionPanel.add(new JLabel("Def. RowTol:"), gbcT);
     gbcT.gridx = 5; gbcT.weightx = 0.0; tableDefinitionPanel.add(txtDetectedRowTol, gbcT); // Kein horizontales Wachsen
     // Button Panel rechts, nimmt restlichen Platz
     JPanel invoiceButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)); // Buttons rechtsbündig
     invoiceButtonPanel.add(btnUpdateCsv); // Update Button
     invoiceButtonPanel.add(btnEditCsv); // Edit CSV Button
     gbcT.gridx = 6; gbcT.gridy = 0; gbcT.gridheight = 2; gbcT.weightx = 1.0; // Nimmt restlichen horiz. Platz
     gbcT.anchor = GridBagConstraints.EAST; gbcT.fill = GridBagConstraints.NONE; // Buttons nicht strecken
     tableDefinitionPanel.add(invoiceButtonPanel, gbcT);
     bottomPanelCombined.add(tableDefinitionPanel, BorderLayout.CENTER); // Füge Panel in die Mitte des unteren Bereichs ein

     // Export/Refresh Buttons rechts unten
     JPanel actionButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); // Rechtsbündig
     actionButtonPanel.add(btnRefresh);
     actionButtonPanel.add(exportExcelButton);
     bottomPanelCombined.add(actionButtonPanel, BorderLayout.EAST); // Füge Buttons rechts hinzu

     configDetailPanel.add(bottomPanelCombined, BorderLayout.SOUTH); // Füge kombiniertes unteres Panel hinzu

     // === Tabs hinzufügen ===
     tabbedPane.addTab("Abrechnungen verarbeiten", null, abrechnungenPanel, "PDF-Dateien für Abrechnungsfunktion auswählen");
     tabbedPane.addTab("Konfiguration & Detailansicht", null, configDetailPanel, "Parameter einstellen und extrahierte Tabellen im Detail anzeigen");

     // === Gesamtlayout ===
     setLayout(new BorderLayout());
     setJMenuBar(menuBar); // Menüleiste setzen
     add(tabbedPane, BorderLayout.CENTER); // TabbedPane als Hauptkomponente
     // Füge Fortschrittsbalken zur Statusleiste hinzu (oder direkt zum Frame unten)
     JPanel statusPanel = new JPanel(new BorderLayout());
     statusPanel.add(progressBar, BorderLayout.EAST); // Fortschritt rechts
     add(statusPanel, BorderLayout.SOUTH); // Statusleiste (nur mit Progress) unten
     // StatusLabel wird nicht mehr verwendet, stattdessen logTextArea
 }

 // --- Methoden für den Controller (Listener registrieren) ---
 public void addLadeButtonAbrechnungListener(ActionListener listener) { ladePdfButtonAbrechnung.addActionListener(listener); }
 public void addLadeButtonDetailsListener(ActionListener listener) { ladePdfButtonDetails.addActionListener(listener); }
 public void addExportButtonListener(ActionListener listener) { exportExcelButton.addActionListener(listener); }
 public void addRefreshButtonListener(ActionListener listener) { btnRefresh.addActionListener(listener); }
 public void addEditCsvButtonListener(ActionListener listener) { btnEditCsv.addActionListener(listener); }
 public void addUpdateCsvButtonListener(ActionListener listener) { btnUpdateCsv.addActionListener(listener); }
 public void addPdfComboBoxListener(ActionListener listener) { pdfComboBox.addActionListener(listener); }
 public void addTabelleComboBoxListener(ActionListener listener) { tabelleComboBox.addActionListener(listener); }
 public void addFlavorComboBoxListener(ActionListener listener) { flavorComboBox.addActionListener(listener); }
 public void addRowToleranceSpinnerListener(ChangeListener listener) { rowToleranceSpinner.addChangeListener(listener); }
 public void addConfigMenuOpenListener(ActionListener listener) {
     if(openConfigEditorMenuItem!=null) openConfigEditorMenuItem.addActionListener(listener);
     else log.error("Menüpunkt für Konfig-Editor ist null!");
 }
 public void addConfigSelectionListener(ItemListener listener) { configComboBox.addItemListener(listener); }


 // --- Getter für Komponenten ---
 public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
 public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
 public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
 public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }
 public JComboBox<Object> getConfigComboBox() { return configComboBox; }


 // --- Methoden zur Aktualisierung der UI-Komponenten ---

 /** Aktualisiert die Konfigurations-ComboBox. */
 public void updateConfigurationComboBox(List<ExtractionConfiguration> availableConfigs, ExtractionConfiguration activeConfig) {
     log.debug("MainFrame.updateConfigurationComboBox...");
     ItemListener[] listeners = configComboBox.getItemListeners(); for(ItemListener l : listeners) configComboBox.removeItemListener(l);
     configComboBox.removeAllItems(); configComboBox.addItem("Keine");
     if (availableConfigs != null) { availableConfigs.sort(Comparator.comparing(ExtractionConfiguration::getName, String.CASE_INSENSITIVE_ORDER)); for (ExtractionConfiguration config : availableConfigs) { configComboBox.addItem(config); } }
     if (activeConfig != null && availableConfigs != null && availableConfigs.contains(activeConfig)) { configComboBox.setSelectedItem(activeConfig); }
     else { configComboBox.setSelectedItem("Keine"); }
     for(ItemListener l : listeners) configComboBox.addItemListener(l);
     log.debug("Konfig-ComboBox Update fertig.");
 }

 /** Aktualisiert die PDF-ComboBox. */
 private void updatePdfComboBox() {
     log.info("MainFrame.updatePdfComboBox START");
     Object selectedPdf = pdfComboBox.getSelectedItem(); ActionListener[] ls = pdfComboBox.getActionListeners(); for(ActionListener l:ls)pdfComboBox.removeActionListener(l);
     pdfComboBox.removeAllItems(); List<PdfDokument> docs = model.getDokumente(); boolean hasEl = false;
     if (docs != null && !docs.isEmpty()) { for(PdfDokument d:docs) pdfComboBox.addItem(d); exportExcelButton.setEnabled(true); hasEl = true; } else { exportExcelButton.setEnabled(false); }
     boolean selSet = false;
     if (selectedPdf instanceof PdfDokument && docs != null && docs.contains(selectedPdf)) { pdfComboBox.setSelectedItem(selectedPdf); selSet = true; log.debug("--> PDF Auswahl wiederhergestellt."); }
     else if (hasEl) { PdfDokument first = docs.get(0); pdfComboBox.setSelectedItem(first); selSet = true; log.info("--> Erstes PDF '{}' gesetzt.", first.getSourcePdf()); if(!Objects.equals(model.getAusgewaehltesDokument(), first)) { log.info("---> Rufe setAusgewaehltesDokument auf..."); model.setAusgewaehltesDokument(first); } else { log.debug("---> Modell aktuell. Manuelle GUI Updates."); updateTabelleComboBox(); updateDatenTabelle(); /* Invoice Panel wird vom Controller getriggert */}}
     if (!selSet) { log.debug("--> Keine PDF Auswahl."); if(model.getAusgewaehltesDokument()!=null) model.setAusgewaehltesDokument(null); updateTabelleComboBox(); updateDatenTabelle(); updateInvoiceTypeDisplay(null); setRefreshButtonEnabled(false);}
     for(ActionListener l:ls)pdfComboBox.addActionListener(l);
     log.info("MainFrame.updatePdfComboBox ENDE");
 }

 /** Aktualisiert die Tabellen-ComboBox. */
 private void updateTabelleComboBox() {
     log.info("MainFrame.updateTabelleComboBox aufgerufen.");
     Object selectedTable = tabelleComboBox.getSelectedItem(); ActionListener[] ls = tabelleComboBox.getActionListeners(); for(ActionListener l:ls)tabelleComboBox.removeActionListener(l);
     tabelleComboBox.removeAllItems(); tabelleComboBox.setEnabled(false);
     PdfDokument currentPdf = model.getAusgewaehltesDokument();
     if (currentPdf != null) {
         List<ExtrahierteTabelle> tabellen = model.getVerfuegbareTabellen(); log.debug("--> Fülle Tabellen Combo mit {} Tabellen.", tabellen.size());
         if (!tabellen.isEmpty()) { for (ExtrahierteTabelle t : tabellen) tabelleComboBox.addItem(t); tabelleComboBox.setEnabled(true); ExtrahierteTabelle modelSel = model.getAusgewaehlteTabelle(); boolean selSet = false; if (selectedTable instanceof ExtrahierteTabelle && tabellen.contains(selectedTable)) { tabelleComboBox.setSelectedItem(selectedTable); selSet=true; if(!Objects.equals(modelSel, selectedTable)) model.setAusgewaehlteTabelle((ExtrahierteTabelle)selectedTable); } else if (modelSel != null && tabellen.contains(modelSel)) { tabelleComboBox.setSelectedItem(modelSel); selSet=true; } else { ExtrahierteTabelle first = tabellen.get(0); tabelleComboBox.setSelectedItem(first); if(!Objects.equals(modelSel, first)) model.setAusgewaehlteTabelle(first); selSet=true; } if (!selSet && modelSel != null) model.setAusgewaehlteTabelle(null); }
         else { log.debug("--> Keine Tabellen gefunden."); if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null); }
     } else { log.debug("--> Kein PDF ausgewählt."); if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null); }
     for(ActionListener l : ls)tabelleComboBox.addActionListener(l); log.debug("Tabellen ComboBox Update abgeschlossen.");
 }

 /** Aktualisiert die Hauptdatentabelle. */
 private void updateDatenTabelle() {
     log.info("MainFrame.updateDatenTabelle für: {}", model.getAusgewaehlteTabelle());
     Optional<List<List<String>>> dataOpt = model.getAusgewaehlteTabellenDaten();
     if (dataOpt.isPresent()) {
         List<List<String>> data = dataOpt.get(); log.debug("--> Daten erhalten ({} Zeilen)", data.size());
         if (!data.isEmpty()) { Vector<String> h=new Vector<>(data.get(0));Vector<Vector<Object>> dv=new Vector<>(); for(int i=1;i<data.size();i++)dv.add(new Vector<>(data.get(i))); log.info("---> Setze Daten: {} Zeilen, {} Spalten", dv.size(), h.size()); tabellenModell.setDataVector(dv,h); logMessage("Zeige: " + model.getAusgewaehlteTabelle()); SwingUtilities.invokeLater(()->{if(datenTabelle.getColumnCount()>0){TabellenSpaltenAnpasser tca=new TabellenSpaltenAnpasser(datenTabelle); tca.adjustColumns(); TableColumnModel cm=datenTabelle.getColumnModel(); for(int i=0;i<cm.getColumnCount();i++){TableColumn c=cm.getColumn(i); int w=c.getPreferredWidth(); c.setPreferredWidth(w*2);}}});}
         else { log.warn("--> Tabellendaten leer."); tabellenModell.setDataVector(new Vector<>(),new Vector<>()); logMessage("Tabelle ist leer: "+model.getAusgewaehlteTabelle()); }
     } else { log.warn("--> Keine Tabellendaten vom Modell."); tabellenModell.setDataVector(new Vector<>(),new Vector<>()); String status; if(model.getAusgewaehltesDokument()!=null&&model.getAusgewaehlteTabelle()!=null)status="Keine Daten verfügbar."; else if(model.getAusgewaehltesDokument()!=null)status="Keine Tabelle ausgewählt."; else status="Kein PDF ausgewählt."; logMessage(status);}
 }

 /** Fügt eine Nachricht zum Log-Textbereich hinzu. */
 public void logMessage(String nachricht) {
     SwingUtilities.invokeLater(() -> {
         if (logTextArea != null) {
             String timestamp = LocalDateTime.now().format(LOG_TIME_FORMATTER);
             logTextArea.append(timestamp + " - " + nachricht + "\n");
             logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
         } else { log.warn("LogTextArea ist null: '{}'", nachricht); }
     });
 }

 /** Setzt die Sichtbarkeit des Fortschrittsbalkens. */
 public void setProgressBarVisible(boolean visible) { SwingUtilities.invokeLater(() -> progressBar.setVisible(visible)); }

 /** Setzt den Wert des Fortschrittsbalkens. */
 public void setProgressBarValue(int value) { SwingUtilities.invokeLater(() -> { progressBar.setValue(value); progressBar.setString(value + "%"); }); }

 /** Aktualisiert die Felder im "Table Definition Panel". */
 public void updateInvoiceTypeDisplay(InvoiceTypeConfig config) {
	 SwingUtilities.invokeLater(() -> {
         // boolean enableUpdate = false; // Nicht mehr hier benötigt
         if (config != null) {
             txtDetectedKeyword.setText(config.getKeyword());
             txtDetectedType.setText(config.getType());
             txtDetectedAreaType.setText(config.getAreaType());
             txtDetectedFlavor.setText(config.getDefaultFlavor());
             txtDetectedRowTol.setText(config.getDefaultRowTol());
             // if (!InvoiceTypeService.DEFAULT_KEYWORD.equalsIgnoreCase(config.getKeyword())) {
             //     enableUpdate = true;
             // } // Nicht mehr hier
         } else {
             txtDetectedKeyword.setText("-"); txtDetectedType.setText("-"); txtDetectedAreaType.setText("-"); txtDetectedFlavor.setText("-"); txtDetectedRowTol.setText("-");
         }
         // btnUpdateCsv.setEnabled(enableUpdate); // Nicht mehr hier setzen!
     });
 }

 /** Setzt den Enabled-Status des Refresh-Buttons. */
 public void setRefreshButtonEnabled(boolean enabled) { SwingUtilities.invokeLater(() -> btnRefresh.setEnabled(enabled)); }

 /** Setzt den Enabled-Status des Update-CSV-Buttons. */
 public void setUpdateCsvButtonEnabled(boolean enabled) {
     // Füge Log hinzu, um zu sehen, ob die Methode erreicht wird
     log.debug("MainFrame.setUpdateCsvButtonEnabled({}) aufgerufen.", enabled);
     SwingUtilities.invokeLater(() -> {
         if (btnUpdateCsv != null) { // Sicherstellen, dass Button initialisiert ist
             btnUpdateCsv.setEnabled(enabled);
             log.trace("--> btnUpdateCsv.setEnabled({}) ausgeführt.", enabled);
         } else {
             log.error("!!! btnUpdateCsv ist null in setUpdateCsvButtonEnabled !!!");
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
                  updatePdfComboBox();
                  if (model.getDokumente().isEmpty()) { updateInvoiceTypeDisplay(null); setRefreshButtonEnabled(false); setUpdateCsvButtonEnabled(false);}
                  break;
              case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                  log.info("-> propertyChange: SELECTED_DOCUMENT auf '{}'", evt.getNewValue());
                  updateTabelleComboBox();
                  setRefreshButtonEnabled(model.getAusgewaehltesDokument() != null);
                  // Das Invoice Panel Update wird vom Controller angestoßen
                  // wenn dieses Event vom Modell kommt und der Controller es behandelt (falls nötig)
                  // oder direkt bei der Benutzeraktion im Controller
                  break;
              case AnwendungsModell.SELECTED_TABLE_PROPERTY:
                  log.info("-> propertyChange: SELECTED_TABLE auf '{}'", evt.getNewValue());
                  updateDatenTabelle();
                  synchronizeTableComboBoxSelection();
                  break;
              case AnwendungsModell.ACTIVE_CONFIG_PROPERTY:
                  log.info("-> propertyChange: ACTIVE_CONFIG auf '{}'", evt.getNewValue());
                  updateConfigurationComboBox(model.getConfigurationService().loadAllConfigurations(), model.getAktiveKonfiguration());
                  break;
              case AnwendungsModell.SINGLE_DOCUMENT_REPROCESSED_PROPERTY:
                  PdfDokument reprocessedDoc = (PdfDokument) evt.getNewValue();
                  log.info("-> propertyChange: SINGLE_DOCUMENT_REPROCESSED für '{}'", (reprocessedDoc != null ? reprocessedDoc.getSourcePdf() : "null"));
                  if (reprocessedDoc != null && reprocessedDoc.equals(model.getAusgewaehltesDokument())) {
                       log.info("--> Aktualisiere GUI nach Neuverarbeitung.");
                       updateTabelleComboBox();
                       updateDatenTabelle();
                       updateInvoiceTypeDisplay(reprocessedDoc.getDetectedInvoiceType()); // Aktualisiere auch Invoice Panel
                  } else { log.debug("--> Event ist nicht für das aktuell ausgewählte Dokument."); }
                  break;
              default:
                  log.debug("-> propertyChange: Ignoriere Event '{}'", propertyName);
                  break;
         }
     });
 }

  // --- Synchronisierungs-Hilfsmethoden ---
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