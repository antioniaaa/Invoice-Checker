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


public class MainFrame extends JFrame implements PropertyChangeListener {
 private static final Logger log = LoggerFactory.getLogger(MainFrame.class);

 private final AnwendungsModell model;
 // GUI Elemente
 private JButton ladePdfButton;
 private JButton exportExcelButton;
 private JButton btnRefresh; // NEU
 private JButton btnEditCsv; // NEU
 private JComboBox<PdfDokument> pdfComboBox;
 private JComboBox<ExtrahierteTabelle> tabelleComboBox;
 private JTable datenTabelle;
 private DefaultTableModel tabellenModell;
 private JLabel statusLabel;
 // Parameter Elemente
 private JComboBox<String> flavorComboBox;
 private JSpinner rowToleranceSpinner;
 private JLabel rowToleranceLabel;
 // Konfiguration Elemente
 private JMenuBar menuBar;
 private JMenuItem openConfigEditorMenuItem;
 private JComboBox<Object> configComboBox;
 // Invoice Type Panel Elemente
 private JPanel tableDefinitionPanel;
 private JTextField txtDetectedKeyword;
 private JTextField txtDetectedType;
 private JTextField txtDetectedAreaType;
 private JTextField txtDetectedFlavor;
 private JTextField txtDetectedRowTol;
 // Fortschrittsbalken
 private JProgressBar progressBar;


 public MainFrame(AnwendungsModell model) {
     this.model = model;
     this.model.addPropertyChangeListener(this);

     setTitle("PDF Tabellen Extraktor");
     setSize(1250, 800); // Etwas breiter/höher
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

 /** Initialisiert alle GUI-Komponenten. */
 private void initKomponenten() {
     // Buttons
     ladePdfButton = new JButton("PDF(s) laden");
     exportExcelButton = new JButton("Nach Excel exportieren");
     exportExcelButton.setEnabled(false);
     btnRefresh = new JButton("Neu verarbeiten");
     btnRefresh.setToolTipText("Verarbeitet das aktuell ausgewählte PDF erneut mit den eingestellten Parametern/Konfigs.");
     btnRefresh.setEnabled(false);
     btnEditCsv = new JButton("Typdefinitionen (CSV)...");
     btnEditCsv.setToolTipText("Öffnet die invoice-config.csv im Standardeditor.");

     // ComboBoxen
     pdfComboBox = new JComboBox<>();
     tabelleComboBox = new JComboBox<>();
     tabelleComboBox.setEnabled(false);
     configComboBox = new JComboBox<>();
     configComboBox.setPreferredSize(new Dimension(160, configComboBox.getPreferredSize().height));
     configComboBox.setToolTipText("Aktive Bereichs-Konfiguration auswählen ('Keine' = ohne Bereiche)");

     // Parameter
     flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"});
     flavorComboBox.setSelectedItem("lattice");
     flavorComboBox.setToolTipText("Manuelle Auswahl der Camelot Extraktionsmethode.");
     SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1);
     rowToleranceSpinner = new JSpinner(spinnerModel);
     rowToleranceSpinner.setToolTipText("Manuelle Zeilentoleranz für 'stream'-Methode.");
     rowToleranceLabel = new JLabel("Row Tol (Stream):");

     // Tabelle
     tabellenModell = new DefaultTableModel();
     datenTabelle = new JTable(tabellenModell);
     datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

     // Menü
     menuBar = new JMenuBar();
     JMenu configMenu = new JMenu("Konfiguration");
     menuBar.add(configMenu);
     openConfigEditorMenuItem = new JMenuItem("Bereichsdefinition Editor...");
     configMenu.add(openConfigEditorMenuItem);

     // Statusleiste & Fortschritt
     statusLabel = new JLabel("Bereit.");
     progressBar = new JProgressBar(0, 100);
     progressBar.setStringPainted(true);
     progressBar.setVisible(false); // Startet unsichtbar

     // Table Definition Panel Komponenten
     tableDefinitionPanel = new JPanel(new GridBagLayout());
     tableDefinitionPanel.setBorder(BorderFactory.createTitledBorder("Erkannter Rechnungstyp & Parameter (aus CSV)"));
     txtDetectedKeyword = createReadOnlyTextField(15);
     txtDetectedType = createReadOnlyTextField(15);
     txtDetectedAreaType = createReadOnlyTextField(10);
     txtDetectedFlavor = createReadOnlyTextField(8);
     txtDetectedRowTol = createReadOnlyTextField(4);
 }

 /** Hilfsmethode für Textfelder. */
 private JTextField createReadOnlyTextField(int columns) {
     JTextField tf = new JTextField(columns);
     tf.setEditable(false);
     tf.setBackground(SystemColor.control); // Standard Hintergrundfarbe für inaktive Felder
     tf.setToolTipText("Dieser Wert wird automatisch aus der invoice-config.csv ermittelt.");
     return tf;
 }

 /** Ordnet die Komponenten im Fenster an. */
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
     topPanel.add(Box.createHorizontalGlue()); // Nach rechts schieben
     JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); /*...*/ parameterPanel.setBorder(BorderFactory.createTitledBorder("Manuelle Parameter")); parameterPanel.add(new JLabel("Flavor:")); parameterPanel.add(flavorComboBox); parameterPanel.add(Box.createHorizontalStrut(10)); parameterPanel.add(rowToleranceLabel); rowToleranceSpinner.setPreferredSize(new Dimension(60, rowToleranceSpinner.getPreferredSize().height)); parameterPanel.add(rowToleranceSpinner);
     topPanel.add(parameterPanel);

     // --- Center Panel ---
     JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
     JScrollPane tableScrollPane = new JScrollPane(datenTabelle);
     centerPanel.add(tableScrollPane, BorderLayout.CENTER); // Tabelle oben/mittig

     // Table Definition Panel Layout
     GridBagConstraints gbcT = new GridBagConstraints();
     gbcT.insets = new Insets(3, 5, 3, 5); gbcT.anchor = GridBagConstraints.WEST;
     gbcT.gridx = 0; gbcT.gridy = 0; tableDefinitionPanel.add(new JLabel("Erk. Keyword:"), gbcT);
     gbcT.gridx = 1; tableDefinitionPanel.add(txtDetectedKeyword, gbcT);
     gbcT.gridx = 2; tableDefinitionPanel.add(new JLabel("Typ:"), gbcT);
     gbcT.gridx = 3; tableDefinitionPanel.add(txtDetectedType, gbcT);
     gbcT.gridx = 4; gbcT.gridy = 0; gbcT.gridheight=2; gbcT.anchor=GridBagConstraints.EAST; gbcT.fill = GridBagConstraints.VERTICAL; // Button rechts, über 2 Zeilen
     tableDefinitionPanel.add(btnEditCsv, gbcT);

     gbcT.gridx = 0; gbcT.gridy = 1; gbcT.gridwidth=1; gbcT.gridheight=1; gbcT.anchor=GridBagConstraints.WEST; gbcT.fill=GridBagConstraints.NONE; // Reset
     tableDefinitionPanel.add(new JLabel("Bereichs-Typ:"), gbcT);
     gbcT.gridx = 1; tableDefinitionPanel.add(txtDetectedAreaType, gbcT);
     gbcT.gridx = 2; tableDefinitionPanel.add(new JLabel("Def. Flavor:"), gbcT);
     gbcT.gridx = 3; tableDefinitionPanel.add(txtDetectedFlavor, gbcT);
     // Row Tol neben Flavor
     // gbcT.gridx = 4; tableDefinitionPanel.add(new JLabel("Def. RowTol:"), gbcT); // Label nicht unbedingt nötig
     // gbcT.gridx = 5; tableDefinitionPanel.add(txtDetectedRowTol, gbcT);
     centerPanel.add(tableDefinitionPanel, BorderLayout.SOUTH); // Füge Panel unten hinzu

     // --- Bottom Panel ---
     JPanel bottomPanel = new JPanel(new BorderLayout());
     bottomPanel.add(statusLabel, BorderLayout.CENTER);
     bottomPanel.add(progressBar, BorderLayout.EAST);

     // --- Gesamtlayout ---
     setLayout(new BorderLayout());
     setJMenuBar(menuBar);
     add(topPanel, BorderLayout.NORTH);
     add(centerPanel, BorderLayout.CENTER);
     add(bottomPanel, BorderLayout.SOUTH);
 }

 // --- Methoden für den Controller ---
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

 // --- Getter für Komponenten ---
 public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
 public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
 public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
 public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }
 public JComboBox<Object> getConfigComboBox() { return configComboBox; }

 // --- UI Update Methoden ---

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

 private void updatePdfComboBox() {
     log.info("MainFrame.updatePdfComboBox START");
     Object selectedPdf = pdfComboBox.getSelectedItem();
     ActionListener[] pdfListeners = pdfComboBox.getActionListeners(); for (ActionListener l : pdfListeners) pdfComboBox.removeActionListener(l);
     pdfComboBox.removeAllItems();
     List<PdfDokument> dokumente = model.getDokumente();
     log.debug("--> Fülle PDF ComboBox mit {} Dokumenten.", (dokumente != null ? dokumente.size() : 0));
     boolean hatPdfElemente = false;
     if (dokumente != null && !dokumente.isEmpty()) { for (PdfDokument doc : dokumente) { pdfComboBox.addItem(doc); } exportExcelButton.setEnabled(true); hatPdfElemente = true; }
     else { exportExcelButton.setEnabled(false); }
     boolean selectionSet = false;
     if (selectedPdf instanceof PdfDokument && dokumente != null && dokumente.contains(selectedPdf)) { pdfComboBox.setSelectedItem(selectedPdf); log.debug("--> PDF Auswahl wiederhergestellt: {}", selectedPdf); selectionSet = true; }
     else if (hatPdfElemente) { PdfDokument erstesElement = dokumente.get(0); pdfComboBox.setSelectedItem(erstesElement); selectionSet = true; log.info("--> Erstes PDF '{}' als ausgewählt in ComboBox gesetzt.", erstesElement.getSourcePdf()); if (!Objects.equals(model.getAusgewaehltesDokument(), erstesElement)) { log.info("---> Rufe model.setAusgewaehltesDokument auf..."); model.setAusgewaehltesDokument(erstesElement); } else { log.debug("---> Modell hatte bereits korrektes Element. Triggere Updates manuell."); updateTabelleComboBox(); updateDatenTabelle(); } }
     if (!selectionSet && model.getAusgewaehltesDokument() != null) { log.info("--> Keine PDFs mehr, lösche Auswahl im Modell."); model.setAusgewaehltesDokument(null); }
     else if (!selectionSet) { log.debug("--> ComboBox leer."); updateTabelleComboBox(); updateDatenTabelle(); }
     for (ActionListener l : pdfListeners) pdfComboBox.addActionListener(l); log.info("MainFrame.updatePdfComboBox ENDE");
 }

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
     } else { log.warn("--> Keine Tabellendaten vom Modell. Leere Tabelle."); tabellenModell.setDataVector(new Vector<>(), new Vector<>()); /* Status setzen wie zuvor */ if(model.getAusgewaehltesDokument()!=null&&model.getAusgewaehlteTabelle()!=null)setStatus("Keine Daten verfügbar."); else if(model.getAusgewaehltesDokument()!=null)setStatus("Keine Tabelle ausgewählt."); else setStatus("Kein PDF ausgewählt.");}
 }

 public void setStatus(String nachricht) { SwingUtilities.invokeLater(() -> statusLabel.setText(nachricht)); }

 // NEU: Methoden für Fortschrittsbalken
 public void setProgressBarVisible(boolean visible) { SwingUtilities.invokeLater(() -> progressBar.setVisible(visible)); }
 public void setProgressBarValue(int value) { SwingUtilities.invokeLater(() -> { progressBar.setValue(value); progressBar.setString(value + "%"); }); }

 // NEU: Aktualisiert die Felder im "Table Definition Panel"
 public void updateInvoiceTypeDisplay(InvoiceTypeConfig config) {
     SwingUtilities.invokeLater(() -> {
         if (config != null) {
             txtDetectedKeyword.setText(config.getKeyword());
             txtDetectedType.setText(config.getType());
             txtDetectedAreaType.setText(config.getAreaType());
             txtDetectedFlavor.setText(config.getDefaultFlavor());
             txtDetectedRowTol.setText(config.getDefaultRowTol());
             btnRefresh.setEnabled(model.getAusgewaehltesDokument() != null); // Aktiviere Refresh, wenn PDF da ist
         } else {
             txtDetectedKeyword.setText("-"); txtDetectedType.setText("-"); txtDetectedAreaType.setText("-"); txtDetectedFlavor.setText("-"); txtDetectedRowTol.setText("-");
             btnRefresh.setEnabled(false); // Deaktiviere Refresh
         }
     });
 }

 // --- PropertyChangeListener Implementierung ---
 @Override
 public void propertyChange(PropertyChangeEvent evt) {
     String propertyName = evt.getPropertyName();
     log.debug("MainFrame.propertyChange empfangen: Eigenschaft='{}'", propertyName);
     SwingUtilities.invokeLater(() -> {
          switch (propertyName) {
              case AnwendungsModell.DOCUMENTS_UPDATED_PROPERTY:
                  log.info("-> propertyChange: DOCUMENTS_UPDATED");
                  updatePdfComboBox();
                  // Wenn keine Dokumente mehr da sind, lösche auch Invoice Info
                  if (model.getDokumente().isEmpty()) {
                       updateInvoiceTypeDisplay(null);
                  }
                  break;
              case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                  log.info("-> propertyChange: SELECTED_DOCUMENT");
                  updateTabelleComboBox();
                  // Aktualisiere Invoice Panel und Refresh Button Status
                  // Controller wird updateInvoiceTypePanel aufrufen, hier nur Refresh Button
                  btnRefresh.setEnabled(model.getAusgewaehltesDokument() != null);
                  break;
              case AnwendungsModell.SELECTED_TABLE_PROPERTY:
                  log.info("-> propertyChange: SELECTED_TABLE");
                  updateDatenTabelle();
                  synchronizeTableComboBoxSelection();
                  break;
              case AnwendungsModell.ACTIVE_CONFIG_PROPERTY:
                  log.info("-> propertyChange: ACTIVE_CONFIG");
                  updateConfigurationComboBox(model.getConfigurationService().loadAllConfigurations(), model.getAktiveKonfiguration());
                  break;
              // PROGRESS_UPDATE wird nicht über PropertyChange gemeldet, sondern direkt vom Controller gesetzt
              default:
                  log.debug("-> propertyChange: Ignoriere Event '{}'", propertyName);
                  break;
         }
     });
 }

  // --- Synchronisierungs-Hilfsmethoden ---
 private void synchronizeTableComboBoxSelection() {
      ExtrahierteTabelle modelSelection = model.getAusgewaehlteTabelle();
      if (!Objects.equals(tabelleComboBox.getSelectedItem(), modelSelection)) {
           log.debug("--> Synchronisiere Tabellen ComboBox mit Modell: {}", modelSelection);
           ActionListener[] listeners = tabelleComboBox.getActionListeners(); for (ActionListener l : listeners) tabelleComboBox.removeActionListener(l);
           tabelleComboBox.setSelectedItem(modelSelection); // Setze Auswahl in der GUI
           for (ActionListener l : listeners) tabelleComboBox.addActionListener(l);
      }
 }
}