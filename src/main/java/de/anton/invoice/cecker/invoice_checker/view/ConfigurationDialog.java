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
 * Beinhaltet den PdfAreaSelectorPanel. Funktionierende Basisversion.
 */
public class ConfigurationDialog extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationDialog.class);

    private final ConfigurationService configService;
    private ExtractionConfiguration currentConfiguration; // Die Konfiguration, die bearbeitet wird
    private PDDocument loadedPdfDoc; // Das im Dialog für die Vorschau geladene PDF

    // GUI Komponenten
    private PdfAreaSelectorPanel pdfPanel;
    private JList<AreaDefinition> areaList;
    private DefaultListModel<AreaDefinition> areaListModel;
    private JButton btnLoadPdf;
    private JButton btnPrevPage;
    private JButton btnNextPage;
    private JLabel lblPageInfo;
    private JButton btnRemoveArea;
    private JButton btnSaveConfig;
    private JTextField txtConfigName;
    private JRadioButton radioPageSpecific;
    private JRadioButton radioGlobal;
    private JLabel lblInfo; // Info-Label

    // Wird gesetzt, wenn der Benutzer speichert
    private ExtractionConfiguration savedConfiguration = null;

    public ConfigurationDialog(Frame owner, ConfigurationService configService, ExtractionConfiguration initialConfig) {
        super(owner, "Extraktionskonfiguration bearbeiten", true); // Modaler Dialog
        this.configService = configService;

        // Erstelle eine Kopie oder eine neue Konfiguration zum Bearbeiten
        if (initialConfig != null) {
             // TODO: Implementiere eine echte Klon-Methode in ExtractionConfiguration
             // Einfaches Klonen über Speichern/Laden (Workaround):
             try {
                configService.saveConfiguration(initialConfig); // Speichere kurz
                this.currentConfiguration = configService.loadConfiguration(initialConfig.getName()); // Lade Kopie
                if (this.currentConfiguration == null) { // Fallback, falls Laden fehlschlägt
                     this.currentConfiguration = new ExtractionConfiguration("Kopie von " + initialConfig.getName());
                }
             } catch(Exception e) {
                  log.error("Fehler beim Klonen der Konfiguration, erstelle neue.", e);
                  this.currentConfiguration = new ExtractionConfiguration("Neue Konfiguration");
             }
        } else {
             this.currentConfiguration = new ExtractionConfiguration("Neue Konfiguration");
        }


        initComponents();
        layoutComponents();
        addListeners();

        // Initialzustand setzen
        txtConfigName.setText(currentConfiguration.getName());
        updateModeSelection(); // Setzt Radiobuttons und Enabled-Status
        updatePageDisplay(); // Setzt Seiteninfo initial

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE); // Schließt nur Dialog, nicht Anwendung
        setSize(1000, 800);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        pdfPanel = new PdfAreaSelectorPanel();
        // Lausche auf Änderungen der Bereiche im Panel
        pdfPanel.addPropertyChangeListener("areasChanged", evt -> updateAreaList());

        areaListModel = new DefaultListModel<>();
        areaList = new JList<>(areaListModel);
        areaList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Listener für Auswahl in der Liste (um Löschen-Button zu aktivieren)
        areaList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btnRemoveArea.setEnabled(areaList.getSelectedIndex() != -1);
            }
        });

        btnLoadPdf = new JButton("Vorschau-PDF laden...");
        btnPrevPage = new JButton("< Vorherige");
        btnNextPage = new JButton("Nächste >");
        lblPageInfo = new JLabel("Seite: - / -");
        btnRemoveArea = new JButton("Markierten Bereich entfernen");
        btnRemoveArea.setEnabled(false); // Initial deaktiviert
        btnSaveConfig = new JButton("Konfiguration speichern & Schließen");
        txtConfigName = new JTextField(this.currentConfiguration.getName(), 25); // Name vorbelegen

        radioPageSpecific = new JRadioButton("Seitenspezifische Bereiche definieren");
        radioGlobal = new JRadioButton("Globale Bereiche definieren (gelten für alle Seiten)");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(radioPageSpecific);
        modeGroup.add(radioGlobal);

        lblInfo = new JLabel("<html>Zeichnen Sie Rechtecke auf die PDF-Seite, um Bereiche zu definieren.<br>Ausgewählte Bereiche können rechts entfernt werden.</html>");
        lblInfo.setForeground(Color.DARK_GRAY);
    }

    private void layoutComponents() {
        // Hauptpanel mit BorderLayout (gut für Center/East Aufteilung)
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10)); // 10px Abstand
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10)); // Außenabstand

        // PDF-Anzeigebereich im Scrollpane (nimmt den meisten Platz ein)
        JScrollPane scrollPanePdf = new JScrollPane(pdfPanel);
        mainPanel.add(scrollPanePdf, BorderLayout.CENTER); // Wichtig: CENTER

        // Rechtes Panel für alle Steuerelemente
        // Verwende BoxLayout für vertikale Anordnung untereinander
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setPreferredSize(new Dimension(300, 600)); // Breite festlegen, Höhe flexibel

        // --- Bereich 1: Konfigurationsname und Modus ---
        JPanel configPanel = new JPanel(new GridBagLayout()); // GridBag für flexible Anordnung
        configPanel.setBorder(BorderFactory.createTitledBorder("Konfiguration"));
        GridBagConstraints gbcConf = new GridBagConstraints();
        gbcConf.gridx = 0; gbcConf.gridy = 0; gbcConf.anchor = GridBagConstraints.WEST; gbcConf.insets = new Insets(2,5,2,5);
        configPanel.add(new JLabel("Name:"), gbcConf);

        gbcConf.gridx = 1; gbcConf.weightx = 1.0; gbcConf.fill = GridBagConstraints.HORIZONTAL;
        configPanel.add(txtConfigName, gbcConf);

        // Modus Radiobuttons untereinander
        gbcConf.gridx = 0; gbcConf.gridy = 1; gbcConf.gridwidth = 2; gbcConf.weightx = 0; gbcConf.fill = GridBagConstraints.NONE; // Reset fill
        configPanel.add(radioPageSpecific, gbcConf);

        gbcConf.gridy = 2;
        configPanel.add(radioGlobal, gbcConf);

        // Setze maximale Höhe, damit dieser Bereich nicht unnötig wächst
        configPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, configPanel.getPreferredSize().height));
        rightPanel.add(configPanel); // Füge KonfigPanel zum rechten Hauptpanel hinzu
        rightPanel.add(Box.createVerticalStrut(10)); // Abstand nach unten

        // --- Bereich 2: PDF Steuerung ---
        JPanel pdfControlPanel = new JPanel(new GridBagLayout());
        pdfControlPanel.setBorder(BorderFactory.createTitledBorder("Vorschau & Navigation"));
        GridBagConstraints gbcPdf = new GridBagConstraints();

        gbcPdf.gridx = 0; gbcPdf.gridy = 0; gbcPdf.gridwidth = 3; gbcPdf.anchor = GridBagConstraints.CENTER; gbcPdf.insets = new Insets(5,5,5,5);
        pdfControlPanel.add(btnLoadPdf, gbcPdf); // Ladebutton zentriert oben

        gbcPdf.gridy = 1; gbcPdf.gridwidth = 1; gbcPdf.weightx = 0.1; gbcPdf.fill = GridBagConstraints.HORIZONTAL; gbcPdf.anchor = GridBagConstraints.LINE_START; // Links
        pdfControlPanel.add(btnPrevPage, gbcPdf);

        gbcPdf.gridx = 1; gbcPdf.weightx = 0.8; gbcPdf.fill = GridBagConstraints.NONE; gbcPdf.anchor = GridBagConstraints.CENTER; // Mitte
        pdfControlPanel.add(lblPageInfo, gbcPdf);

        gbcPdf.gridx = 2; gbcPdf.weightx = 0.1; gbcPdf.fill = GridBagConstraints.HORIZONTAL; gbcPdf.anchor = GridBagConstraints.LINE_END; // Rechts
        pdfControlPanel.add(btnNextPage, gbcPdf);

        pdfControlPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, pdfControlPanel.getPreferredSize().height)); // Max Höhe begrenzen
        rightPanel.add(pdfControlPanel); // Füge PDFControlPanel zum rechten Hauptpanel hinzu
        rightPanel.add(Box.createVerticalStrut(10)); // Abstand nach unten


         // --- Bereich 3: Bereichsdefinition ---
        JPanel areaPanel = new JPanel(new BorderLayout(5, 5)); // BorderLayout für Liste und Button
        areaPanel.setBorder(BorderFactory.createTitledBorder("Bereiche definieren (Aktuelle Seite)"));

        // Info Label oben
        areaPanel.add(lblInfo, BorderLayout.NORTH);

        // Liste der Bereiche in der Mitte (mit ScrollPane)
        JScrollPane areaScrollPane = new JScrollPane(areaList);
        // Gib dem ScrollPane eine bevorzugte Höhe, damit es nicht kollabiert
        areaScrollPane.setPreferredSize(new Dimension(200, 200)); // Höhe anpassen nach Bedarf
        areaPanel.add(areaScrollPane, BorderLayout.CENTER); // Wichtig: CENTER

        // Button zum Entfernen unten
        areaPanel.add(btnRemoveArea, BorderLayout.SOUTH); // Wichtig: SOUTH

        rightPanel.add(areaPanel); // Füge AreaPanel zum rechten Hauptpanel hinzu

        // --- Füller und Speicherbutton ---
        // Fügt flexiblen Leerraum hinzu, schiebt den Speicherbutton nach unten
        rightPanel.add(Box.createVerticalGlue());

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT)); // Button rechtsbündig
        bottomPanel.add(btnSaveConfig);
         // Verhindere, dass das Bottom-Panel vertikal wächst
        bottomPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, bottomPanel.getPreferredSize().height));
        rightPanel.add(bottomPanel); // Füge Speicherbutton-Panel zum rechten Hauptpanel hinzu


        // Füge das rechte Panel zum Hauptpanel hinzu
        mainPanel.add(rightPanel, BorderLayout.EAST); // Wichtig: EAST

        // Setze das Hauptpanel als Content Pane des Dialogs
        setContentPane(mainPanel);
    }

    // Registriert Listener für die GUI-Elemente
    private void addListeners() {
        btnLoadPdf.addActionListener(e -> loadPdfAction());
        btnPrevPage.addActionListener(e -> changePage(-1));
        btnNextPage.addActionListener(e -> changePage(1));
        btnRemoveArea.addActionListener(e -> removeSelectedArea());
        btnSaveConfig.addActionListener(e -> saveConfigurationAction());

        // Modus-Änderung Listener
        ItemListener modeListener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateMode();
            }
        };
        radioPageSpecific.addItemListener(modeListener);
        radioGlobal.addItemListener(modeListener);

        // Schließe PDF-Ressourcen, wenn der Dialog geschlossen wird
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pdfPanel.closeDocument();
            }
        });
    }

    // --- Aktionen der Buttons und Steuerelemente ---

    private void loadPdfAction() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF Dokumente", "pdf"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            try {
                pdfPanel.closeDocument(); // Wichtig: Altes Dokument schließen
                log.info("Lade Vorschau-PDF: {}", selectedFile.getAbsolutePath());
                // Lade PDF *nur* für die Anzeige im Dialog
                loadedPdfDoc = PDDocument.load(selectedFile);
                pdfPanel.loadDocument(loadedPdfDoc); // Panel informieren
                loadAreasForCurrentPage(); // Bereiche für die erste Seite laden
                updatePageDisplay(); // Seitenanzeige aktualisieren
            } catch (IOException ex) {
                log.error("Fehler beim Laden des Vorschau-PDFs: {}", selectedFile.getAbsolutePath(), ex);
                JOptionPane.showMessageDialog(this, "Fehler beim Laden des PDFs:\n" + ex.getMessage(), "Ladefehler", JOptionPane.ERROR_MESSAGE);
                pdfPanel.closeDocument();
                loadedPdfDoc = null;
                updatePageDisplay();
            }
        }
    }

    private void changePage(int delta) {
        if (loadedPdfDoc == null) return;
        int currentPageIdx = pdfPanel.getCurrentPageNumber();
        int newPageIdx = currentPageIdx + delta;

        // Prüfe Gültigkeit des neuen Index
        if (newPageIdx >= 0 && newPageIdx < loadedPdfDoc.getNumberOfPages()) {
            // Speichere Bereiche der *alten* Seite, bevor gewechselt wird
            saveAreasForCurrentPage(currentPageIdx); // Übergebe alten Index
            // Setze neue Seite im Panel
            pdfPanel.setPage(newPageIdx);
            // Lade Bereiche für die *neue* Seite
            loadAreasForCurrentPage(); // Lädt für die jetzt aktuelle Seite im Panel
            updatePageDisplay(); // Aktualisiere "Seite x / y" Anzeige
        }
    }

    // Speichert die im Panel definierten Bereiche für die GEGEBENE Seitenzahl (0-basiert)
    private void saveAreasForCurrentPage(int pageIndexToSave) {
         int pageNumOneBased = pageIndexToSave + 1; // 1-basiert für Konfig-Map
         if (pageNumOneBased > 0 && currentConfiguration != null) {
              List<AreaDefinition> areasFromPanel = pdfPanel.getAreas(); // Hole aktuelle Bereiche vom Panel
              if (currentConfiguration.isUsePageSpecificAreas()) {
                  currentConfiguration.setPageSpecificAreas(pageNumOneBased, areasFromPanel);
                  log.debug("Seitenspezifische Bereiche für Seite {} gespeichert (Anzahl: {}).", pageNumOneBased, areasFromPanel.size());
              } else {
                   // Bei globalem Modus werden aktuelle Panel-Bereiche als die globalen gesetzt
                   currentConfiguration.setGlobalAreas(areasFromPanel);
                   log.debug("Globale Bereiche gespeichert (bearbeitet auf Seite {}, Anzahl: {}).", pageNumOneBased, areasFromPanel.size());
              }
         }
    }

     // Lädt die Bereiche für die aktuell im Panel angezeigte Seite aus der Konfiguration
    private void loadAreasForCurrentPage() {
        int currentPageIdx = pdfPanel.getCurrentPageNumber(); // Aktueller 0-basierter Index
        int pageNumOneBased = currentPageIdx + 1;
        List<AreaDefinition> areasToShow = new ArrayList<>();

        if (pageNumOneBased > 0 && currentConfiguration != null) {
             if (currentConfiguration.isUsePageSpecificAreas()) {
                 // Lade seitenspezifische Bereiche
                 areasToShow = currentConfiguration.getPageSpecificAreas(pageNumOneBased);
                 log.debug("Lade {} seitenspezifische Bereiche für Seite {}.", areasToShow.size(), pageNumOneBased);
             } else {
                 // Lade globale Bereiche (da globaler Modus aktiv ist)
                 areasToShow = currentConfiguration.getGlobalAreas();
                 log.debug("Lade {} globale Bereiche (Anzeige auf Seite {}).", areasToShow.size(), pageNumOneBased);
             }
        }
        pdfPanel.setAreas(areasToShow); // Setze Bereiche im Panel (aktualisiert auch Anzeige)
        updateAreaList(); // Aktualisiere die JList rechts
    }


    // Entfernt den in der JList ausgewählten Bereich
    private void removeSelectedArea() {
        AreaDefinition selectedArea = areaList.getSelectedValue();
        if (selectedArea != null && pdfPanel != null) {
            log.debug("Entferne Bereich: {}", selectedArea);
            // Temporäre Liste der Bereiche vom Panel holen
            List<AreaDefinition> currentAreas = pdfPanel.getAreas();
            // Ausgewählten Bereich entfernen
            boolean removed = currentAreas.remove(selectedArea);
            if (removed) {
                // Aktualisierte Liste wieder im Panel setzen (löst repaint aus)
                pdfPanel.setAreas(currentAreas);
                // Änderung direkt auch in der Konfiguration speichern
                saveAreasForCurrentPage(pdfPanel.getCurrentPageNumber());
                // JList neu aufbauen (updateAreaList wird durch setAreas im Panel getriggert)
                 // updateAreaList(); // Wird jetzt durch PropertyChangeListener vom Panel getriggert
            } else {
                 log.warn("Konnte ausgewählten Bereich nicht aus Panel-Liste entfernen.");
            }
        }
    }

    // Speichert die aktuelle Konfiguration und schließt den Dialog
    private void saveConfigurationAction() {
        String configName = txtConfigName.getText().trim();
        if (configName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte geben Sie einen Namen für die Konfiguration ein.", "Speichern fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // Speichere Bereiche der aktuell angezeigten Seite
        saveAreasForCurrentPage(pdfPanel.getCurrentPageNumber());

        // Aktualisiere Konfigurationsobjekt
        currentConfiguration.setName(configName);
        currentConfiguration.setUsePageSpecificAreas(radioPageSpecific.isSelected());

        try {
            // Speichere über den Service
            configService.saveConfiguration(currentConfiguration);
            this.savedConfiguration = currentConfiguration; // Merken für Aufrufer
            JOptionPane.showMessageDialog(this, "Konfiguration '" + configName + "' erfolgreich gespeichert.", "Gespeichert", JOptionPane.INFORMATION_MESSAGE);
            pdfPanel.closeDocument(); // PDF schließen
            dispose(); // Dialog schließen
        } catch (IOException e) {
            log.error("Fehler beim Speichern der Konfiguration '{}'", configName, e);
            JOptionPane.showMessageDialog(this, "Fehler beim Speichern der Konfiguration:\n" + e.getMessage(), "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        } catch(IllegalArgumentException e) {
             JOptionPane.showMessageDialog(this, "Fehler: " + e.getMessage(), "Speichern fehlgeschlagen", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Aktualisiert die JList mit den Bereichen vom PdfAreaSelectorPanel
    private void updateAreaList() {
        areaListModel.clear();
        if (pdfPanel != null) {
            for (AreaDefinition area : pdfPanel.getAreas()) {
                areaListModel.addElement(area);
            }
        }
        // Stelle sicher, dass Löschen-Button nur aktiv ist, wenn etwas ausgewählt werden KANN
        btnRemoveArea.setEnabled(areaList.getSelectedIndex() != -1);
    }

     // Aktualisiert die Seitenanzeige und Button-Status
    private void updatePageDisplay() {
        int currentPage = pdfPanel.getCurrentPageNumber(); // 0-basiert
        int totalPages = pdfPanel.getTotalPages();

        if (currentPage >= 0 && totalPages > 0) {
            lblPageInfo.setText(String.format("Seite: %d / %d", currentPage + 1, totalPages));
            btnPrevPage.setEnabled(currentPage > 0);
            btnNextPage.setEnabled(currentPage < totalPages - 1);
        } else {
            lblPageInfo.setText("Seite: - / -");
            btnPrevPage.setEnabled(false);
            btnNextPage.setEnabled(false);
        }
        // Aktivierungsstatus des Panels/der Liste basierend auf Modus setzen
        updateModeSelection();
    }

    // Aktualisiert Radiobuttons und Aktivierungsstatus basierend auf Konfig-Objekt
    private void updateModeSelection() {
         boolean isPageSpecific = currentConfiguration.isUsePageSpecificAreas();
         radioPageSpecific.setSelected(isPageSpecific);
         radioGlobal.setSelected(!isPageSpecific);

         // Aktivieren/Deaktivieren basierend auf Modus (Panel ist immer aktiv, wenn PDF geladen)
         boolean pdfLoaded = (loadedPdfDoc != null);
         pdfPanel.setEnabled(pdfLoaded);
         areaList.setEnabled(pdfLoaded);
         btnRemoveArea.setEnabled(pdfLoaded && areaList.getSelectedIndex() != -1);
         // Ggf. spezifische GUI-Elemente für globalen Modus hier (de)aktivieren
         // ...
    }

    // Wird aufgerufen, wenn der Modus (Radiobutton) geändert wird
     private void updateMode() {
         boolean usePageSpecific = radioPageSpecific.isSelected();
         // Wenn Modus wechselt, müssen die Bereiche der aktuellen Seite evtl. neu geladen/gespeichert werden
         if (currentConfiguration.isUsePageSpecificAreas() != usePageSpecific) {
              log.info("Wechsle Konfigurationsmodus zu: {}", usePageSpecific ? "Seitenspezifisch" : "Global");
              // 1. Speichere Bereiche der aktuellen Seite unter dem ALTEN Modus
              saveAreasForCurrentPage(pdfPanel.getCurrentPageNumber());
              // 2. Setze den NEUEN Modus in der Konfiguration
              currentConfiguration.setUsePageSpecificAreas(usePageSpecific);
              // 3. Lade die Bereiche für den NEUEN Modus für die aktuelle Seite
              loadAreasForCurrentPage();
         }
         // Stelle sicher, dass die GUI-Elemente den korrekten Enabled-Status haben
         updateModeSelection();
    }

    /**
     * Gibt die Konfiguration zurück, die zuletzt gespeichert wurde.
     * Gibt null zurück, wenn der Dialog abgebrochen oder ohne Speichern geschlossen wurde.
     * @return Die gespeicherte ExtractionConfiguration oder null.
     */
    public ExtractionConfiguration getSavedConfiguration() {
        return savedConfiguration;
    }
}