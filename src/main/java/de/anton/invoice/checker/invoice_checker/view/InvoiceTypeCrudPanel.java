package de.anton.invoice.checker.invoice_checker.view;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.anton.invoice.checker.invoice_checker.model.InvoiceTypeConfig;
import de.anton.invoice.checker.invoice_checker.model.InvoiceTypeService;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Comparator;
import java.util.List;

/**
 * Ein Panel zur Verwaltung (CRUD) der InvoiceTypeConfig-Einträge.
 * Ermöglicht das Anzeigen, Auswählen, Bearbeiten, Hinzufügen und Löschen von Typdefinitionen.
 */
public class InvoiceTypeCrudPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(InvoiceTypeCrudPanel.class);

    // Modell für die Liste
    private DefaultListModel<InvoiceTypeConfig> listModel;
    // GUI Komponenten
    private JList<InvoiceTypeConfig> configList;
    private JTextField txtType;
    private JTextField txtKeywordIncl1; // Primäres Keyword, nicht änderbar nach Erstellung?
    private JTextField txtKeywordIncl2;
    private JTextField txtKeywordIncl3;
    private JTextField txtKeywordExcl1;
    private JTextField txtKeywordExcl2;
    private JTextField txtAreaType;
    private JComboBox<String> comboFlavor; // ComboBox für Flavor Auswahl
    private JSpinner spinnerRowTol;        // Spinner für Row Tolerance
    private JButton btnNeu;
    private JButton btnSpeichern;
    private JButton btnLoeschen;
    private JButton btnCsvEdit; // Button zum externen Bearbeiten

    private boolean isNewEntryMode = false; // Flag, ob gerade ein neuer Eintrag erstellt wird
    
    public boolean isNewEntryMode() {
		return isNewEntryMode;
	}

	public InvoiceTypeCrudPanel() {
        initComponents();
        layoutComponents();
        setFormEnabled(false); // Initial Formular deaktivieren
    }

    private void initComponents() {
        listModel = new DefaultListModel<>();
        configList = new JList<>(listModel);
        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setToolTipText("Gespeicherte Rechnungstypen auswählen");

        txtType = new JTextField(15);
        txtKeywordIncl1 = new JTextField(15);
        txtKeywordIncl1.setToolTipText("Primäres Keyword (Regex) zur Identifizierung dieses Typs. Nicht änderbar.");
        txtKeywordIncl2 = new JTextField(15);
        txtKeywordIncl3 = new JTextField(15);
        txtKeywordExcl1 = new JTextField(15);
        txtKeywordExcl2 = new JTextField(15);
        txtAreaType = new JTextField(10);
        txtAreaType.setToolTipText("Name der Bereichs-Konfig (aus configs/area) oder 'Keine' oder 'Konfig*'.");
        comboFlavor = new JComboBox<>(new String[]{"lattice", "stream"});
        comboFlavor.setToolTipText("Standard-Extraktionsmethode für diesen Typ.");
        spinnerRowTol = new JSpinner(new SpinnerNumberModel(2, 0, 100, 1)); // Default 2, Min 0, Max 100
        spinnerRowTol.setToolTipText("Standard-Zeilentoleranz für 'stream'-Methode für diesen Typ.");

        btnNeu = new JButton("Neu");
        btnNeu.setToolTipText("Neuen Rechnungstyp hinzufügen.");
        btnSpeichern = new JButton("Speichern (CSV)");
        btnSpeichern.setToolTipText("Änderungen für den ausgewählten/neuen Typ in invoice-config.csv speichern.");
        btnLoeschen = new JButton("Löschen");
        btnLoeschen.setToolTipText("Löscht den ausgewählten Rechnungstyp aus der CSV.");
        btnLoeschen.setEnabled(false); // Aktivieren, wenn Eintrag ausgewählt
        btnCsvEdit = new JButton("CSV bearbeiten...");
        btnCsvEdit.setToolTipText("Öffnet die invoice-config.csv im Standardeditor.");
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 5));
        setBorder(BorderFactory.createTitledBorder("Rechnungstyp-Definitionen (invoice-config.csv)"));

        // --- Linke Seite: Liste der Konfigurationen ---
        JScrollPane listScrollPane = new JScrollPane(configList);
        listScrollPane.setPreferredSize(new Dimension(200, 150)); // Größe für Liste
        add(listScrollPane, BorderLayout.WEST);

        // --- Rechte Seite: Formular zum Bearbeiten ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Zeile 0: Typ + Keyword 1 (primär)
        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("Typ:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; formPanel.add(txtType, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; formPanel.add(new JLabel("Prim. Keyword (ID):"), gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; formPanel.add(txtKeywordIncl1, gbc);

        // Zeile 1: Keywords Include 2 + 3
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; formPanel.add(new JLabel("Keyword Inkl. 2:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; formPanel.add(txtKeywordIncl2, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; formPanel.add(new JLabel("Keyword Inkl. 3:"), gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; formPanel.add(txtKeywordIncl3, gbc);

        // Zeile 2: Keywords Exclude 1 + 2
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; formPanel.add(new JLabel("Keyword Exkl. 1:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; formPanel.add(txtKeywordExcl1, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; formPanel.add(new JLabel("Keyword Exkl. 2:"), gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; formPanel.add(txtKeywordExcl2, gbc);

        // Zeile 3: AreaType, Flavor, RowTol
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; formPanel.add(new JLabel("Bereichs-Konfig:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; formPanel.add(txtAreaType, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; formPanel.add(new JLabel("Def. Flavor:"), gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.5; // Letzte Spalte nimmt Rest
        JPanel paramPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); // Panel für Flavor+Tol
        paramPanel.add(comboFlavor);
        paramPanel.add(Box.createHorizontalStrut(5));
        paramPanel.add(new JLabel("Tol:"));
        spinnerRowTol.setPreferredSize(new Dimension(60, spinnerRowTol.getPreferredSize().height));
        paramPanel.add(spinnerRowTol);
        formPanel.add(paramPanel, gbc);

        add(formPanel, BorderLayout.CENTER);

        // --- Untere Leiste: Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(btnNeu);
        buttonPanel.add(btnSpeichern);
        buttonPanel.add(btnLoeschen);
        buttonPanel.add(Box.createHorizontalStrut(20)); // Abstand
        buttonPanel.add(btnCsvEdit);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    // --- Öffentliche Methoden zur Interaktion ---

    /** Aktualisiert die Liste der angezeigten Konfigurationen. */
    public void updateList(List<InvoiceTypeConfig> configs) {
        InvoiceTypeConfig selectedValue = configList.getSelectedValue(); // Auswahl merken
        listModel.clear();
        if (configs != null) {
            // Sortieren nach Typ, dann Keyword?
            configs.sort(Comparator.comparing(InvoiceTypeConfig::getType)
                                   .thenComparing(InvoiceTypeConfig::getIdentifyingKeyword));
            for(InvoiceTypeConfig cfg : configs) {
                // Füge nicht den "Others"-Eintrag hinzu (kann nicht direkt editiert werden)
                if (!InvoiceTypeService.DEFAULT_IDENTIFYING_KEYWORD.equalsIgnoreCase(cfg.getIdentifyingKeyword())) {
                    listModel.addElement(cfg);
                }
            }
        }
        // Versuche Auswahl wiederherzustellen
        if (selectedValue != null && listModel.contains(selectedValue)) {
            configList.setSelectedValue(selectedValue, true);
        } else {
            clearForm(); // Keine Auswahl -> Formular leeren
            setFormEnabled(false);
        }
        log.debug("InvoiceType Liste mit {} Einträgen aktualisiert.", listModel.getSize());
    }

    /** Füllt das Formular mit den Daten einer Konfiguration. */
    public void displayConfig(InvoiceTypeConfig config) {
        isNewEntryMode = false; // Bearbeitungsmodus
        if (config != null) {
            txtType.setText(config.getType());
            txtKeywordIncl1.setText(config.getKeywordIncl1());
            txtKeywordIncl1.setEditable(false); // Primärkey nicht änderbar machen
            txtKeywordIncl1.setBackground(UIManager.getColor("TextField.inactiveBackground"));
            txtKeywordIncl2.setText(config.getKeywordIncl2());
            txtKeywordIncl3.setText(config.getKeywordIncl3());
            txtKeywordExcl1.setText(config.getKeywordExcl1());
            txtKeywordExcl2.setText(config.getKeywordExcl2());
            txtAreaType.setText(config.getAreaType());
            comboFlavor.setSelectedItem(config.getDefaultFlavor());
            try {
                 spinnerRowTol.setValue(Integer.parseInt(config.getDefaultRowTol()));
            } catch (NumberFormatException e) {
                 spinnerRowTol.setValue(2); // Fallback
                 log.warn("Konnte RowTol '{}' nicht parsen, setze auf Default 2.", config.getDefaultRowTol());
            }
            setFormEnabled(true); // Formular aktivieren
            btnLoeschen.setEnabled(true); // Löschen erlauben
        } else {
            clearForm();
            setFormEnabled(false);
            btnLoeschen.setEnabled(false);
        }
    }

    /** Leert das Formular und setzt es für eine neue Eingabe zurück. */
    public void prepareNewEntry() {
        isNewEntryMode = true;
        configList.clearSelection(); // Auswahl in Liste aufheben
        clearForm();
        txtKeywordIncl1.setEditable(true); // Primärkey ist bei Neuerstellung editierbar
        txtKeywordIncl1.setBackground(UIManager.getColor("TextField.background"));
        txtType.requestFocus(); // Fokus auf erstes Feld
        setFormEnabled(true);
        btnLoeschen.setEnabled(false); // Löschen nicht möglich bei neuem Eintrag
    }

    /** Leert alle Formularfelder. */
    public void clearForm() {
        txtType.setText("");
        txtKeywordIncl1.setText("");
        txtKeywordIncl2.setText("");
        txtKeywordIncl3.setText("");
        txtKeywordExcl1.setText("");
        txtKeywordExcl2.setText("");
        txtAreaType.setText(InvoiceTypeConfig.USE_MANUAL_CONFIG); // Default
        comboFlavor.setSelectedItem("lattice");
        spinnerRowTol.setValue(2);
        txtKeywordIncl1.setEditable(false); // Standard: nicht editierbar
        txtKeywordIncl1.setBackground(UIManager.getColor("TextField.inactiveBackground"));
    }

    /** Aktiviert oder deaktiviert die Formularfelder. */
    public void setFormEnabled(boolean enabled) {
        // KeywordIncl1 ist nur im Neu-Modus editierbar
        txtKeywordIncl1.setEditable(enabled && isNewEntryMode);
        txtKeywordIncl1.setBackground(enabled && isNewEntryMode ? UIManager.getColor("TextField.background") : UIManager.getColor("TextField.inactiveBackground"));

        txtType.setEnabled(enabled); txtType.setEditable(enabled);
        txtKeywordIncl2.setEnabled(enabled); txtKeywordIncl2.setEditable(enabled);
        txtKeywordIncl3.setEnabled(enabled); txtKeywordIncl3.setEditable(enabled);
        txtKeywordExcl1.setEnabled(enabled); txtKeywordExcl1.setEditable(enabled);
        txtKeywordExcl2.setEnabled(enabled); txtKeywordExcl2.setEditable(enabled);
        txtAreaType.setEnabled(enabled); txtAreaType.setEditable(enabled);
        comboFlavor.setEnabled(enabled);
        spinnerRowTol.setEnabled(enabled);
        btnSpeichern.setEnabled(enabled); // Speichern nur möglich, wenn Formular aktiv
    }

    /** Liest die Werte aus dem Formular und erstellt ein neues InvoiceTypeConfig-Objekt. */
    public InvoiceTypeConfig getConfigFromForm() {
        // Validierung hinzufügen? Z.B. ob KeywordIncl1 gesetzt ist?
        if (txtKeywordIncl1.getText().isBlank()) {
             JOptionPane.showMessageDialog(this, "Das Feld 'Prim. Keyword (ID)' darf nicht leer sein.", "Eingabefehler", JOptionPane.WARNING_MESSAGE);
             return null; // Ungültige Eingabe
        }

        return new InvoiceTypeConfig(
                txtType.getText(),
                txtKeywordIncl1.getText(),
                txtKeywordIncl2.getText(),
                txtKeywordIncl3.getText(),
                txtKeywordExcl1.getText(),
                txtKeywordExcl2.getText(),
                txtAreaType.getText(),
                (String) comboFlavor.getSelectedItem(),
                spinnerRowTol.getValue().toString()
        );
    }

    /** Gibt den aktuell ausgewählten Eintrag aus der Liste zurück. */
    public InvoiceTypeConfig getSelectedConfig() {
        return configList.getSelectedValue();
    }

    /** Wählt einen Eintrag in der Liste basierend auf dem primären Keyword aus. */
    public void setSelectedConfig(String keyword) {
        if (keyword == null) {
             configList.clearSelection();
             return;
        }
        for (int i = 0; i < listModel.getSize(); i++) {
             if (keyword.equals(listModel.getElementAt(i).getIdentifyingKeyword())) {
                  configList.setSelectedIndex(i);
                  configList.ensureIndexIsVisible(i); // Hinzuscrollen
                  return;
             }
        }
         configList.clearSelection(); // Nicht gefunden
    }

    // --- Listener hinzufügen ---
    public void addListSelectionListener(ListSelectionListener listener) { configList.addListSelectionListener(listener); }
    public void addNewButtonListener(ActionListener listener) { btnNeu.addActionListener(listener); }
    public void addSaveButtonListener(ActionListener listener) { btnSpeichern.addActionListener(listener); }
    public void addDeleteButtonListener(ActionListener listener) { btnLoeschen.addActionListener(listener); }
    public void addEditCsvButtonListener(ActionListener listener) { btnCsvEdit.addActionListener(listener); }
}
