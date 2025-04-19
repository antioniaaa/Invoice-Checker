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

 private final AnwendungsModell model; // Nur noch Referenz auf das Modell

 // --- GUI Elemente ---
 private JButton ladePdfButton; private JButton exportExcelButton; private JButton btnRefresh; private JButton btnEditCsv;
 private JComboBox<PdfDokument> pdfComboBox; private JComboBox<ExtrahierteTabelle> tabelleComboBox; private JComboBox<Object> configComboBox;
 private JTable datenTabelle; private DefaultTableModel tabellenModell; private JLabel statusLabel;
 private JComboBox<String> flavorComboBox; private JSpinner rowToleranceSpinner; private JLabel rowToleranceLabel;
 private JPanel tableDefinitionPanel; private JTextField txtDetectedKeyword; private JTextField txtDetectedType; private JTextField txtDetectedAreaType; private JTextField txtDetectedFlavor; private JTextField txtDetectedRowTol;
 private JProgressBar progressBar; private JMenuBar menuBar; private JMenuItem openConfigEditorMenuItem; private JMenu configMenu;

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
     setSize(1250, 850);
     setMinimumSize(new Dimension(1000, 600));
     setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
     setLocationRelativeTo(null);

     initKomponenten();
     layoutKomponenten();

     // Listener werden vom Controller über die public add... Methoden registriert

     addWindowListener(new WindowAdapter() {
         @Override public void windowClosing(WindowEvent e) {
             log.info("Fenster schließt."); model.shutdownExecutor(); dispose(); System.exit(0);
         }
     });
 }

 /** Initialisiert alle Swing-Komponenten. */
 private void initKomponenten() {
     ladePdfButton = new JButton("PDF(s) laden"); exportExcelButton = new JButton("Nach Excel exportieren"); exportExcelButton.setEnabled(false); btnRefresh = new JButton("Neu verarbeiten"); btnRefresh.setToolTipText("Verarbeitet das aktuell ausgewählte PDF erneut..."); btnRefresh.setEnabled(false); btnEditCsv = new JButton("Typdefinitionen (CSV)..."); btnEditCsv.setToolTipText("Öffnet die invoice-config.csv..."); pdfComboBox = new JComboBox<>(); tabelleComboBox = new JComboBox<>(); tabelleComboBox.setEnabled(false); configComboBox = new JComboBox<>(); configComboBox.setPreferredSize(new Dimension(160, configComboBox.getPreferredSize().height)); configComboBox.setToolTipText("Aktive Bereichs-Konfiguration auswählen..."); flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"}); flavorComboBox.setSelectedItem("lattice"); flavorComboBox.setToolTipText("Manuelle Extraktionsmethode."); SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1); rowToleranceSpinner = new JSpinner(spinnerModel); rowToleranceSpinner.setToolTipText("Manuelle Zeilentoleranz für 'stream'."); rowToleranceLabel = new JLabel("Row Tol (Stream):"); tabellenModell = new DefaultTableModel(); datenTabelle = new JTable(tabellenModell); datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); menuBar = new JMenuBar(); configMenu = new JMenu("Konfiguration"); menuBar.add(configMenu); openConfigEditorMenuItem = new JMenuItem("Bereichsdefinition Editor..."); openConfigEditorMenuItem.setToolTipText("Öffnet Dialog zum Definieren von Bereichen."); configMenu.add(openConfigEditorMenuItem); statusLabel = new JLabel("Bereit."); statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5)); progressBar = new JProgressBar(0, 100); progressBar.setStringPainted(true); progressBar.setVisible(false); progressBar.setPreferredSize(new Dimension(150, progressBar.getPreferredSize().height)); tableDefinitionPanel = new JPanel(new GridBagLayout()); tableDefinitionPanel.setBorder(BorderFactory.createTitledBorder("Erkannter Rechnungstyp & Parameter (aus CSV)")); txtDetectedKeyword = createReadOnlyTextField(15); txtDetectedType = createReadOnlyTextField(15); txtDetectedAreaType = createReadOnlyTextField(10); txtDetectedFlavor = createReadOnlyTextField(8); txtDetectedRowTol = createReadOnlyTextField(4);
 }

 /** Hilfsmethode für Textfelder. */
 private JTextField createReadOnlyTextField(int columns) { JTextField tf = new JTextField(columns); tf.setEditable(false); tf.setBackground(UIManager.getColor("TextField.inactiveBackground")); tf.setToolTipText("Automatisch aus invoice-config.csv ermittelt."); return tf; }

 /** Ordnet Komponenten im Layout an. */
 private void layoutKomponenten() { JPanel topPanel=new JPanel(); topPanel.setLayout(new BoxLayout(topPanel,BoxLayout.X_AXIS)); topPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5)); topPanel.add(ladePdfButton); topPanel.add(Box.createHorizontalStrut(10)); topPanel.add(exportExcelButton); topPanel.add(Box.createHorizontalStrut(10)); topPanel.add(btnRefresh); topPanel.add(Box.createHorizontalStrut(20)); topPanel.add(new JLabel("PDF:")); topPanel.add(pdfComboBox); topPanel.add(Box.createHorizontalStrut(10)); topPanel.add(new JLabel("Tabelle:")); topPanel.add(tabelleComboBox); topPanel.add(Box.createHorizontalStrut(10)); topPanel.add(new JLabel("Bereichs-Konfig:")); topPanel.add(configComboBox); topPanel.add(Box.createHorizontalGlue()); JPanel parameterPanel=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0)); parameterPanel.setBorder(BorderFactory.createTitledBorder("Manuelle Parameter")); parameterPanel.add(new JLabel("Flavor:")); parameterPanel.add(flavorComboBox); parameterPanel.add(Box.createHorizontalStrut(10)); parameterPanel.add(rowToleranceLabel); rowToleranceSpinner.setPreferredSize(new Dimension(60,rowToleranceSpinner.getPreferredSize().height)); parameterPanel.add(rowToleranceSpinner); topPanel.add(parameterPanel); JPanel centerPanel=new JPanel(new BorderLayout(5,5)); JScrollPane tableScrollPane=new JScrollPane(datenTabelle); centerPanel.add(tableScrollPane,BorderLayout.CENTER); GridBagConstraints gbcT=new GridBagConstraints(); gbcT.insets=new Insets(3,5,3,5); gbcT.anchor=GridBagConstraints.WEST; gbcT.gridx=0; gbcT.gridy=0; tableDefinitionPanel.add(new JLabel("Erk. Keyword:"),gbcT); gbcT.gridx=1; tableDefinitionPanel.add(txtDetectedKeyword,gbcT); gbcT.gridx=2; tableDefinitionPanel.add(new JLabel("Typ:"),gbcT); gbcT.gridx=3; tableDefinitionPanel.add(txtDetectedType,gbcT); gbcT.gridx=0; gbcT.gridy=1; tableDefinitionPanel.add(new JLabel("Bereichs-Typ:"),gbcT); gbcT.gridx=1; tableDefinitionPanel.add(txtDetectedAreaType,gbcT); gbcT.gridx=2; tableDefinitionPanel.add(new JLabel("Def. Flavor:"),gbcT); gbcT.gridx=3; tableDefinitionPanel.add(txtDetectedFlavor,gbcT); gbcT.gridx=4; tableDefinitionPanel.add(new JLabel("Def. RowTol:"),gbcT); gbcT.gridx=5; gbcT.weightx=0.0; tableDefinitionPanel.add(txtDetectedRowTol,gbcT); gbcT.gridx=6; gbcT.gridy=0; gbcT.gridheight=2; gbcT.weightx=1.0; gbcT.anchor=GridBagConstraints.EAST; gbcT.fill=GridBagConstraints.NONE; tableDefinitionPanel.add(btnEditCsv,gbcT); centerPanel.add(tableDefinitionPanel,BorderLayout.SOUTH); JPanel bottomPanel=new JPanel(new BorderLayout(5,0)); bottomPanel.setBorder(BorderFactory.createEmptyBorder(2,5,2,5)); bottomPanel.add(statusLabel,BorderLayout.CENTER); bottomPanel.add(progressBar,BorderLayout.EAST); setLayout(new BorderLayout()); setJMenuBar(menuBar); add(topPanel,BorderLayout.NORTH); add(centerPanel,BorderLayout.CENTER); add(bottomPanel,BorderLayout.SOUTH); }

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

 // --- Getter für Komponenten ---
 public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
 public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
 public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
 public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }
 public JComboBox<Object> getConfigComboBox() { return configComboBox; }

 // --- Methoden zur Aktualisierung der UI-Komponenten ---
 public void updateConfigurationComboBox(List<ExtractionConfiguration> availableConfigs, ExtractionConfiguration activeConfig) { log.debug("MainFrame.updateConfigurationComboBox..."); ItemListener[] ls=configComboBox.getItemListeners(); for(ItemListener l:ls)configComboBox.removeItemListener(l); configComboBox.removeAllItems(); configComboBox.addItem("Keine"); if(availableConfigs!=null){ availableConfigs.sort(Comparator.comparing(ExtractionConfiguration::getName,String.CASE_INSENSITIVE_ORDER)); for(ExtractionConfiguration c:availableConfigs)configComboBox.addItem(c); } if(activeConfig!=null&&availableConfigs!=null&&availableConfigs.contains(activeConfig)){configComboBox.setSelectedItem(activeConfig);} else {configComboBox.setSelectedItem("Keine");} for(ItemListener l:ls)configComboBox.addItemListener(l); log.debug("Konfig-ComboBox Update fertig."); }
 private void updatePdfComboBox() { log.info("MainFrame.updatePdfComboBox START"); Object selPdf=pdfComboBox.getSelectedItem(); ActionListener[] ls=pdfComboBox.getActionListeners(); for(ActionListener l:ls)pdfComboBox.removeActionListener(l); pdfComboBox.removeAllItems(); List<PdfDokument> docs=model.getDokumente(); boolean hasEl=false; if(docs!=null&&!docs.isEmpty()){for(PdfDokument d:docs)pdfComboBox.addItem(d); exportExcelButton.setEnabled(true); hasEl=true;}else{exportExcelButton.setEnabled(false);} boolean selSet=false; if(selPdf instanceof PdfDokument&&docs!=null&&docs.contains(selPdf)){pdfComboBox.setSelectedItem(selPdf);selSet=true;log.debug("--> PDF Auswahl wiederhergestellt.");} else if(hasEl){PdfDokument first=docs.get(0);pdfComboBox.setSelectedItem(first);selSet=true;log.info("--> Erstes PDF '{}' gesetzt.",first.getSourcePdf()); if(!Objects.equals(model.getAusgewaehltesDokument(),first)){log.info("---> Rufe setAusgewaehltesDokument auf..."); model.setAusgewaehltesDokument(first);}else{log.debug("---> Modell aktuell. Manuelle GUI Updates."); updateTabelleComboBox(); updateDatenTabelle(); /* Invoice Panel wird vom Controller getriggert */}} if(!selSet){log.debug("--> Keine PDF Auswahl."); if(model.getAusgewaehltesDokument()!=null)model.setAusgewaehltesDokument(null); updateTabelleComboBox(); updateDatenTabelle(); updateInvoiceTypeDisplay(null);} for(ActionListener l:ls)pdfComboBox.addActionListener(l); log.info("MainFrame.updatePdfComboBox ENDE"); }
 private void updateTabelleComboBox() { log.info("MainFrame.updateTabelleComboBox aufgerufen."); Object selT=tabelleComboBox.getSelectedItem(); ActionListener[] ls=tabelleComboBox.getActionListeners(); for(ActionListener l:ls)tabelleComboBox.removeActionListener(l); tabelleComboBox.removeAllItems(); tabelleComboBox.setEnabled(false); PdfDokument curPdf=model.getAusgewaehltesDokument(); if(curPdf!=null){List<ExtrahierteTabelle> tables=model.getVerfuegbareTabellen(); log.debug("--> Fülle Tabellen Combo mit {} Tabellen.", tables.size()); if(!tables.isEmpty()){for(ExtrahierteTabelle t:tables)tabelleComboBox.addItem(t); tabelleComboBox.setEnabled(true); ExtrahierteTabelle modelSel=model.getAusgewaehlteTabelle(); boolean selSet=false; if(selT instanceof ExtrahierteTabelle&&tables.contains(selT)){tabelleComboBox.setSelectedItem(selT); selSet=true; if(!Objects.equals(modelSel,selT))model.setAusgewaehlteTabelle((ExtrahierteTabelle)selT);} else if(modelSel!=null&&tables.contains(modelSel)){tabelleComboBox.setSelectedItem(modelSel); selSet=true;} else {ExtrahierteTabelle first=tables.get(0); tabelleComboBox.setSelectedItem(first); if(!Objects.equals(modelSel,first))model.setAusgewaehlteTabelle(first); selSet=true;} if(!selSet&&modelSel!=null)model.setAusgewaehlteTabelle(null);}} if(model.getAusgewaehlteTabelle()!=null&&tabelleComboBox.getItemCount()==0)model.setAusgewaehlteTabelle(null); for(ActionListener l:ls)tabelleComboBox.addActionListener(l); log.debug("Tabellen-ComboBox Update fertig.");}
 private void updateDatenTabelle() { log.info("MainFrame.updateDatenTabelle für: {}", model.getAusgewaehlteTabelle()); Optional<List<List<String>>> dataOpt=model.getAusgewaehlteTabellenDaten(); if(dataOpt.isPresent()){List<List<String>> data=dataOpt.get(); log.debug("--> Daten erhalten ({} Zeilen)", data.size()); if(!data.isEmpty()){Vector<String> h=new Vector<>(data.get(0));Vector<Vector<Object>> dv=new Vector<>(); for(int i=1;i<data.size();i++)dv.add(new Vector<>(data.get(i))); log.info("---> Setze Daten: {} Zeilen, {} Spalten", dv.size(), h.size()); tabellenModell.setDataVector(dv,h); setStatus("Zeige: "+model.getAusgewaehlteTabelle()); SwingUtilities.invokeLater(()->{if(datenTabelle.getColumnCount()>0){TabellenSpaltenAnpasser tca=new TabellenSpaltenAnpasser(datenTabelle); tca.adjustColumns(); TableColumnModel cm=datenTabelle.getColumnModel(); for(int i=0;i<cm.getColumnCount();i++){TableColumn c=cm.getColumn(i); int w=c.getPreferredWidth(); c.setPreferredWidth(w*2);}}});}else{log.warn("--> Tabellendaten leer."); tabellenModell.setDataVector(new Vector<>(),new Vector<>()); setStatus("Tabelle ist leer: "+model.getAusgewaehlteTabelle());}}else{log.warn("--> Keine Tabellendaten vom Modell."); tabellenModell.setDataVector(new Vector<>(),new Vector<>()); if(model.getAusgewaehltesDokument()!=null&&model.getAusgewaehlteTabelle()!=null)setStatus("Keine Daten verfügbar."); else if(model.getAusgewaehltesDokument()!=null)setStatus("Keine Tabelle ausgewählt."); else setStatus("Kein PDF ausgewählt.");}}
 public void setStatus(String nachricht) { SwingUtilities.invokeLater(() -> statusLabel.setText(nachricht)); }
 public void setProgressBarVisible(boolean visible) { SwingUtilities.invokeLater(() -> progressBar.setVisible(visible)); }
 public void setProgressBarValue(int value) { SwingUtilities.invokeLater(() -> { progressBar.setValue(value); progressBar.setString(value + "%"); }); }
 public void updateInvoiceTypeDisplay(InvoiceTypeConfig config) { SwingUtilities.invokeLater(() -> { if (config != null) { txtDetectedKeyword.setText(config.getKeyword()); txtDetectedType.setText(config.getType()); txtDetectedAreaType.setText(config.getAreaType()); txtDetectedFlavor.setText(config.getDefaultFlavor()); txtDetectedRowTol.setText(config.getDefaultRowTol()); } else { txtDetectedKeyword.setText("-"); txtDetectedType.setText("-"); txtDetectedAreaType.setText("-"); txtDetectedFlavor.setText("-"); txtDetectedRowTol.setText("-"); btnRefresh.setEnabled(false); } }); }
 public void setRefreshButtonEnabled(boolean enabled) { SwingUtilities.invokeLater(() -> btnRefresh.setEnabled(enabled)); }


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
                  if (model.getDokumente().isEmpty()) { updateInvoiceTypeDisplay(null); setRefreshButtonEnabled(false); }
                  break;
              case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                  log.info("-> propertyChange: SELECTED_DOCUMENT auf '{}'", evt.getNewValue());
                  updateTabelleComboBox();
                  setRefreshButtonEnabled(model.getAusgewaehltesDokument() != null);
                  // Der Controller wird benachrichtigt (via handlePdfComboBoxAuswahl) und ruft das Update für InvoicePanel auf.
                  // Kein direkter Aufruf mehr hier.
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
                       log.info("--> Aktualisiere Tabellen-ComboBox und Daten-Tabelle nach Neuverarbeitung.");
                       updateTabelleComboBox();
                       updateDatenTabelle();
                  } else { log.debug("--> Event ist nicht für das aktuell ausgewählte Dokument."); }
                  break;
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
           tabelleComboBox.setSelectedItem(modelSelection);
           for (ActionListener l : listeners) tabelleComboBox.addActionListener(l);
      }
 }
}