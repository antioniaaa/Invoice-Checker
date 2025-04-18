package de.anton.invoice.cecker.invoice_checker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects; // Für equals/hashCode

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtrahierteTabelle {
    private int index;
    private int page;
    private double accuracy;
    private double whitespace;
    private String flavor;
    private List<List<String>> data;

    // --- Getter und Setter ---
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
    public double getWhitespace() { return whitespace; }
    public void setWhitespace(double whitespace) { this.whitespace = whitespace; }
    public String getFlavor() { return flavor; }
    public void setFlavor(String flavor) { this.flavor = flavor; }
    public List<List<String>> getData() { return data; }
    public void setData(List<List<String>> data) { this.data = data; }

    // Verbesserte toString für die Anzeige in der ComboBox
    @Override
    public String toString() {
        return "Seite " + page + ", Index " + index + (flavor != null ? " (" + flavor + ")" : "") + " [" + (data != null && data.size() > 1 ? (data.size() -1) + " Zeilen" : "Keine Daten") + "]";
    }

    // Optional: equals und hashCode, falls man Tabellenobjekte direkt vergleichen will
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtrahierteTabelle that = (ExtrahierteTabelle) o;
        // Eindeutigkeit über Seite und Index innerhalb eines Dokuments
        return page == that.page && index == that.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(page, index);
    }
}