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


//Util Imports
import java.time.LocalDateTime; // Für Zeitstempel im Log-Bereich
import java.time.format.DateTimeFormatter; // Für Zeitstempel im Log-Bereich

import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionListener; // NEU für CRUD Liste
import javax.swing.filechooser.FileNameExtensionFilter;

/**
* Das Hauptfenster (View) der Anwendung mit Tab-basierter Oberfläche.
* Tab 1: Laden von Abrechnungs-PDFs und Anzeige von Log-Meldungen.
* Tab 2: Laden von PDFs zur Detailansicht, Konfiguration, Parameteranpassung,
*        Verwaltung von Rechnungstypen (CRUD) und Tabellenanzeige.
* Lauscht auf Änderungen im Modell. Enthält Fortschrittsanzeige.
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
 private JComboBox<PdfDokument> pdfComboBox;
 private JComboBox<ExtrahierteTabelle> tabelleComboBox;
 private JComboBox<Object> configComboBox; // Bereichs-Konfigs
 private JComboBox<String> flavorComboBox;
 private JSpinner rowToleranceSpinner;
 private JLabel rowToleranceLabel;
 private JTable datenTabelle;
 private DefaultTableModel tabellenModell;
 private InvoiceTypeCrudPanel invoiceTypeCrudPanel; // NEU: Das CRUD Panel ersetzt das alte Info-Panel
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
 public MainFrame(AnwendungsModell model) { // Nimmt KEINEN Controller entgegen
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
     // Tabbed Pane
     tabbedPane = new JTabbedPane();

     // --- Komponenten für Tab 1: Abrechnungen ---
     ladePdfButtonAbrechnung = new JButton("Abrechnungs-PDFs auswählen...");
     ladePdfButtonAbrechnung.setToolTipText("Wählt PDFs für eine zukünftige Abrechnungsfunktion aus.");
     logTextArea = new JTextArea(15, 80);
     logTextArea.setEditable(false); logTextArea.setLineWrap(true); logTextArea.setWrapStyleWord(true); logTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

     // --- Komponenten für Tab 2: Konfiguration & Details ---
     ladePdfButtonDetails = new JButton("PDF(s) laden (Details)"); ladePdfButtonDetails.setToolTipText("Lädt PDFs zur Detailansicht...");
     exportExcelButton = new JButton("Tabelle exportieren"); exportExcelButton.setEnabled(false); exportExcelButton.setToolTipText("Exportiert aktuell angezeigte Tabelle...");
     btnRefresh = new JButton("Neu verarbeiten"); btnRefresh.setToolTipText("Verarbeitet PDF neu..."); btnRefresh.setEnabled(false);
     pdfComboBox = new JComboBox<>(); pdfComboBox.setToolTipText("Verarbeitetes PDF auswählen");
     tabelleComboBox = new JComboBox<>(); tabelleComboBox.setEnabled(false); tabelleComboBox.setToolTipText("Extrahierte Tabelle auswählen");
     configComboBox = new JComboBox<>(); configComboBox.setPreferredSize(new Dimension(160, configComboBox.getPreferredSize().height)); configComboBox.setToolTipText("Aktive Bereichs-Konfiguration");
     flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"}); flavorComboBox.setSelectedItem("lattice"); flavorComboBox.setToolTipText("Manuelle Extraktionsmethode");
     SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1);
     rowToleranceSpinner = new JSpinner(spinnerModel); rowToleranceSpinner.setToolTipText("Manuelle Zeilentoleranz (nur für stream)");
     rowToleranceLabel = new JLabel("Row Tol (Stream):");
     tabellenModell = new DefaultTableModel(); datenTabelle = new JTable(tabellenModell); datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

     // NEU: CRUD Panel initialisieren
     invoiceTypeCrudPanel = new InvoiceTypeCrudPanel();

     // Menü
     menuBar = new JMenuBar(); configMenu = new JMenu("Konfiguration"); menuBar.add(configMenu); openConfigEditorMenuItem = new JMenuItem("Bereichsdefinition Editor..."); openConfigEditorMenuItem.setToolTipText("Definiert Bereiche für die Extraktion"); configMenu.add(openConfigEditorMenuItem);

     // Fortschrittsbalken
     progressBar = new JProgressBar(0, 100); progressBar.setStringPainted(true); progressBar.setVisible(false); progressBar.setPreferredSize(new Dimension(150, progressBar.getPreferredSize().height));
 }

 /** Ordnet die Komponenten im Fenster mit Tabs an. */
 private void layoutKomponenten() {
     // === Tab 1: Abrechnungen ===
     abrechnungenPanel = new JPanel(new BorderLayout(10, 10)); abrechnungenPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
     JPanel loadBtnPanel1 = new JPanel(new FlowLayout(FlowLayout.CENTER)); loadBtnPanel1.add(ladePdfButtonAbrechnung); abrechnungenPanel.add(loadBtnPanel1, BorderLayout.NORTH);
     JScrollPane logScrollPane = new JScrollPane(logTextArea); logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS); abrechnungenPanel.add(logScrollPane, BorderLayout.CENTER);

     // === Tab 2: Konfiguration & Details ===
     configDetailPanel = new JPanel(new BorderLayout(5, 5)); configDetailPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
     // Oberes Panel für Auswahl, Parameter, Laden (Details)
     JPanel topControlPanel = new JPanel(); topControlPanel.setLayout(new BoxLayout(topControlPanel, BoxLayout.X_AXIS)); topControlPanel.setBorder(BorderFactory.createEmptyBorder(0,0,5,0));
     JPanel selectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
     selectionPanel.add(ladePdfButtonDetails); selectionPanel.add(Box.createHorizontalStrut(15));
     selectionPanel.add(new JLabel("PDF:")); selectionPanel.add(pdfComboBox); selectionPanel.add(Box.createHorizontalStrut(10));
     selectionPanel.add(new JLabel("Tabelle:")); selectionPanel.add(tabelleComboBox); selectionPanel.add(Box.createHorizontalStrut(10));
     selectionPanel.add(new JLabel("Bereichs-Konfig:")); selectionPanel.add(configComboBox);
     topControlPanel.add(selectionPanel); topControlPanel.add(Box.createHorizontalGlue());
     JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); parameterPanel.setBorder(BorderFactory.createTitledBorder("Manuelle Parameter")); parameterPanel.add(new JLabel("Flavor:")); parameterPanel.add(flavorComboBox); parameterPanel.add(Box.createHorizontalStrut(10)); parameterPanel.add(rowToleranceLabel); rowToleranceSpinner.setPreferredSize(new Dimension(60, rowToleranceSpinner.getPreferredSize().height)); parameterPanel.add(rowToleranceSpinner);
     topControlPanel.add(parameterPanel);
     configDetailPanel.add(topControlPanel, BorderLayout.NORTH);

     // Mittleres Panel für Tabelle
     JScrollPane tableScrollPane = new JScrollPane(datenTabelle);
     configDetailPanel.add(tableScrollPane, BorderLayout.CENTER);

     // Unteres Panel mit CRUD und Aktionsbuttons
     JPanel bottomArea = new JPanel(new BorderLayout(10, 5)); // Mehr Abstand zwischen CRUD und Buttons
     bottomArea.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0)); // Abstand nach oben
     // Füge das CRUD Panel hinzu (nimmt den linken/mittleren Bereich ein)
     bottomArea.add(invoiceTypeCrudPanel, BorderLayout.CENTER);
     // Panel für die Buttons rechts unten
     JPanel actionButtonPanel = new JPanel();
     actionButtonPanel.setLayout(new BoxLayout(actionButtonPanel, BoxLayout.Y_AXIS)); // Buttons untereinander
     actionButtonPanel.add(btnRefresh);
     actionButtonPanel.add(Box.createVerticalStrut(5)); // Abstand zwischen Buttons
     actionButtonPanel.add(exportExcelButton);
     // Füge leeren Rand hinzu für Ästhetik
     actionButtonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
     bottomArea.add(actionButtonPanel, BorderLayout.EAST); // Buttons rechts

     configDetailPanel.add(bottomArea, BorderLayout.SOUTH); // Füge kombiniertes unteres Panel hinzu

     // === Tabs hinzufügen ===
     tabbedPane.addTab("Abrechnungen verarbeiten", null, abrechnungenPanel, "Platzhalter für zukünftige Abrechnungsfunktion");
     tabbedPane.addTab("Konfiguration & Detailansicht", null, configDetailPanel, "Parameter einstellen und extrahierte Tabellen ansehen");

     // === Gesamtlayout ===
     setLayout(new BorderLayout());
     setJMenuBar(menuBar); // Menüleiste setzen
     add(tabbedPane, BorderLayout.CENTER); // TabbedPane als Hauptkomponente
     // StatusPanel für Fortschrittsbalken
     JPanel statusPanel = new JPanel(new BorderLayout());
     statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
     statusPanel.add(progressBar, BorderLayout.EAST); // Fortschritt rechts
     add(statusPanel, BorderLayout.SOUTH); // Unten platzieren
 }

 // --- Methoden für den Controller (Listener registrieren) ---
 // Die add... Methoden ermöglichen dem Controller, seine Handler zu registrieren
 public void addLadeButtonAbrechnungListener(ActionListener listener) { ladePdfButtonAbrechnung.addActionListener(listener); }
 public void addLadeButtonDetailsListener(ActionListener listener) { ladePdfButtonDetails.addActionListener(listener); }
 public void addExportButtonListener(ActionListener listener) { exportExcelButton.addActionListener(listener); }
 public void addRefreshButtonListener(ActionListener listener) { btnRefresh.addActionListener(listener); }
 // CRUD Panel Listener
 public void addCrudListSelectionListener(ListSelectionListener listener) { invoiceTypeCrudPanel.addListSelectionListener(listener); }
 public void addCrudNewButtonListener(ActionListener listener) { invoiceTypeCrudPanel.addNewButtonListener(listener); }
 public void addCrudSaveButtonListener(ActionListener listener) { invoiceTypeCrudPanel.addSaveButtonListener(listener); }
 public void addCrudDeleteButtonListener(ActionListener listener) { invoiceTypeCrudPanel.addDeleteButtonListener(listener); }
 public void addCrudEditCsvButtonListener(ActionListener listener) { invoiceTypeCrudPanel.addEditCsvButtonListener(listener); }
 // Andere Listener
 public void addPdfComboBoxListener(ActionListener listener) { pdfComboBox.addActionListener(listener); }
 public void addTabelleComboBoxListener(ActionListener listener) { tabelleComboBox.addActionListener(listener); }
 public void addFlavorComboBoxListener(ActionListener listener) { flavorComboBox.addActionListener(listener); }
 public void addRowToleranceSpinnerListener(ChangeListener listener) { rowToleranceSpinner.addChangeListener(listener); }
 public void addConfigMenuOpenListener(ActionListener listener) { if(openConfigEditorMenuItem!=null) openConfigEditorMenuItem.addActionListener(listener); }
 public void addConfigSelectionListener(ItemListener listener) { configComboBox.addItemListener(listener); }


 // --- Getter für Komponenten ---
 // Werden vom Controller benötigt, um Werte zu lesen oder Zustand zu prüfen/setzen
 public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
 public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
 public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
 public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }
 public JComboBox<Object> getConfigComboBox() { return configComboBox; }
 public InvoiceTypeCrudPanel getInvoiceTypeCrudPanel() { return invoiceTypeCrudPanel; } // Wichtig für Controller


 // --- Methoden zur Aktualisierung der UI-Komponenten ---

 /** Aktualisiert die Bereichs-Konfigurations-ComboBox. */
 public void updateConfigurationComboBox(List<ExtractionConfiguration> availableConfigs, ExtractionConfiguration activeConfig) {
     log.debug("MainFrame.updateConfigurationComboBox aufgerufen...");
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
     else if (hasEl) { PdfDokument first = docs.get(0); pdfComboBox.setSelectedItem(first); selSet = true; log.info("--> Erstes PDF '{}' gesetzt.", first.getSourcePdf()); if(!Objects.equals(model.getAusgewaehltesDokument(), first)) { log.info("---> Rufe setAusgewaehltesDokument auf..."); model.setAusgewaehltesDokument(first); } else { log.debug("---> Modell aktuell. Updates triggern."); updateTabelleComboBox(); updateDatenTabelle(); /* Invoice Panel durch Controller */}}
     if (!selSet) { log.debug("--> Keine PDF Auswahl."); if(model.getAusgewaehltesDokument()!=null) model.setAusgewaehltesDokument(null); updateTabelleComboBox(); updateDatenTabelle(); invoiceTypeCrudPanel.displayConfig(null); setRefreshButtonEnabled(false);} // CRUD Panel auch leeren
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
         if (!data.isEmpty()) { Vector<String> h=new Vector<>(data.get(0));Vector<Vector<Object>> dv=new Vector<>(); for(int i=1;i<data.size();i++)dv.add(new Vector<>(data.get(i))); log.info("---> Setze Daten: {} Zeilen, {} Spalten", dv.size(), h.size()); tabellenModell.setDataVector(dv,h); logMessage("Zeige: "+model.getAusgewaehlteTabelle()); SwingUtilities.invokeLater(()->{if(datenTabelle.getColumnCount()>0){TabellenSpaltenAnpasser tca=new TabellenSpaltenAnpasser(datenTabelle); tca.adjustColumns(); TableColumnModel cm=datenTabelle.getColumnModel(); for(int i=0;i<cm.getColumnCount();i++){TableColumn c=cm.getColumn(i); int w=c.getPreferredWidth(); c.setPreferredWidth(w*2);}}});}
         else { log.warn("--> Tabellendaten leer."); tabellenModell.setDataVector(new Vector<>(), new Vector<>()); logMessage("Tabelle ist leer: "+model.getAusgewaehlteTabelle()); }
     } else { log.warn("--> Keine Tabellendaten vom Modell."); tabellenModell.setDataVector(new Vector<>(), new Vector<>()); String status; if(model.getAusgewaehltesDokument()!=null&&model.getAusgewaehlteTabelle()!=null)status="Keine Daten verfügbar."; else if(model.getAusgewaehltesDokument()!=null)status="Keine Tabelle ausgewählt."; else status="Kein PDF ausgewählt."; logMessage(status);}
 }

 /** Fügt eine Nachricht zum Log-Textbereich hinzu. */
 public void logMessage(String nachricht) {
     SwingUtilities.invokeLater(() -> {
         if (logTextArea != null) { String ts=LocalDateTime.now().format(LOG_TIME_FORMATTER); logTextArea.append(ts + " - " + nachricht + "\n"); logTextArea.setCaretPosition(logTextArea.getDocument().getLength()); }
         else { log.warn("LogTextArea ist null: '{}'", nachricht); }
     });
 }

 /** Setzt die Sichtbarkeit des Fortschrittsbalkens. */
 public void setProgressBarVisible(boolean visible) { SwingUtilities.invokeLater(() -> progressBar.setVisible(visible)); }

 /** Setzt den Wert des Fortschrittsbalkens. */
 public void setProgressBarValue(int value) { SwingUtilities.invokeLater(() -> { progressBar.setValue(value); progressBar.setString(value + "%"); }); }

 /** Aktualisiert die Felder im CRUD-Panel (wird vom Controller aufgerufen). */
 public void updateInvoiceTypeDisplay(InvoiceTypeConfig config) {
     // Delegiere an das CRUD Panel
     if (invoiceTypeCrudPanel != null) {
          invoiceTypeCrudPanel.displayConfig(config);
     } else {
          log.error("invoiceTypeCrudPanel ist null in updateInvoiceTypeDisplay!");
     }
 }

 /** Setzt den Enabled-Status des Refresh-Buttons. */
 public void setRefreshButtonEnabled(boolean enabled) { SwingUtilities.invokeLater(() -> btnRefresh.setEnabled(enabled)); }

 // setUpdateCsvButtonEnabled wird nicht mehr direkt benötigt, da das CRUD-Panel seinen Button selbst verwaltet.

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
                  // Wenn Liste leer, auch CRUD Panel leeren/deaktivieren
                  if (model.getDokumente().isEmpty()) {
                      invoiceTypeCrudPanel.displayConfig(null);
                      invoiceTypeCrudPanel.setFormEnabled(false);
                      setRefreshButtonEnabled(false);
                  }
                  break;
              case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                  log.info("-> propertyChange: SELECTED_DOCUMENT auf '{}'", evt.getNewValue());
                  updateTabelleComboBox();
                  setRefreshButtonEnabled(model.getAusgewaehltesDokument() != null);
                  // Invoice Panel Update wird vom Controller angestoßen
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
                       // Aktualisiere CRUD Panel mit den Daten aus dem neu verarbeiteten Dokument
                       invoiceTypeCrudPanel.displayConfig(reprocessedDoc.getDetectedInvoiceType());
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
      if (tabelleComboBox != null && !Objects.equals(tabelleComboBox.getSelectedItem(), modelSelection)) {
           log.debug("--> Synchronisiere Tabellen ComboBox mit Modell: {}", modelSelection);
           ActionListener[] listeners = tabelleComboBox.getActionListeners(); for (ActionListener l : listeners) tabelleComboBox.removeActionListener(l);
           tabelleComboBox.setSelectedItem(modelSelection);
           for (ActionListener l : listeners) tabelleComboBox.addActionListener(l);
      }
 }
}