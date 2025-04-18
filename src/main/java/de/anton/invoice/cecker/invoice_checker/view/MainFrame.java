package de.anton.invoice.cecker.invoice_checker.view;

import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.model.ExtractionConfiguration;
import de.anton.invoice.cecker.invoice_checker.model.ExtrahierteTabelle;
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
 // Konfiguration Elemente
 private JMenuBar menuBar;
 private JMenuItem openConfigEditorMenuItem; // Referenz für Listener-Registrierung
 private JComboBox<Object> configComboBox; // Kann String("Keine") oder ExtractionConfiguration enthalten


 /**
  * Konstruktor für das Hauptfenster.
  * @param model Das Anwendungsmodell.
  */
 public MainFrame(AnwendungsModell model) {
     this.model = model;
     this.model.addPropertyChangeListener(this); // Auf Modelländerungen lauschen

     setTitle("PDF Tabellen Extraktor");
     setSize(1200, 750); // Breite erhöht für Parameter/Konfig
     setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Schließen im Listener behandeln
     setLocationRelativeTo(null); // Fenster zentrieren

     initKomponenten(); // GUI-Elemente erstellen
     layoutKomponenten(); // GUI-Elemente anordnen

     // Listener zum sauberen Behandeln des Fenster-Schließens
     addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
             log.info("Fenster-Schließen-Ereignis erkannt.");
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

     // Auswahl ComboBoxen
     pdfComboBox = new JComboBox<>();
     tabelleComboBox = new JComboBox<>();
     tabelleComboBox.setEnabled(false); // Initial deaktiviert

     // Parameter Komponenten initialisieren
     flavorComboBox = new JComboBox<>(new String[]{"lattice", "stream"});
     flavorComboBox.setSelectedItem("lattice"); // Default
     SpinnerNumberModel spinnerModel = new SpinnerNumberModel(2, 0, 100, 1); // Default 2, Min 0, Max 100, Step 1
     rowToleranceSpinner = new JSpinner(spinnerModel);
     rowToleranceLabel = new JLabel("Row Tol (Stream):");

     // Konfigurations-ComboBox
     configComboBox = new JComboBox<>(); // Wird später befüllt
     configComboBox.setPreferredSize(new Dimension(150, configComboBox.getPreferredSize().height)); // Breite begrenzen
     configComboBox.setToolTipText("Aktive Extraktionskonfiguration auswählen");

     // Tabelle initialisieren
     tabellenModell = new DefaultTableModel();
     datenTabelle = new JTable(tabellenModell);
     datenTabelle.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Horizontales Scrollen erlauben

     // Menüleiste
     menuBar = new JMenuBar();
     JMenu configMenu = new JMenu("Konfiguration");
     menuBar.add(configMenu);
     openConfigEditorMenuItem = new JMenuItem("Bereichsdefinition öffnen/bearbeiten...");
     configMenu.add(openConfigEditorMenuItem);
     // Listener für Menüpunkt wird vom Controller hinzugefügt

     // Statusleiste
     statusLabel = new JLabel("Bereit. Laden Sie PDFs, um zu starten.");
     statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
 }

 /**
  * Ordnet die initialisierten Komponenten im Fenster an (Layout).
  */
 private void layoutKomponenten() {
     // Top Panel für die Hauptsteuerlemente (Buttons, Auswahl, Parameter, Konfig)
     JPanel topPanel = new JPanel();
     topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS)); // Horizontale Anordnung
     topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // Außenabstand

     // Linke Steuerlemente (Laden, Export, PDF, Tabelle)
     topPanel.add(ladePdfButton);
     topPanel.add(Box.createHorizontalStrut(10)); // Abstand
     topPanel.add(exportExcelButton);
     topPanel.add(Box.createHorizontalStrut(20)); // Größerer Abstand
     topPanel.add(new JLabel("PDF:"));
     topPanel.add(pdfComboBox);
     topPanel.add(Box.createHorizontalStrut(10));
     topPanel.add(new JLabel("Tabelle:"));
     topPanel.add(tabelleComboBox);
     topPanel.add(Box.createHorizontalStrut(10));

     // Konfigurationsauswahl
     topPanel.add(new JLabel("Konfig:"));
     topPanel.add(configComboBox);

     // Dehnbarer Platzhalter, schiebt Parameter nach rechts
     topPanel.add(Box.createHorizontalGlue());

     // Rechtes Panel für die Parameter
     JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); // Links ausgerichtet, wenig vertikaler Abstand
     parameterPanel.setBorder(BorderFactory.createTitledBorder("Parameter"));
     parameterPanel.add(new JLabel("Flavor:"));
     parameterPanel.add(flavorComboBox);
     parameterPanel.add(Box.createHorizontalStrut(10));
     parameterPanel.add(rowToleranceLabel);
     rowToleranceSpinner.setPreferredSize(new Dimension(60, rowToleranceSpinner.getPreferredSize().height)); // Breite für Spinner
     parameterPanel.add(rowToleranceSpinner);
     topPanel.add(parameterPanel); // Füge Parameter-Panel zum Top-Panel hinzu

     // Hauptbereich für die Tabelle mit Scrollbalken
     JScrollPane tableScrollPane = new JScrollPane(datenTabelle);

     // Gesamtlayout des Frames
     setLayout(new BorderLayout());
     setJMenuBar(menuBar); // Menüleiste setzen
     add(topPanel, BorderLayout.NORTH); // Obere Leiste
     add(tableScrollPane, BorderLayout.CENTER); // Tabelle in der Mitte
     add(statusLabel, BorderLayout.SOUTH); // Statusleiste unten
 }

 // --- Methoden für den Controller (um Listener zu registrieren) ---
 public void addLadeButtonListener(ActionListener listener) { ladePdfButton.addActionListener(listener); }
 public void addExportButtonListener(ActionListener listener) { exportExcelButton.addActionListener(listener); }
 public void addPdfComboBoxListener(ActionListener listener) { pdfComboBox.addActionListener(listener); }
 public void addTabelleComboBoxListener(ActionListener listener) { tabelleComboBox.addActionListener(listener); }
 public void addFlavorComboBoxListener(ActionListener listener) { flavorComboBox.addActionListener(listener); }
 public void addRowToleranceSpinnerListener(ChangeListener listener) { rowToleranceSpinner.addChangeListener(listener); }
 // Listener für Konfiguration
 public void addConfigMenuOpenListener(ActionListener listener) {
     if (openConfigEditorMenuItem != null) {
         // Füge Listener zum Menüpunkt hinzu (im Controller aufgerufen)
         openConfigEditorMenuItem.addActionListener(listener);
     } else {
         log.error("Menüpunkt zum Öffnen des Konfig-Editors wurde nicht korrekt initialisiert!");
     }
 }
 public void addConfigSelectionListener(ItemListener listener) { // ItemListener für JComboBox
     configComboBox.addItemListener(listener);
 }


 // --- Getter für Komponenten (damit Controller darauf zugreifen kann) ---
 public JComboBox<String> getFlavorComboBox() { return flavorComboBox; }
 public JSpinner getRowToleranceSpinner() { return rowToleranceSpinner; }
 public JComboBox<PdfDokument> getPdfComboBox() { return pdfComboBox; }
 public JComboBox<ExtrahierteTabelle> getTabelleComboBox() { return tabelleComboBox; }
 public JComboBox<Object> getConfigComboBox() { return configComboBox; } // Gibt Konfig-ComboBox zurück


 // --- Methoden zur Aktualisierung der UI-Komponenten (werden vom PropertyChangeListener aufgerufen) ---

 /**
  * Aktualisiert die PDF-ComboBox mit der Liste der geladenen Dokumente aus dem Modell.
  * Wählt automatisch das erste Element aus, wenn die Liste nicht leer ist und
  * informiert das Modell darüber (falls nötig), um die abhängigen Updates anzustoßen.
  */
 private void updatePdfComboBox() {
     log.info("MainFrame.updatePdfComboBox START"); // Markiere Start
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
         // Wenn das vorher ausgewählte Element noch existiert, wähle es wieder aus
         log.trace("--> Versuche, vorherige PDF Auswahl wiederherzustellen: {}", selectedPdf);
         pdfComboBox.setSelectedItem(selectedPdf);
         log.debug("--> PDF Auswahl wiederhergestellt: {}", selectedPdf);
         selectionSet = true;
         // Keine Modellaktualisierung nötig, da Auswahl schon korrekt war
     } else if (hatPdfElemente) {
         // Wenn es Elemente gibt, aber die alte Auswahl weg ist (oder es keine gab), wähle das erste
         PdfDokument erstesElement = dokumente.get(0);
         log.trace("--> Versuche, erstes PDF Element auszuwählen: {}", erstesElement.getSourcePdf());
         pdfComboBox.setSelectedItem(erstesElement); // Setze Auswahl in der ComboBox
         selectionSet = true; // Markiere, dass eine Auswahl getroffen wurde
         log.info("--> Erstes PDF '{}' als ausgewählt in ComboBox gesetzt.", erstesElement.getSourcePdf());

         // *** Informiere das Modell über diese Auswahl, FALLS sie von der im Modell abweicht ***
         if (!Objects.equals(model.getAusgewaehltesDokument(), erstesElement)) {
              log.info("---> Rufe model.setAusgewaehltesDokument auf, da Modellauswahl abweicht (Modell hat: {}).", model.getAusgewaehltesDokument());
              model.setAusgewaehltesDokument(erstesElement); // Dies sollte die Events für selectedDocument und selectedTable auslösen
         } else {
             log.debug("---> Modell hatte bereits das korrekte erste Element ausgewählt. Kein setAusgewaehltesDokument nötig.");
             // Wenn das Modell schon korrekt war, die abhängigen GUIs aber nicht aktuell sind, manuell triggern.
             log.debug("---> Triggere Updates für Tabellen-ComboBox und Daten-Tabelle manuell (Sicherheitsmaßnahme).");
             updateTabelleComboBox(); // Stellt sicher, dass die Tabellenliste stimmt
             updateDatenTabelle();   // Stellt sicher, dass die Tabelle angezeigt wird
         }
     }

     // Wenn keine Auswahl gesetzt wurde (weil Liste leer), stelle sicher, dass Modellauswahl auch null ist
     if (!selectionSet && model.getAusgewaehltesDokument() != null) {
          log.info("--> Keine PDFs mehr in ComboBox, lösche Auswahl im Modell.");
          model.setAusgewaehltesDokument(null); // Löst Event aus -> Updates werden folgen
     } else if (!selectionSet) {
          log.debug("--> ComboBox ist leer, keine Auswahl gesetzt.");
          // Wenn die Box leer ist, explizit auch die Tabellen leeren
          updateTabelleComboBox(); // Wird leer sein
          updateDatenTabelle(); // Wird leer sein
     }

     // Listener wieder hinzufügen
     log.trace("--> Füge PDF ComboBox Listener wieder hinzu...");
     for (ActionListener l : pdfListeners) pdfComboBox.addActionListener(l);
     log.info("MainFrame.updatePdfComboBox ENDE"); // Markiere Ende
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

     PdfDokument currentPdf = model.getAusgewaehltesDokument();
     if (currentPdf != null) {
         List<ExtrahierteTabelle> tabellen = model.getVerfuegbareTabellen(); // Hole verfügbare Tabellen
         log.debug("--> Fülle Tabellen ComboBox mit {} Tabellen für PDF '{}'.", tabellen.size(), currentPdf.getSourcePdf());
         if (!tabellen.isEmpty()) {
             for (ExtrahierteTabelle tabelle : tabellen) {
                 tabelleComboBox.addItem(tabelle);
             }
             tabelleComboBox.setEnabled(true); // Aktiviere ComboBox

             // Setze die Auswahl basierend auf dem Modellzustand oder dem vorherigen Zustand
             ExtrahierteTabelle modelSelectedTable = model.getAusgewaehlteTabelle();
             boolean selectionSet = false;
             if (selectedTable instanceof ExtrahierteTabelle && tabellen.contains(selectedTable)) {
                  tabelleComboBox.setSelectedItem(selectedTable);
                  log.debug("--> Tabellen-Auswahl wiederhergestellt: {}", selectedTable);
                  selectionSet = true;
                  if (!Objects.equals(modelSelectedTable, selectedTable)) { model.setAusgewaehlteTabelle((ExtrahierteTabelle)selectedTable); }
             } else if (modelSelectedTable != null && tabellen.contains(modelSelectedTable)) {
                  tabelleComboBox.setSelectedItem(modelSelectedTable);
                  log.debug("--> Setze Tabellen-ComboBox Auswahl auf Tabelle aus Modell: {}", modelSelectedTable);
                  selectionSet = true;
             } else if (!tabellen.isEmpty()){
                  ExtrahierteTabelle erstesElement = tabellen.get(0);
                  tabelleComboBox.setSelectedItem(erstesElement);
                  log.warn("--> Setze erstes Element '{}' in Tabellen-ComboBox als Fallback.", erstesElement);
                  if (!Objects.equals(model.getAusgewaehlteTabelle(), erstesElement)) { model.setAusgewaehlteTabelle(erstesElement); }
                  selectionSet = true;
             }

              if (!selectionSet && model.getAusgewaehlteTabelle() != null) { model.setAusgewaehlteTabelle(null); }

         } else {
              log.debug("--> Keine Tabellen für das ausgewählte PDF gefunden.");
              if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null);
         }
     } else {
         log.debug("--> Kein PDF ausgewählt, Tabellen ComboBox bleibt leer/deaktiviert.");
          if (model.getAusgewaehlteTabelle() != null) model.setAusgewaehlteTabelle(null);
     }

      // Listener wieder hinzufügen
     for(ActionListener l : tableListeners) tabelleComboBox.addActionListener(l);
     log.debug("Tabellen ComboBox Update abgeschlossen.");
 }

 /**
  * Aktualisiert die JTable (datenTabelle) mit den Daten der aktuell im Modell ausgewählten Tabelle.
  * Beinhaltet auch die automatische Anpassung und Verdopplung der Spaltenbreiten.
  */
 private void updateDatenTabelle() {
     log.info("MainFrame.updateDatenTabelle wird aufgerufen für ausgewählte Tabelle: {}", model.getAusgewaehlteTabelle());
     Optional<List<List<String>>> tabellenDatenOpt = model.getAusgewaehlteTabellenDaten(); // Holt Daten der ausgewählten Tabelle

     if (tabellenDatenOpt.isPresent()) {
         List<List<String>> tabellenDaten = tabellenDatenOpt.get();
         log.debug("--> Tabellendaten erhalten ({} Zeilen)", tabellenDaten.size());
         // Zeige Tabelle auch an, wenn sie nur aus einem Header besteht
         if (!tabellenDaten.isEmpty()) {
             Vector<String> headers = new Vector<>(tabellenDaten.get(0));
             Vector<Vector<Object>> datenVektor = new Vector<>();
             // Füge Datenzeilen hinzu (beginnend ab Index 1)
             for (int i = 1; i < tabellenDaten.size(); i++) {
                 datenVektor.add(new Vector<>(tabellenDaten.get(i)));
             }
             log.info("---> Setze Daten für Tabelle: {} Datenzeilen, {} Spalten (Header: {})",
                      datenVektor.size(), headers.size(), headers);
             // Aktualisiere das TableModel -> JTable wird neu gezeichnet
             tabellenModell.setDataVector(datenVektor, headers);
             setStatus("Zeige Tabelle: " + model.getAusgewaehlteTabelle()); // Verwende toString der Tabelle für Status

             // Spaltenbreiten anpassen und verdoppeln (im EDT sicherstellen)
             SwingUtilities.invokeLater(() -> { // Verzögere leicht, um Layout zu ermöglichen
                 if (datenTabelle.getColumnCount() > 0) { // Nur ausführen, wenn Spalten existieren
                     TabellenSpaltenAnpasser tca = new TabellenSpaltenAnpasser(datenTabelle);
                     tca.adjustColumns();
                     TableColumnModel columnModel = datenTabelle.getColumnModel();
                     for (int i = 0; i < columnModel.getColumnCount(); i++) {
                         TableColumn column = columnModel.getColumn(i);
                         int aktuelleBreite = column.getPreferredWidth();
                         int neueBreite = aktuelleBreite * 2; // Verdoppeln
                         column.setPreferredWidth(neueBreite); // Setze neue bevorzugte Breite
                     }
                     log.debug("---> Alle Spaltenbreiten nach Verdopplung angepasst.");
                 } else {
                      log.debug("---> Keine Spalten zum Anpassen vorhanden.");
                 }
             });

         } else {
              // Daten sind komplett leer
              log.warn("--> Tabellendaten sind komplett leer. Leere Tabelle.");
              tabellenModell.setDataVector(new Vector<>(), new Vector<>()); // Explizit leeren
              setStatus("Ausgewählte Tabelle hat keine Daten oder Header: " + model.getAusgewaehlteTabelle());
         }
     } else {
         // Keine Tabellendaten vom Modell erhalten (Optional war leer)
         log.warn("--> Keine Tabellendaten vom Modell erhalten (Optional ist leer). Leere Tabelle.");
         tabellenModell.setDataVector(new Vector<>(), new Vector<>()); // Explizit leeren
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
  * Aktualisiert die Konfigurations-ComboBox mit den übergebenen Konfigurationen
  * und wählt die aktuell im Modell aktive Konfiguration aus.
  * Wird vom AppController aufgerufen.
  *
  * @param availableConfigs Eine Liste der verfügbaren ExtractionConfiguration-Objekte.
  * @param activeConfig Die aktuell im Modell aktive Konfiguration (kann null sein).
  */
 public void updateConfigurationComboBox(List<ExtractionConfiguration> availableConfigs, ExtractionConfiguration activeConfig) {
     log.debug("MainFrame.updateConfigurationComboBox wird aufgerufen mit {} Konfigs. Aktiv: {}",
               (availableConfigs != null ? availableConfigs.size() : 0), // Prüfe auf null
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
                  // Die Liste der Dokumente wurde geändert
                  log.info("-> propertyChange: Aktualisiere PDF ComboBox wegen '{}'.", propertyName);
                  updatePdfComboBox(); // Aktualisiert die PDF-Auswahl
                  break;
              case AnwendungsModell.SELECTED_DOCUMENT_PROPERTY:
                  // Das im Modell ausgewählte PDF-Dokument hat sich geändert
                  log.info("-> propertyChange: Aktualisiere Tabellen ComboBox wegen '{}'.", propertyName);
                  updateTabelleComboBox(); // Aktualisiert die Liste der verfügbaren Tabellen
                  // Die DatenTabelle wird durch das nachfolgende SELECTED_TABLE_PROPERTY Event aktualisiert
                  break;
              case AnwendungsModell.SELECTED_TABLE_PROPERTY:
                  // Die im Modell ausgewählte Tabelle hat sich geändert
                  log.info("-> propertyChange: Aktualisiere Daten Tabelle wegen '{}'.", propertyName);
                  updateDatenTabelle(); // Zeichnet die Haupttabelle mit den neuen Daten neu
                  // Stelle sicher, dass die Tabellen-ComboBox synchronisiert ist
                  synchronizeTableComboBoxSelection();
                  break;
              case AnwendungsModell.ACTIVE_CONFIG_PROPERTY:
                  // Die im Modell aktive Konfiguration wurde geändert
                  log.info("-> propertyChange: Aktive Konfiguration geändert auf '{}'. Aktualisiere ComboBox-Auswahl.", evt.getNewValue());
                  // Rufe die Methode auf, die die ComboBox mit den aktuellen Daten neu befüllt und die Auswahl setzt
                  updateConfigurationComboBox(model.getConfigurationService().loadAllConfigurations(), model.getAktiveKonfiguration());
                  break;
              default:
                  // Ignoriere andere Events
                  log.debug("-> propertyChange: Ignoriere Event '{}'", propertyName);
                  break;
         }
     });
 }

 /**
  * Stellt sicher, dass die Auswahl in der Tabellen-ComboBox mit der
  * Auswahl im Modell übereinstimmt. Wird nach SELECTED_TABLE_PROPERTY aufgerufen.
  */
 private void synchronizeTableComboBoxSelection() {
      ExtrahierteTabelle modelSelection = model.getAusgewaehlteTabelle();
      // Prüfe, ob die Auswahl in der GUI von der im Modell abweicht
      if (!Objects.equals(tabelleComboBox.getSelectedItem(), modelSelection)) {
           log.debug("--> Synchronisiere Tabellen ComboBox mit Modell-Auswahl: {}", modelSelection);
           // Listener kurz entfernen, um Endlosschleife durch setItem zu vermeiden
           ActionListener[] listeners = tabelleComboBox.getActionListeners();
           for (ActionListener l : listeners) tabelleComboBox.removeActionListener(l);
           // Setze die Auswahl in der GUI entsprechend dem Modell
           tabelleComboBox.setSelectedItem(modelSelection);
           // Füge die Listener wieder hinzu
           for (ActionListener l : listeners) tabelleComboBox.addActionListener(l);
      }
 }
}