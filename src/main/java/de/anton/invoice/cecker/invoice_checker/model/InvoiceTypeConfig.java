package de.anton.invoice.cecker.invoice_checker.model;

import java.util.Objects;

/**
 * Repräsentiert eine Zeile aus der erweiterten invoice-config.csv.
 */
public class InvoiceTypeConfig {
    // Konstanten für Default-Werte oder Marker
    public static final String USE_MANUAL_CONFIG = "Konfig*"; // Marker für manuelle Bereichs-Konfig
    public static final String USE_MANUAL_FLAVOR = "Flavor*"; // Marker für manuellen Flavor
    public static final String USE_MANUAL_ROW_TOL = "Row Tol*"; // Marker für manuelle Row Tol

    // Felder entsprechend CSV-Spalten
    private String type;
    private String keywordIncl1;
    private String keywordIncl2;
    private String keywordIncl3;
    private String keywordExcl1;
    private String keywordExcl2;
    private String areaType; // Name der Bereichs-Konfig oder Marker
    private String defaultFlavor;
    private String defaultRowTol;

    // --- Getter ---
    public String getType() { return type != null ? type : ""; }
    public String getKeywordIncl1() { return keywordIncl1 != null ? keywordIncl1 : ""; }
    public String getKeywordIncl2() { return keywordIncl2 != null ? keywordIncl2 : ""; }
    public String getKeywordIncl3() { return keywordIncl3 != null ? keywordIncl3 : ""; }
    public String getKeywordExcl1() { return keywordExcl1 != null ? keywordExcl1 : ""; }
    public String getKeywordExcl2() { return keywordExcl2 != null ? keywordExcl2 : ""; }
    public String getAreaType() { return areaType != null ? areaType : ""; }
    public String getDefaultFlavor() { return defaultFlavor != null ? defaultFlavor : "lattice"; }
    public String getDefaultRowTol() { return defaultRowTol != null ? defaultRowTol : "2"; }

    // --- Setter ---
    public void setType(String type) { this.type = type != null ? type.trim() : ""; }
    public void setKeywordIncl1(String keywordIncl1) { this.keywordIncl1 = keywordIncl1 != null ? keywordIncl1.trim() : ""; }
    public void setKeywordIncl2(String keywordIncl2) { this.keywordIncl2 = keywordIncl2 != null ? keywordIncl2.trim() : ""; }
    public void setKeywordIncl3(String keywordIncl3) { this.keywordIncl3 = keywordIncl3 != null ? keywordIncl3.trim() : ""; }
    public void setKeywordExcl1(String keywordExcl1) { this.keywordExcl1 = keywordExcl1 != null ? keywordExcl1.trim() : ""; }
    public void setKeywordExcl2(String keywordExcl2) { this.keywordExcl2 = keywordExcl2 != null ? keywordExcl2.trim() : ""; }
    public void setAreaType(String areaType) { this.areaType = areaType != null ? areaType.trim() : ""; }
    public void setDefaultFlavor(String defaultFlavor) { this.defaultFlavor = (defaultFlavor != null && !defaultFlavor.isBlank()) ? defaultFlavor.trim() : "lattice"; }
    public void setDefaultRowTol(String defaultRowTol) { this.defaultRowTol = (defaultRowTol != null && !defaultRowTol.isBlank()) ? defaultRowTol.trim() : "2"; }

    /**
     * Konstruktor für die erweiterte Struktur.
     */
    public InvoiceTypeConfig(String type, String keywordIncl1, String keywordIncl2, String keywordIncl3,
                             String keywordExcl1, String keywordExcl2, String areaType,
                             String defaultFlavor, String defaultRowTol) {
        setType(type);
        setKeywordIncl1(keywordIncl1);
        setKeywordIncl2(keywordIncl2);
        setKeywordIncl3(keywordIncl3);
        setKeywordExcl1(keywordExcl1);
        setKeywordExcl2(keywordExcl2);
        setAreaType(areaType);
        setDefaultFlavor(defaultFlavor);
        setDefaultRowTol(defaultRowTol);
    }

    /** Gibt das primäre Keyword zurück (für Identifikation). */
    public String getIdentifyingKeyword() {
        return getKeywordIncl1(); // Annahme: Erstes Include-Keyword ist der primäre Identifier
    }

    @Override
    public String toString() {
        return "InvoiceTypeConfig{keyword='" + getIdentifyingKeyword() + "', type='" + type + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceTypeConfig that = (InvoiceTypeConfig) o;
        // Gleichheit basiert auf dem primären Keyword
        return Objects.equals(getIdentifyingKeyword(), that.getIdentifyingKeyword());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIdentifyingKeyword());
    }
}