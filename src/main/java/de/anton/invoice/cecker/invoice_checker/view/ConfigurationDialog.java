package de.anton.invoice.cecker.invoice_checker.view;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.anton.invoice.cecker.invoice_checker.model.AreaDefinition;
import de.anton.invoice.cecker.invoice_checker.model.ConfigurationService;
import de.anton.invoice.cecker.invoice_checker.model.ExtractionConfiguration;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
// import java.util.stream.Collectors; // Nicht unbedingt hier benötigt

/**
 * Dialog zur Erstellung und Bearbeitung von Extraktionskonfigurationen.
 * Beinhaltet den PdfAreaSelectorPanel und Steuerelemente zur Bereichsverwaltung.
 * Ermöglicht das Speichern und Laden von Konfigurationen.
 */
public class ConfigurationDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationDialog.class);

    private final ConfigurationService configService; // Zum Speichern/Laden
    private ExtractionConfiguration currentConfiguration; // Die Konfiguration, die bearbeitet wird
    private PDDocument loadedPdfDoc; // Das im Dialog für die Vorschau geladene PDF

    // GUI Komponenten
    private PdfAreaSelectorPanel pdfPanel; // Panel für PDF-Anzeige und Bereichsauswahl
    private JList<AreaDefinition> areaList; // Zeigt definierte Bereiche für aktuelle Seite
    private DefaultListModel<AreaDefinition> areaListModel; // Datenmodell für die JList
    private JButton btnLoadPdf;      // Button zum Laden eines Vorschau-PDFs
    private JButton btnPrevPage;     // Button zur vorherigen Seite
    private JButton btnNextPage;     // Button zur nächsten Seite
    private JLabel lblPageInfo;      // Anzeige "Seite x / y"
    private JButton btnRemoveArea;   // Button zum Entfernen des ausgewählten Bereichs
    private JButton btnSaveConfig;   // Button zum Speichern der Konfiguration
    private JTextField txtConfigName;  // Textfeld für den Namen der Konfiguration
    private JRadioButton radioPageSpecific; // Auswahl: Seitenspezifische Bereiche
    private JRadioButton radioGlobal;       // Auswahl: Globale Bereiche
    private JLabel lblInfo;          // Info-Text für Benutzerführung
    private JButton btnZoomIn;       // Button zum Vergrößern
    private JButton btnZoomOut;      // Button zum Verkleinern

    // Merkt sich die Konfiguration, die gespeichert wurde, um sie an den Aufrufer zurückzugeben
    private ExtractionConfiguration savedConfiguration = null;

    /**
     * Konstruktor für den Konfigurationsdialog.
     * @param owner Das übergeordnete Fenster (Frame).
     * @param configService Der Service zum Laden/Speichern von Konfigurationen.
     * @param initialConfig Die Konfiguration, die bearbeitet werden soll (kann null sein für neue Konfig).
     */
    public ConfigurationDialog(Frame owner, ConfigurationService configService, ExtractionConfiguration initialConfig) {
        super(owner, "Extraktionskonfiguration bearbeiten", true); // Modaler Dialog
        this.configService = configService;

        // Erstelle eine Kopie der übergebenen Konfiguration oder eine neue,
        // damit das Original nicht direkt verändert wird.
        if (initialConfig != null) {
             // Einfaches Klonen über Speichern/Laden (Workaround, da keine Klon-Methode implementiert ist)
             try {
                // Speichere kurz unter temporärem Namen oder dem Originalnamen
                configService.saveConfiguration(initialConfig);
                // Lade die gerade gespeicherte Version als Kopie
                this.currentConfiguration = configService.loadConfiguration(initialConfig.getName());
                if (this.currentConfiguration == null) { // Fallback, falls Laden fehlschlägt
                     log.warn("Konnte Konfiguration '{}' nicht neu laden nach temporärem Speichern. Erstelle leere Kopie.", initialConfig.getName());
                     this.currentConfiguration = new ExtractionConfiguration("Kopie von " + initialConfig.getName());
                } else {
                     log.debug("Arbeitskopie von Konfiguration '{}' erstellt.", initialConfig.getName());
                }
             } catch(Exception e) {
                  log.error("Fehler beim Klonen der Konfiguration '{}', erstelle neue.", initialConfig.getName(), e);
                  this.currentConfiguration = new ExtractionConfiguration("Neue Konfiguration");
             }
        } else {
             // Keine initiale Konfiguration übergeben -> Neue erstellen
             this.currentConfiguration = new ExtractionConfiguration("Neue Konfiguration");
        }

        // Initialisiere alle GUI-Komponenten
        initComponents();
        // Ordne die Komponenten im Dialog an
        layoutComponents();
        // Registriere die Event-Listener
        addListeners();

        // Setze initiale Werte basierend auf der Konfiguration
        txtConfigName.setText(currentConfiguration.getName());
        updateModeSelection(); // Setzt Radiobuttons und Enabled-Status
        updatePageDisplay(); // Setzt Seiteninfo initial

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Schließt nur Dialog, nicht Anwendung
        // Setze eine Mindestgröße und erlaube Größenänderung
        setMinimumSize(new Dimension(900, 700));
        setSize(1100, 800); // Initiale Größe
        setLocationRelativeTo(owner); // Zentriere relativ zum Hauptfenster
    }

    /**
     * Initialisiert die Swing-Komponenten.
     */
    private void initComponents() {
        // PDF Panel
        pdfPanel = new PdfAreaSelectorPanel();
        // Lausche auf Änderungen der Bereiche (hinzugefügt/entfernt) im Panel
        pdfPanel.addPropertyChangeListener("areasChanged", evt -> updateAreaList());

        // Liste für Bereiche
        areaListModel = new DefaultListModel<>();
        areaList = new JList<>(areaListModel);
        areaList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // Nur ein Bereich kann ausgewählt werden
        // Listener für Auswahl in der Liste (um Löschen-Button zu aktivieren/deaktivieren)
        areaList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // Nur reagieren, wenn Auswahl abgeschlossen ist
                btnRemoveArea.setEnabled(areaList.getSelectedIndex() != -1);
            }
        });

        // Buttons
        btnLoadPdf = new JButton("Vorschau-PDF laden...");
        btnLoadPdf.setToolTipText("Lädt ein PDF zur Definition von Bereichen.");
        btnPrevPage = new JButton("< Vorherige");
        btnPrevPage.setEnabled(false); // Initial deaktiviert
        btnNextPage = new JButton("Nächste >");
        btnNextPage.setEnabled(false); // Initial deaktiviert
        btnZoomIn = new JButton("+");
        btnZoomIn.setToolTipText("Vergrößern");
        btnZoomIn.setEnabled(false);
        btnZoomOut = new JButton("-");
        btnZoomOut.setToolTipText("Verkleinern");
        btnZoomOut.setEnabled(false);
        btnRemoveArea = new JButton("Markierten Bereich entfernen");
        btnRemoveArea.setEnabled(false); // Aktiviert, wenn Bereich in Liste ausgewählt wird
        btnSaveConfig = new JButton("Speichern & Schließen");
        btnSaveConfig.setToolTipText("Speichert die aktuelle Konfiguration unter dem angegebenen Namen.");

        // Textfeld und Labels
        txtConfigName = new JTextField(this.currentConfiguration.getName(), 25); // Name vorbelegen
        lblPageInfo = new JLabel("Seite: - / -");
        lblPageInfo.setHorizontalAlignment(SwingConstants.CENTER);
        lblInfo = new JLabel("<html>Zeichnen Sie Rechtecke auf die PDF-Seite.<br>Ausgewählte Bereiche können rechts entfernt werden.</html>");
        lblInfo.setForeground(Color.DARK_GRAY);

        // Radio Buttons für Modus
        radioPageSpecific = new JRadioButton("Seitenspezifische Bereiche definieren");
        radioGlobal = new JRadioButton("Globale Bereiche definieren (gelten für alle Seiten)");
        ButtonGroup modeGroup = new ButtonGroup(); // Sorgt dafür, dass nur einer ausgewählt ist
        modeGroup.add(radioPageSpecific);
        modeGroup.add(radioGlobal);
    }

    /**
     * Ordnet die initialisierten Komponenten im Fenster an (Layout).
     * Verwendet BorderLayout für die Hauptstruktur und GridBagLayout für Details.
     */
    private void layoutComponents() {
        // Hauptpanel mit BorderLayout (10px Abstand horizontal/vertikal)
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        // Außenabstand hinzufügen
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // PDF-Anzeigebereich in der Mitte, eingebettet in ein Scrollpane
        JScrollPane scrollPanePdf = new JScrollPane(pdfPanel);
        mainPanel.add(scrollPanePdf, BorderLayout.CENTER); // Nimmt den meisten Platz ein

        // Rechtes Panel für alle Steuerelemente, verwendet GridBagLayout für flexible Anordnung
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0)); // Kleiner linker Rand zum PDF-Panel
        GridBagConstraints gbcRight = new GridBagConstraints();
        gbcRight.gridx = 0; // Alles in der ersten Spalte
        gbcRight.weightx = 1.0; // Erlaube horizontale Ausdehnung
        gbcRight.fill = GridBagConstraints.HORIZONTAL; // Fülle horizontal aus
        gbcRight.anchor = GridBagConstraints.NORTHWEST; // Beginne oben links
        gbcRight.insets = new Insets(0, 0, 10, 0); // Abstand nach unten zwischen den Komponentenblöcken

        // --- Block 1: Konfigurationsname und Modus ---
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Konfiguration"));
        GridBagConstraints gbcConf = new GridBagConstraints();
        gbcConf.insets = new Insets(2, 5, 2, 5); // Innenabstände
        gbcConf.anchor = GridBagConstraints.WEST;
        gbcConf.gridx = 0; gbcConf.gridy = 0; configPanel.add(new JLabel("Name:"), gbcConf);
        gbcConf.gridx = 1; gbcConf.weightx = 1.0; gbcConf.fill = GridBagConstraints.HORIZONTAL; configPanel.add(txtConfigName, gbcConf); // Textfeld dehnt sich aus
        gbcConf.gridx = 0; gbcConf.gridy = 1; gbcConf.gridwidth = 2; gbcConf.weightx = 0; gbcConf.fill = GridBagConstraints.NONE; configPanel.add(radioPageSpecific, gbcConf);
        gbcConf.gridy = 2; configPanel.add(radioGlobal, gbcConf);
        // Füge KonfigPanel zum rechten Panel hinzu
        gbcRight.gridy = 0; // Erste Zeile im rechten Panel
        gbcRight.weighty = 0; // Höhe nicht variabel
        rightPanel.add(configPanel, gbcRight);

        // --- Block 2: PDF Steuerung & Zoom ---
        JPanel pdfControlPanel = new JPanel(new GridBagLayout());
        pdfControlPanel.setBorder(BorderFactory.createTitledBorder("Vorschau & Navigation"));
        GridBagConstraints gbcPdf = new GridBagConstraints();
        gbcPdf.insets = new Insets(5, 5, 5, 5); // Innenabstände
        // Ladebutton über die ganze Breite
        gbcPdf.gridx = 0; gbcPdf.gridy = 0; gbcPdf.gridwidth = 5; gbcPdf.fill = GridBagConstraints.HORIZONTAL;
        pdfControlPanel.add(btnLoadPdf, gbcPdf);
        // Navigation und Zoom in der nächsten Zeile
        gbcPdf.gridy = 1; gbcPdf.gridwidth = 1; gbcPdf.fill = GridBagConstraints.NONE; // Buttons nicht füllen lassen
        gbcPdf.anchor = GridBagConstraints.LINE_START; gbcPdf.weightx = 0.1; // Wenig Platz für Buttons
        pdfControlPanel.add(btnPrevPage, gbcPdf);
        gbcPdf.gridx = 1; gbcPdf.anchor = GridBagConstraints.CENTER; gbcPdf.weightx = 0.7; // Viel Platz für Label
        pdfControlPanel.add(lblPageInfo, gbcPdf);
        gbcPdf.gridx = 2; gbcPdf.anchor = GridBagConstraints.LINE_END; gbcPdf.weightx = 0.1;
        pdfControlPanel.add(btnNextPage, gbcPdf);
        gbcPdf.gridx = 3; gbcPdf.anchor = GridBagConstraints.LINE_END; gbcPdf.weightx = 0.05; gbcPdf.insets = new Insets(5, 0, 5, 0); // Kein horizontaler Abstand
        pdfControlPanel.add(btnZoomOut, gbcPdf);
        gbcPdf.gridx = 4; gbcPdf.anchor = GridBagConstraints.LINE_END; gbcPdf.weightx = 0.05;
        pdfControlPanel.add(btnZoomIn, gbcPdf);
        // Füge PDFControlPanel zum rechten Panel hinzu
        gbcRight.gridy = 1; // Zweite Zeile im rechten Panel
        gbcRight.weighty = 0; // Höhe nicht variabel
        gbcRight.fill = GridBagConstraints.HORIZONTAL; // Fülle horizontal
        rightPanel.add(pdfControlPanel, gbcRight);


        // --- Block 3: Bereichsdefinition ---
        JPanel areaPanel = new JPanel(new BorderLayout(5, 5));
        areaPanel.setBorder(BorderFactory.createTitledBorder("Bereiche definieren (Aktuelle Seite)"));
        areaPanel.add(lblInfo, BorderLayout.NORTH); // Info oben
        JScrollPane areaScrollPane = new JScrollPane(areaList);
        areaScrollPane.setPreferredSize(new Dimension(200, 200)); // Bevorzugte Größe für die Liste
        areaPanel.add(areaScrollPane, BorderLayout.CENTER); // Liste in der Mitte
        areaPanel.add(btnRemoveArea, BorderLayout.SOUTH); // Button unten
        // Füge AreaPanel zum rechten Panel hinzu
        gbcRight.gridy = 2; // Dritte Zeile
        gbcRight.weighty = 1.0; // Dieser Bereich soll vertikal wachsen!
        gbcRight.fill = GridBagConstraints.BOTH; // Fülle horizontal und vertikal
        rightPanel.add(areaPanel, gbcRight);

        // --- Block 4: Speicherbutton ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); // Button rechtsbündig
        bottomPanel.add(btnSaveConfig);
        // Füge bottomPanel zum rechten Panel hinzu
        gbcRight.gridy = 3; // Vierte Zeile
        gbcRight.weighty = 0; // Höhe nicht variabel
        gbcRight.fill = GridBagConstraints.HORIZONTAL; // Nur horizontal füllen
        rightPanel.add(bottomPanel, gbcRight);


        // Füge das komplett aufgebaute rechte Panel zum Hauptpanel hinzu
        mainPanel.add(rightPanel, BorderLayout.EAST); // Im Osten platzieren

        // Setze das Hauptpanel als Content Pane des Dialogs
        setContentPane(mainPanel);
    }

    /**
     * Registriert die Action- und ChangeListener für die GUI-Komponenten.
     */
    private void addListeners() {
        // Button-Aktionen
        btnLoadPdf.addActionListener(e -> loadPdfAction());
        btnPrevPage.addActionListener(e -> changePage(-1));
        btnNextPage.addActionListener(e -> changePage(1));
        btnZoomIn.addActionListener(e -> pdfPanel.zoomIn());
        btnZoomOut.addActionListener(e -> pdfPanel.zoomOut());
        btnRemoveArea.addActionListener(e -> removeSelectedArea());
        btnSaveConfig.addActionListener(e -> saveConfigurationAction());

        // Modus-Änderung Listener (RadioButton)
        ItemListener modeListener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateMode(); // Rufe Methode zur Modus-Aktualisierung auf
            }
        };
        radioPageSpecific.addItemListener(modeListener);
        radioGlobal.addItemListener(modeListener);

        // Fenster-Listener zum Schließen des PDF-Dokuments beim Schließen des Dialogs
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log.debug("Schließe Konfigurationsdialog, schließe internes PDF.");
                pdfPanel.closeDocument(); // Gibt PDFBox-Ressourcen frei
            }
        });
    }

    // --- Aktionen der Buttons und Steuerelemente ---

    /** Lädt ein PDF zur Vorschau und Bereichsdefinition. */
    private void loadPdfAction() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
        chooser.setDialogTitle("Vorschau-PDF auswählen");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            try {
                pdfPanel.closeDocument(); // Wichtig: Altes Dokument schließen
                log.info("Lade Vorschau-PDF: {}", selectedFile.getAbsolutePath());
                loadedPdfDoc = PDDocument.load(selectedFile); // Lade PDF mit PDFBox
                pdfPanel.loadDocument(loadedPdfDoc); // Informiere das Panel
                loadAreasForCurrentPage(); // Lade direkt Bereiche für die erste Seite
                updatePageDisplay(); // Aktualisiere Seitenanzeige und Button-Status
            } catch (IOException ex) {
                log.error("Fehler beim Laden des Vorschau-PDFs: {}", selectedFile.getAbsolutePath(), ex);
                JOptionPane.showMessageDialog(this, "Fehler beim Laden des PDFs:\n" + ex.getMessage(), "Ladefehler", JOptionPane.ERROR_MESSAGE);
                pdfPanel.closeDocument(); // Sicherstellen, dass Panel zurückgesetzt wird
                loadedPdfDoc = null;
                updatePageDisplay(); // Anzeige zurücksetzen
            }
        }
    }

    /** Wechselt zur nächsten oder vorherigen Seite im Vorschau-PDF. */
    private void changePage(int delta) {
        if (loadedPdfDoc == null) return; // Kein PDF geladen
        int currentPageIdx = pdfPanel.getCurrentPageNumber(); // Aktueller 0-basierter Index
        int newPageIdx = currentPageIdx + delta; // Neuer Index

        // Prüfe Gültigkeit des neuen Index
        if (newPageIdx >= 0 && newPageIdx < loadedPdfDoc.getNumberOfPages()) {
            log.debug("Wechsle von Seite {} zu Seite {}", currentPageIdx + 1, newPageIdx + 1);
            // 1. Speichere Bereiche der *alten* Seite, bevor gewechselt wird
            saveAreasForCurrentPage(currentPageIdx);
            // 2. Setze neue Seite im Panel (rendert neu)
            pdfPanel.setPage(newPageIdx);
            // 3. Lade Bereiche für die *neue* Seite aus der Konfiguration
            loadAreasForCurrentPage();
            // 4. Aktualisiere "Seite x / y" Anzeige und Button-Status
            updatePageDisplay();
        }
    }

    /** Speichert die aktuell im Panel sichtbaren Bereiche in das Konfigurationsobjekt für die gegebene Seite. */
    private void saveAreasForCurrentPage(int pageIndexToSave) {
         // Nur speichern, wenn Index gültig ist (größer/gleich 0)
         if (pageIndexToSave < 0) return;

         int pageNumOneBased = pageIndexToSave + 1; // Konfiguration verwendet 1-basierte Seitenzahlen
         if (currentConfiguration != null) {
              List<AreaDefinition> areasFromPanel = pdfPanel.getAreas(); // Hole aktuelle Bereiche vom Panel
              if (currentConfiguration.isUsePageSpecificAreas()) {
                  // Speichere als seitenspezifische Bereiche
                  currentConfiguration.setPageSpecificAreas(pageNumOneBased, areasFromPanel);
                  log.debug("Seitenspezifische Bereiche für Seite {} gespeichert (Anzahl: {}).", pageNumOneBased, areasFromPanel.size());
              } else {
                   // Speichere als globale Bereiche (überschreibt vorherige globale)
                   currentConfiguration.setGlobalAreas(areasFromPanel);
                   log.debug("Globale Bereiche gespeichert (bearbeitet auf Seite {}, Anzahl: {}).", pageNumOneBased, areasFromPanel.size());
              }
         }
    }

     /** Lädt die Bereiche für die aktuell im Panel angezeigte Seite aus der Konfiguration und zeigt sie an. */
    private void loadAreasForCurrentPage() {
        int currentPageIdx = pdfPanel.getCurrentPageNumber(); // Aktueller 0-basierter Index
        int pageNumOneBased = currentPageIdx + 1;
        List<AreaDefinition> areasToShow = new ArrayList<>(); // Leere Liste als Default

        if (pageNumOneBased > 0 && currentConfiguration != null) {
             if (currentConfiguration.isUsePageSpecificAreas()) {
                 // Lade seitenspezifische Bereiche für diese Seite
                 areasToShow = currentConfiguration.getPageSpecificAreas(pageNumOneBased);
                 log.debug("Lade {} seitenspezifische Bereiche für Seite {}.", areasToShow.size(), pageNumOneBased);
             } else {
                 // Lade globale Bereiche (da globaler Modus aktiv ist)
                 areasToShow = currentConfiguration.getGlobalAreasList();
                 log.debug("Lade {} globale Bereiche (Anzeige auf Seite {}).", areasToShow.size(), pageNumOneBased);
             }
        }
        pdfPanel.setAreas(areasToShow); // Setze Bereiche im Panel (löst repaint und PropertyChange aus)
        // updateAreaList(); // Wird jetzt durch PropertyChangeListener vom Panel getriggert
    }


    /** Entfernt den in der JList ausgewählten Bereich aus dem Panel und der Konfiguration. */
    private void removeSelectedArea() {
        AreaDefinition selectedArea = areaList.getSelectedValue(); // Hole Auswahl aus JList
        if (selectedArea != null && pdfPanel != null) {
            log.debug("Entferne ausgewählten Bereich: {}", selectedArea);
            // Rufe Methode im Panel auf, um Bereich zu entfernen (löst repaint und Event aus)
            pdfPanel.removeArea(selectedArea);
            // Änderung direkt auch in der Konfiguration speichern (für die aktuelle Seite/Modus)
            saveAreasForCurrentPage(pdfPanel.getCurrentPageNumber());
            // JList wird durch den PropertyChangeListener aktualisiert
        }
    }

    /** Speichert die aktuelle Konfiguration (Name, Modus, Bereiche) und schließt den Dialog. */
    private void saveConfigurationAction() {
        String configName = txtConfigName.getText().trim();
        if (configName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte geben Sie einen Namen für die Konfiguration ein.", "Speichern fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
            txtConfigName.requestFocus();
            return;
        }
        // Speichere Bereiche der aktuell angezeigten Seite, bevor die Konfig gespeichert wird
        saveAreasForCurrentPage(pdfPanel.getCurrentPageNumber());

        // Aktualisiere das Konfigurationsobjekt mit den GUI-Einstellungen
        currentConfiguration.setName(configName);
        currentConfiguration.setUsePageSpecificAreas(radioPageSpecific.isSelected());

        try {
            // Speichere die Konfiguration über den Service
            configService.saveConfiguration(currentConfiguration);
            this.savedConfiguration = currentConfiguration; // Merken für den Aufrufer (AppController)
            JOptionPane.showMessageDialog(this, "Konfiguration '" + configName + "' erfolgreich gespeichert.", "Gespeichert", JOptionPane.INFORMATION_MESSAGE);
            pdfPanel.closeDocument(); // PDF schließen
            dispose(); // Dialog schließen
        } catch (IOException e) {
            log.error("Fehler beim Speichern der Konfiguration '{}'", configName, e);
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern der Konfiguration:\n" + e.getMessage(), "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        } catch(IllegalArgumentException e) {
             // Z.B. wenn Name leer war (sollte oben abgefangen werden)
             JOptionPane.showMessageDialog(this, "Fehler: " + e.getMessage(), "Speichern fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Aktualisiert die JList mit den Bereichen, die aktuell im PdfAreaSelectorPanel sind. */
    private void updateAreaList() {
        // Diese Methode wird jetzt durch den PropertyChangeListener aufgerufen, wenn
        // das Panel das "areasChanged" Event feuert.
        if (pdfPanel != null) {
             areaListModel.clear(); // Liste leeren
             List<AreaDefinition> currentAreas = pdfPanel.getAreas(); // Aktuelle Bereiche holen
             for (AreaDefinition area : currentAreas) {
                 areaListModel.addElement(area); // Zur Liste hinzufügen
             }
             // Status des Löschen-Buttons aktualisieren
             btnRemoveArea.setEnabled(areaList.getSelectedIndex() != -1);
        }
    }

     /** Aktualisiert die Seitenanzeige ("Seite x / y") und den Enabled-Status der Navigations- und Zoom-Buttons. */
    private void updatePageDisplay() {
        int currentPageIdx = pdfPanel.getCurrentPageNumber(); // 0-basiert
        int totalPages = pdfPanel.getTotalPages();

        boolean pdfLoaded = (currentPageIdx >= 0 && totalPages > 0);

        if (pdfLoaded) {
            lblPageInfo.setText(String.format("Seite %d / %d", currentPageIdx + 1, totalPages));
            btnPrevPage.setEnabled(currentPageIdx > 0); // Aktiv, wenn nicht erste Seite
            btnNextPage.setEnabled(currentPageIdx < totalPages - 1); // Aktiv, wenn nicht letzte Seite
        } else {
            lblPageInfo.setText("Seite: - / -");
            btnPrevPage.setEnabled(false);
            btnNextPage.setEnabled(false);
        }
        // Zoom-Buttons sind nur aktiv, wenn ein PDF geladen ist
        btnZoomIn.setEnabled(pdfLoaded);
        btnZoomOut.setEnabled(pdfLoaded);

        // Status der Modus-Auswahl (und abhängiger Elemente) auch aktualisieren
        updateModeSelection();
    }

    /** Aktualisiert den Status der Radiobuttons und die Aktivierung abhängiger GUI-Elemente basierend auf dem Konfigurationsmodus. */
    private void updateModeSelection() {
         boolean isPageSpecific = currentConfiguration.isUsePageSpecificAreas();
         // Setze den richtigen Radiobutton
         radioPageSpecific.setSelected(isPageSpecific);
         radioGlobal.setSelected(!isPageSpecific);

         // Aktiviere/Deaktiviere Elemente basierend darauf, ob ein PDF geladen ist
         boolean pdfLoaded = (loadedPdfDoc != null);
         pdfPanel.setEnabled(pdfLoaded); // Mausereignisse im Panel nur wenn PDF da
         areaList.setEnabled(pdfLoaded); // Liste nur aktiv, wenn PDF da
         btnRemoveArea.setEnabled(pdfLoaded && areaList.getSelectedIndex() != -1); // Löschen nur wenn PDF da UND was ausgewählt

         // TODO: Hier könnte man spezifische Steuerelemente für den globalen Modus (de)aktivieren, falls hinzugefügt
    }

    /** Wird aufgerufen, wenn der Benutzer den Modus (Seitenspezifisch/Global) ändert. */
     private void updateMode() {
         boolean usePageSpecific = radioPageSpecific.isSelected();
         // Nur handeln, wenn sich der Modus tatsächlich geändert hat
         if (currentConfiguration.isUsePageSpecificAreas() != usePageSpecific) {
              log.info("Wechsle Konfigurationsmodus zu: {}", usePageSpecific ? "Seitenspezifisch" : "Global");
              // 1. Speichere Bereiche der aktuellen Seite unter dem ALTEN Modus, falls ein PDF geladen ist
              if (loadedPdfDoc != null) {
                  saveAreasForCurrentPage(pdfPanel.getCurrentPageNumber());
              }
              // 2. Setze den NEUEN Modus in der Konfiguration
              currentConfiguration.setUsePageSpecificAreas(usePageSpecific);
              // 3. Lade die Bereiche für den NEUEN Modus für die aktuelle Seite (falls PDF geladen)
              if (loadedPdfDoc != null) {
                   loadAreasForCurrentPage();
              }
         }
         // Stelle sicher, dass der Enabled-Status der GUI-Elemente korrekt ist
         updateModeSelection();
    }

    /**
     * Gibt die Konfiguration zurück, die zuletzt erfolgreich gespeichert wurde.
     * Gibt null zurück, wenn der Dialog abgebrochen oder ohne Speichern geschlossen wurde.
     * @return Die gespeicherte ExtractionConfiguration oder null.
     */
    public ExtractionConfiguration getSavedConfiguration() {
        return savedConfiguration;
    }
}