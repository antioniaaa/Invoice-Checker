package de.anton.invoice.cecker.invoice_checker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfDokument implements Comparable<PdfDokument> {

    @JsonProperty("source_pdf")
    private String sourcePdf; // Dateiname

    @JsonProperty("full_path")
    private String fullPath; // Voller Pfad für die Verarbeitung

    @JsonProperty("billing_period_start")
    private String abrechnungszeitraumStartStr; // JJJJ-MM-TT aus Python

    @JsonProperty("billing_period_end")
    private String abrechnungszeitraumEndeStr; // JJJJ-MM-TT aus Python

    private List<ExtrahierteTabelle> tables = new ArrayList<>(); // Tabellen
    private String error; // Fehlermeldung aus Python-Skript speichern

    private transient LocalDate abrechnungszeitraumStart; // Geparsstes Datum
    private transient LocalDate abrechnungszeitraumEnde; // Geparsstes Datum

    // Getter und Setter
    public String getSourcePdf() { return sourcePdf; }
    public void setSourcePdf(String sourcePdf) { this.sourcePdf = sourcePdf; }
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }
    public String getAbrechnungszeitraumStartStr() { return abrechnungszeitraumStartStr; }
    public void setAbrechnungszeitraumStartStr(String abrechnungszeitraumStartStr) {
        this.abrechnungszeitraumStartStr = abrechnungszeitraumStartStr;
        parseDaten();
    }
    public String getAbrechnungszeitraumEndeStr() { return abrechnungszeitraumEndeStr; }
     public void setAbrechnungszeitraumEndeStr(String abrechnungszeitraumEndeStr) {
        this.abrechnungszeitraumEndeStr = abrechnungszeitraumEndeStr;
        parseDaten();
    }
    public List<ExtrahierteTabelle> getTables() { return tables; }
    public void setTables(List<ExtrahierteTabelle> tables) { this.tables = tables; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDate getAbrechnungszeitraumStart() { return abrechnungszeitraumStart; }
    public LocalDate getAbrechnungszeitraumEnde() { return abrechnungszeitraumEnde; }

    // Methode zum Parsen der Datumsstrings, nachdem sie gesetzt wurden (z.B. durch Jackson)
    private void parseDaten() {
        this.abrechnungszeitraumStart = versucheDatumParsen(this.abrechnungszeitraumStartStr);
        this.abrechnungszeitraumEnde = versucheDatumParsen(this.abrechnungszeitraumEndeStr);
    }

    private LocalDate versucheDatumParsen(String datumStr) {
        if (datumStr != null && !datumStr.isBlank()) {
            try {
                // Nimmt JJJJ-MM-TT Format an
                return LocalDate.parse(datumStr);
            } catch (DateTimeParseException e) {
                System.err.println("Konnte Datum nicht parsen: " + datumStr);
                return null;
            }
        }
        return null;
    }

    // Wird für die Anzeige in JComboBox verwendet
    @Override
    public String toString() {
        String anzeige = sourcePdf != null ? sourcePdf : "Unbekanntes PDF";
        if (abrechnungszeitraumStart != null) {
             anzeige += " (" + abrechnungszeitraumStart + ")";
        } else if (abrechnungszeitraumStartStr != null){
             anzeige += " (Datum: " + abrechnungszeitraumStartStr + ")"; // Fallback, falls Parsen fehlschlug
        } else {
             anzeige += " (Kein Datum gefunden)";
        }
        if (error != null && !error.isBlank()) {
            anzeige += " [FEHLER]";
        }
        return anzeige;
    }

    // --- Sortierlogik ---
    // Sortiert nach Startdatum (aufsteigend), PDFs ohne Datum kommen ans Ende.
    @Override
    public int compareTo(PdfDokument other) {
         return Comparator.comparing(PdfDokument::getAbrechnungszeitraumStart, Comparator.nullsLast(LocalDate::compareTo))
                       .thenComparing(PdfDokument::getSourcePdf, Comparator.nullsLast(String::compareTo)) // Zweite Sortierung nach Name
                       .compare(this, other);
    }

    // --- Gleichheitsprüfung ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PdfDokument that = (PdfDokument) o;
        // Nutze vollen Pfad zur Eindeutigkeitsprüfung, falls verfügbar, sonst Dateiname
        if (fullPath != null && that.fullPath != null) {
             return fullPath.equals(that.fullPath);
        }
        return Objects.equals(sourcePdf, that.sourcePdf); // Fallback
    }

    @Override
    public int hashCode() {
         // Nutze vollen Pfad für Hashcode, falls verfügbar
        return Objects.hash(fullPath != null ? fullPath : sourcePdf);
    }
}
