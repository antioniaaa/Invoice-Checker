package de.anton.invoice.cecker.invoice_checker.model;

import java.util.Objects;

/**
 * Repräsentiert eine Zeile aus der invoice-config.csv.
 */
public class InvoiceTypeConfig {
    private String keyword;
    private String keywordAlternate;
    private String type;
    private String areaType; // "Konfig" oder "Konfig*" (für Default/Fallback?)
    private String defaultFlavor;
    private String defaultRowTol; // Als String speichern, Konvertierung später

    // Getters (Setter sind optional, da wir primär lesen)
    public String getKeyword() { return keyword; }
    public String getKeywordAlternate() { return keywordAlternate; }
    public String getType() { return type; }
    public String getAreaType() { return areaType; }
    public String getDefaultFlavor() { return defaultFlavor; }
    public String getDefaultRowTol() { return defaultRowTol; }

    // Konstruktor zum einfachen Erstellen
    public InvoiceTypeConfig(String keyword, String keywordAlternate, String type, String areaType, String defaultFlavor, String defaultRowTol) {
        this.keyword = keyword != null ? keyword.trim() : "";
        this.keywordAlternate = keywordAlternate != null ? keywordAlternate.trim() : "";
        this.type = type != null ? type.trim() : "";
        this.areaType = areaType != null ? areaType.trim() : "";
        this.defaultFlavor = defaultFlavor != null ? defaultFlavor.trim() : "lattice"; // Default setzen
        this.defaultRowTol = defaultRowTol != null ? defaultRowTol.trim() : "2"; // Default setzen
    }

 // --- NEUE Setter ---
    public void setKeyword(String keyword) { this.keyword = keyword != null ? keyword.trim() : ""; }
    public void setKeywordAlternate(String keywordAlternate) { this.keywordAlternate = keywordAlternate != null ? keywordAlternate.trim() : ""; }
    public void setType(String type) { this.type = type != null ? type.trim() : ""; }
    public void setAreaType(String areaType) { this.areaType = areaType != null ? areaType.trim() : ""; }
    public void setDefaultFlavor(String defaultFlavor) { this.defaultFlavor = (defaultFlavor != null && !defaultFlavor.isBlank()) ? defaultFlavor.trim() : "lattice"; }
    public void setDefaultRowTol(String defaultRowTol) { this.defaultRowTol = (defaultRowTol != null && !defaultRowTol.isBlank()) ? defaultRowTol.trim() : "2"; }
    
    // toString für Debugging
    @Override
    public String toString() {
        return "InvoiceTypeConfig{" +
               "keyword='" + keyword + '\'' +
               ", type='" + type + '\'' +
               ", areaType='" + areaType + '\'' +
               ", flavor='" + defaultFlavor + '\'' +
               ", rowTol='" + defaultRowTol + '\'' +
               '}';
    }

    // equals/hashCode basierend auf Keyword (als primärer Identifikator)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvoiceTypeConfig that = (InvoiceTypeConfig) o;
        return Objects.equals(keyword, that.keyword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyword);
    }
}
