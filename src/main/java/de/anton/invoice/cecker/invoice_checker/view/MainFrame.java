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
 private JComboBox<PdfDokument> pdfComboBox;
 private JComboBox<ExtrahierteTabelle> tabelleComboBox;
 private JComboBox<Object> configComboBox; // Für Bereichs-Konfigs
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
 private JButton btnEditCsv;
 // GUI Elemente - Statusleiste Unten
 private JLabel statusLabel;
 private JProgressBar progressBar;
 // GUI Elemente - Menü
 private JMenuBar menuBar;
 private JMenuItem openConfigEditorMenuItem;


 /**
  * Konstruktor für das Hauptfenster.
  * @param model Das Anwendungsmodell.
  */
 public MainFrame(AnwendungsModell model) {
     this.model = model;
     this.model.addPropertyChangeListener(this);

     setTitle("PDF Tabellen Extraktor");
     // Größe leicht angepasst für neues Panel
     setSize(1250, 850);
     setMinimumSize(new Dimension(1000, 600)); // Mindestgröße
     setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
     setLocationRelativeTo(null);

     initKomponenten();
     layoutKomponenten();

     addWindowListener(new WindowAdapter() {
         @Override public void windowClosing(WindowEvent e) {
             log.info("Fenster schließt."); model.shutdownExecutor(); dispose(); System.exit(0);
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
     exportExcelButton.setEnabled(false);
     btnRefresh = new JButton("Neu verarbeiten");
     btnRefresh.setToolTipText("Verarbeitet das aktuell ausgewählte PDF erneut mit den eingestellten Parametern/Konfigs.");
     btnRefresh.setEnabled(false); // Aktivieren, wenn PDF ausgewählt ist
     btnEditCsv = new JButton("Typdefinitionen (CSV)...");
     btnEditCsv.setToolTipText("Öffnet die invoice-config.csv im Standardeditor.");

     // ComboBoxen
     pdfComboBox = new JComboBox<>();
     tabelleComboBox = new JComboBox<>();
     tabelleComboBox.setEnabled(false);
     configComboBox = new JComboBox<>(); // Wird mit String "Keine" und ExtractionConfiguration Objekten befüllt
     configComboBox.setPreferredSize(new Dimension(160, configComboBox.getPreferredSize().height));
     configComboBox.setToolTipText("Aktive Bereichs-Konfiguration auswählen ('Keine' = ohne Bereiche)");

     // Parameter
     flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"});
     flavorComboBox.setSelectedItem("lattice");
     flavorComboBox.setToolTipText("Manuelle Auswahl der Camelot Extraktionsmethode.");
     SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1); // Default 2, Min 0, Max 100, Step 1
     rowToleranceSpinner = new JSpinner(spinnerModel);
     rowToleranceSpinner.setToolTipText("Manuelle Zeilentoleranz für 'stream'-Methode (höher=toleranter).");
     rowToleranceLabel = new JLabel("Row Tol (Stream):");

     // Tabelle
     tabellenModell = new DefaultTableModel();
     datenTabelle = new JTable(tabellenModell);
     datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Wichtig für viele/breite Spalten

     // Menü
     menuBar = new JMenuBar();
     JMenu configMenu = new JMenu("Konfiguration");
     menuBar.add(configMenu);
     openConfigEditorMenuItem = new JMenuItem("Bereichsdefinition Editor...");
     openConfigEditorMenuItem.setToolTipText("Öffnet einen Dialog zum Definieren von Tabellenbereichen für verschiedene Konfigurationen.");
     configMenu.add(openConfigEditorMenuItem);

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
     topPanel.add(ladePdfButton); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(exportExcelButton); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(btnRefresh); topPanel.add(Box.createHorizontalStrut(20));
     topPanel.add(new JLabel("PDF:")); topPanel.add(pdfComboBox); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(new JLabel("Tabelle:")); topPanel.add(tabelleComboBox); topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(new JLabel("Bereichs-Konfig:")); topPanel.add(configComboBox);
     topPanel.add(Box.createHorizontalGlue()); // Schiebt Parameter nach rechts
     JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
     parameterPanel.setBorder(BorderFactory.createTitledBorder("Manuelle Parameter"));
     parameterPanel.add(new JLabel("Flavor:")); parameterPanel.add(flavorComboBox); parameterPanel.add(Box.createHorizontalStrut(10));
     parameterPanel.add(rowToleranceLabel); rowToleranceSpinner.setPreferredSize(new Dimension(60, rowToleranceSpinner.getPreferredSize().height)); parameterPanel.add(rowToleranceSpinner);
     topPanel.add(parameterPanel);

     // --- Center Panel (Tabelle + Invoice Type Panel) ---
     JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
     JScrollPane tableScrollPane = new JScrollPane(datenTabelle);
     centerPanel.add(tableScrollPane, BorderLayout.CENTER);

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
     gbcT.gridx = 5; gbcT.weightx = 0.0; tableDefinitionPanel.add(txtDetectedRowTol, gbcT); // Kein horizontales Wachsen für letztes Feld
     // Button rechts, nimmt restlichen horizontalen Platz
     gbcT.gridx = 6; gbcT.gridy = 0; gbcT.gridheight = 2; gbcT.weightx = 1.0; // Wächst horizontal
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
     setJMenuBar(menuBar); // Menüleiste oben
     add(topPanel, BorderLayout.NORTH); // Steuerung oben
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
 public void addConfigMenuOpenListener(ActionListener listener) { if(openConfigEditorMenuItem!=null) openConfigEditorMenuItem.addActionListener(listener); }
 public void addConfigSelectionListener(ItemListener listener) { configComboBox.addItemListener(listener); }


 // --- Getter für Komponenten (damit Controller darauf zugreifen kann) ---
 public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
 public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
 public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
 public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }
 public JComboBox<Object> getConfigComboBox() { return configComboBox; }


 // --- Methoden zur Aktualisierung der UI-Komponenten ---

 /** Aktualisiert die Konfigurations-ComboBox. */
 public void updateConfigurationComboBox(List<ExtractionConfiguration> availableConfigs, ExtractionConfiguration activeConfig) {
     log.debug("MainFrame.updateConfigurationComboBox aufgerufen mit {} Konfigs. Aktiv: {}", (availableConfigs != null ? availableConfigs.size() : 0), (activeConfig != null ? activeConfig.getName() : "Keine"));
     ItemListener[] listeners = configComboBox.getItemListeners(); for(ItemListener l : listeners) configComboBox.removeItemListener(l);
     configComboBox.removeAllItems(); configComboBox.addItem("Keine");
     if (availableConfigs != null) { availableConfigs.sort(Comparator.comparing(ExtractionConfiguration::getName, String.CASE_INSENSITIVE_ORDER)); for (ExtractionConfiguration config : availableConfigs) { configComboBox.addItem(config); } }
     if (activeConfig != null && availableConfigs != null && availableConfigs.contains(activeConfig)) { configComboBox.setSelectedItem(activeConfig); }
     else { configComboBox.setSelectedItem("Keine"); }
     for(ItemListener l : listeners) configComboBox.addItemListener(l);
     log.debug("Konfigurations-ComboBox Update abgeschlossen.");
 }

 /** Aktualisiert die PDF-ComboBox. */
 private void updatePdfComboBox() {
     log.info("MainFrame.updatePdfComboBox START");
     Object selectedPdf = pdfComboBox.getSelectedItem();
     ActionListener[] pdfListeners = pdfComboBox.getActionListeners(); for (ActionListener l : pdfListeners) pdfComboBox.removeActionListener(l);
     pdfComboBox.removeAllItems();
     List<PdfDokument> dokumente = model.getDokumente();
     log.debug("--> Fülle PDF ComboBox mit {} Dokumenten.", (dokumente != null ? dokumente.size() : 0));
     boolean hatPdfElemente = false;
     if (dokumente != null && !dokumente.isEmpty()) { for (PdfDokument doc : dokumente) pdfComboBox.addItem(doc); exportExcelButton.setEnabled(true); hatPdfElemente = true; }
     else { exportExcelButton.setEnabled(false); }
     boolean selectionSet = false;
     if (selectedPdf instanceof PdfDokument && dokumente != null && dokumente.contains(selectedPdf)) { pdfComboBox.setSelectedItem(selectedPdf); log.debug("--> PDF Auswahl wiederhergestellt: {}", selectedPdf); selectionSet = true; }
     else if (hatPdfElemente) { PdfDokument erstesElement = dokumente.get(0); pdfComboBox.setSelectedItem(erstesElement); selectionSet = true; log.info("--> Erstes PDF '{}' als ausgewählt in ComboBox gesetzt.", erstesElement.getSourcePdf()); if (!Objects.equals(model.getAusgewaehltesDokument(), erstesElement)) { log.info("---> Rufe model.setAusgewaehltesDokument auf..."); model.setAusgewaehltesDokument(erstesElement); } else { log.debug("---> Modell hatte bereits korrekten Eintrag. Triggere Updates manuell."); updateTabelleComboBox(); updateDatenTabelle(); updateInvoiceTypeDisplay(null); /* Controller holt es neu */}} // Invoice Panel leeren, Controller lädt neu
     if (!selectionSet) { log.debug("--> ComboBox leer oder keine Auswahl gesetzt."); if (model.getAusgewaehltesDokument() != null) model.setAusgewaehltesDokument(null); updateTabelleComboBox(); updateDatenTabelle(); updateInvoiceTypeDisplay(null); } // Auch hier Invoice Panel leeren
     for (ActionListener l : pdfListeners) pdfComboBox.addActionListener(l);
     log.info("MainFrame.updatePdfComboBox ENDE");
 }

 /** Aktualisiert die Tabellen-ComboBox. */
 private void updateTabelleComboBox() {
     log.info("MainFrame.updateTabelleComboBox aufgerufen.");
     Object selectedTable = tabelleComboBox.getSelectedItem();
     ActionListener[] tableListeners = tabelleComboBox.getActionListeners(); for(ActionListener l : tableListeners) tabelleComboBox.removeActionListener(l);
     tabelleComboBox.removeAllItems(); tabelleComboBox.setEnabled(false);
     PdfDokument currentPdf = model.getAusgewaehltesDokument();
     if (currentPdf != null) {
         List<ExtrahierteTabelle> tabellen = model.getVerfuegbareTabellen();
         log.debug("--> Fülle Tabellen Combo mit {} Tabellen für PDF '{}'.", tabellen.size(), currentPdf.getSourcePdf());
         if (!tabellen.isEmpty()) { for (ExtrahierteTabelle t : tabellen) tabelleComboBox.addItem(t); tabelleComboBox.setEnabled(true); ExtrahierteTabelle modelSel = model.getAusgewaehlteTabelle(); boolean selSet = false; if (selectedTable instanceof ExtrahierteTabelle && tabellen.contains(selectedTable)) { tabelleComboBox.setSelectedItem(selectedTable); selSet=true; if(!Objects.equals(modelSel, selectedTable)) model.setAusgewaehlteTabelle((ExtrahierteTabelle)selectedTable); } else if (modelSel != null && tabellen.contains(modelSel)) { tabelleComboBox.setSelectedItem(modelSel); selSet=true; } else { ExtrahierteTabelle first = tabellen.get(0); tabelleComboBox.setSelectedItem(first); log.warn("--> Setze erstes Element '{}' in Tabellen-Combo als Fallback.", first); if(!Objects.equals(modelSel, first)) model.setAusgewaehlteTabelle(first); selSet=true; } if (!selSet && modelSel != null) model.setAusgewaehlteTabelle(null); }
         else { if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null); }
     } else { if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null); }
     for(ActionListener l : tableListeners) tabelleComboBox.addActionListener(l); log.debug("Tabellen ComboBox Update abgeschlossen.");
 }

 /** Aktualisiert die Hauptdatentabelle. */
 private void updateDatenTabelle() {
     log.info("MainFrame.updateDatenTabelle aufgerufen für: {}", model.getAusgewaehlteTabelle());
     Optional<List<List<String>>> dataOpt = model.getAusgewaehlteTabellenDaten();
     if (dataOpt.isPresent()) {
         List<List<String>> data = dataOpt.get();
         log.debug("--> Daten erhalten ({} Zeilen)", data.size());
         if (!data.isEmpty()) {
             Vector<String> headers = new Vector<>(data.get(0)); Vector<Vector<Object>> dv = new Vector<>();
             for (int i = 1; i < data.size(); i++) dv.add(new Vector<>(data.get(i)));
             log.info("---> Setze Daten: {} Zeilen, {} Spalten", dv.size(), headers.size());
             tabellenModell.setDataVector(dv, headers);
             setStatus("Zeige: " + model.getAusgewaehlteTabelle());
             SwingUtilities.invokeLater(() -> { if (datenTabelle.getColumnCount() > 0) { TabellenSpaltenAnpasser tca=new TabellenSpaltenAnpasser(datenTabelle); tca.adjustColumns(); TableColumnModel cm=datenTabelle.getColumnModel(); for(int i=0;i<cm.getColumnCount();i++){ TableColumn c=cm.getColumn(i); int w=c.getPreferredWidth(); c.setPreferredWidth(w*2); } log.debug("---> Spaltenbreiten verdoppelt."); }});
         } else { log.warn("--> Tabellendaten leer."); tabellenModell.setDataVector(new Vector<>(), new Vector<>()); setStatus("Tabelle ist leer: " + model.getAusgewaehlteTabelle()); }
     } else { log.warn("--> Keine Tabellendaten vom Modell."); tabellenModell.setDataVector(new Vector<>(), new Vector<>()); /* Status setzen */ if(model.getAusgewaehltesDokument()!=null&&model.getAusgewaehlteTabelle()!=null)setStatus("Keine Daten verfügbar."); else if(model.getAusgewaehltesDokument()!=null)setStatus("Keine Tabelle ausgewählt."); else setStatus("Kein PDF ausgewählt.");}
 }

 /** Setzt den Text in der Statusleiste. */
 public void setStatus(String nachricht) { SwingUtilities.invokeLater(() -> statusLabel.setText(nachricht)); }

 /** Setzt die Sichtbarkeit des Fortschrittsbalkens. */
 public void setProgressBarVisible(boolean visible) { SwingUtilities.invokeLater(() -> progressBar.setVisible(visible)); }

 /** Setzt den Wert (0-100) des Fortschrittsbalkens. */
 public void setProgressBarValue(int value) { SwingUtilities.invokeLater(() -> { progressBar.setValue(value); progressBar.setString(value + "%"); }); }

 /** Aktualisiert die Felder im "Table Definition Panel". */
 public void updateInvoiceTypeDisplay(InvoiceTypeConfig config) {
     SwingUtilities.invokeLater(() -> {
         if (config != null) {
             txtDetectedKeyword.setText(config.getKeyword());
             txtDetectedType.setText(config.getType());
             txtDetectedAreaType.setText(config.getAreaType());
             txtDetectedFlavor.setText(config.getDefaultFlavor());
             txtDetectedRowTol.setText(config.getDefaultRowTol());
             // Refresh Button wird jetzt im PropertyChangeListener für SELECTED_DOCUMENT aktiviert/deaktiviert
         } else {
             txtDetectedKeyword.setText("-"); txtDetectedType.setText("-"); txtDetectedAreaType.setText("-"); txtDetectedFlavor.setText("-"); txtDetectedRowTol.setText("-");
             btnRefresh.setEnabled(false); // Sicherstellen, dass Refresh deaktiviert ist
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
                  updatePdfComboBox(); // Aktualisiert PDF Liste und löst ggf. weitere Updates aus
                  // Invoice Panel wird durch nachfolgendes SELECTED_DOCUMENT Event geleert/aktualisiert
                  if (model.getDokumente().isEmpty()) { updateInvoiceTypeDisplay(null); } // Explizit leeren, wenn Liste leer ist
                  break;
              case AnwendungsModell.SINGLE_DOCUMENT_REPROCESSED_PROPERTY:
                  // Ein einzelnes Dokument wurde neu verarbeitet
                  log.info("-> propertyChange: SINGLE_DOCUMENT_REPROCESSED für '{}'", evt.getNewValue());
                  // Prüfe, ob das Event für das aktuell ausgewählte Dokument ist
                  if (Objects.equals(evt.getNewValue(), model.getAusgewaehltesDokument())) {
                       log.info("--> Aktualisiere Tabellen-ComboBox und Daten-Tabelle für neu verarbeitetes Dokument.");
                       updateTabelleComboBox();
                       updateDatenTabelle();
                  } else {
                       log.debug("--> Event ist nicht für das aktuell ausgewählte Dokument, ignoriere Tabellen-Updates.");
                  }
                  break;
              case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                  log.info("-> propertyChange: SELECTED_DOCUMENT auf '{}'", evt.getNewValue());
                  updateTabelleComboBox(); // Aktualisiert Tabellenliste
                  btnRefresh.setEnabled(model.getAusgewaehltesDokument() != null); // Aktualisiert Refresh Button
                  // Invoice Panel wird durch Controller aktualisiert, nicht mehr hier
                  break;
              case AnwendungsModell.SELECTED_TABLE_PROPERTY:
                  log.info("-> propertyChange: SELECTED_TABLE auf '{}'", evt.getNewValue());
                  updateDatenTabelle(); // Aktualisiert JTable Anzeige
                  synchronizeTableComboBoxSelection(); // Synchronisiert Tabellen ComboBox
                  break;
              case AnwendungsModell.ACTIVE_CONFIG_PROPERTY:
                  log.info("-> propertyChange: ACTIVE_CONFIG auf '{}'", evt.getNewValue());
                  // Aktualisiere die Konfig ComboBox, um die Auswahl widerzuspiegeln
                  updateConfigurationComboBox(model.getConfigurationService().loadAllConfigurations(), model.getAktiveKonfiguration());
                  break;
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