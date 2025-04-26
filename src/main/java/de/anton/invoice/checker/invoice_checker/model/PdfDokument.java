package de.anton.invoice.checker.invoice_checker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;


import com.fasterxml.jackson.annotation.JsonIgnore; // Import für @JsonIgnore

import java.util.Collections; // Import für Collections.emptyList()

/**
* Repräsentiert ein verarbeitetes PDF-Dokument mit seinen Metadaten
* (Dateiname, Pfad, Abrechnungszeitraum), den darin gefundenen Tabellen
* und dem erkannten Rechnungstyp.
* Implementiert Comparable für die Sortierung nach Abrechnungsdatum.
*/
@JsonIgnoreProperties(ignoreUnknown = true) // Ignoriert unbekannte Felder beim JSON-Lesen
public class PdfDokument implements Comparable<PdfDokument> {

 // --- Felder aus JSON oder intern gesetzt ---
 @JsonProperty("source_pdf")
 private String sourcePdf; // Dateiname

 @JsonProperty("full_path")
 private String fullPath; // Voller Pfad zur Originaldatei

 @JsonProperty("billing_period_start")
 private String abrechnungszeitraumStartStr; // Startdatum als JJJJ-MM-TT String

 @JsonProperty("billing_period_end")
 private String abrechnungszeitraumEndeStr; // Enddatum als JJJJ-MM-TT String

 @JsonProperty("tables") // Stelle sicher, dass Jackson die Tabellenliste mappt
 private List<ExtrahierteTabelle> tables = new ArrayList<>(); // Extrahierte Tabellen (immer initialisiert)

 @JsonProperty("error")
 private String error; // Fehlermeldung aus der Verarbeitung

 // --- Transiente Felder (werden nicht serialisiert/deserialisiert) ---
 @JsonIgnore // Jackson soll dieses Feld ignorieren
 private transient LocalDate abrechnungszeitraumStart; // Geparsstes Startdatum
 @JsonIgnore // Jackson soll dieses Feld ignorieren
 private transient LocalDate abrechnungszeitraumEnde; // Geparsstes Enddatum
 @JsonIgnore // Jackson soll dieses Feld ignorieren
 private transient InvoiceTypeConfig detectedInvoiceType; // Der erkannte Rechnungstyp

 // --- Konstruktoren ---
 /**
  * Standardkonstruktor (wird von Jackson benötigt).
  * Initialisiert die Tabellenliste.
  */
 public PdfDokument() {
     this.tables = new ArrayList<>();
 }

 // --- Getter und Setter ---

 public String getSourcePdf() { return sourcePdf; }
 public void setSourcePdf(String sourcePdf) { this.sourcePdf = sourcePdf; }

 public String getFullPath() { return fullPath; }
 public void setFullPath(String fullPath) { this.fullPath = fullPath; }

 public String getAbrechnungszeitraumStartStr() { return abrechnungszeitraumStartStr; }
 /**
  * Setzt das Startdatum als String und versucht sofort, es als LocalDate zu parsen.
  * @param abrechnungszeitraumStartStr Startdatum im Format JJJJ-MM-TT.
  */
 public void setAbrechnungszeitraumStartStr(String abrechnungszeitraumStartStr) {
     this.abrechnungszeitraumStartStr = abrechnungszeitraumStartStr;
     parseDaten(); // Versuche sofort zu parsen
 }

 public String getAbrechnungszeitraumEndeStr() { return abrechnungszeitraumEndeStr; }
 /**
  * Setzt das Enddatum als String und versucht sofort, es als LocalDate zu parsen.
  * @param abrechnungszeitraumEndeStr Enddatum im Format JJJJ-MM-TT.
  */
  public void setAbrechnungszeitraumEndeStr(String abrechnungszeitraumEndeStr) {
     this.abrechnungszeitraumEndeStr = abrechnungszeitraumEndeStr;
     parseDaten(); // Versuche sofort zu parsen
 }

 public List<ExtrahierteTabelle> getTables() {
     // Gib die interne Liste direkt zurück oder eine Kopie/unveränderliche Liste
     return tables != null ? tables : Collections.emptyList(); // Nie null zurückgeben
 }
 public void setTables(List<ExtrahierteTabelle> tables) {
     this.tables = (tables != null) ? new ArrayList<>(tables) : new ArrayList<>();
 }

 public String getError() { return error; }
 public void setError(String error) { this.error = error; }

 // Getter für die geparsten (transienten) Datumsfelder
 @JsonIgnore
 public LocalDate getAbrechnungszeitraumStart() { return abrechnungszeitraumStart; }
 @JsonIgnore
 public LocalDate getAbrechnungszeitraumEnde() { return abrechnungszeitraumEnde; }

 // --- NEUE Getter/Setter für den erkannten Typ ---
 /**
  * Gibt die während der Verarbeitung erkannte {@link InvoiceTypeConfig} zurück.
  * @return Die erkannte Konfiguration oder null, wenn keine erkannt wurde.
  */
 @JsonIgnore // Nicht serialisieren
 public InvoiceTypeConfig getDetectedInvoiceType() {
     return detectedInvoiceType;
 }
 /**
  * Setzt die während der Verarbeitung erkannte {@link InvoiceTypeConfig}.
  * @param detectedInvoiceType Die erkannte Konfiguration.
  */
 @JsonIgnore // Nicht serialisieren
 public void setDetectedInvoiceType(InvoiceTypeConfig detectedInvoiceType) {
     this.detectedInvoiceType = detectedInvoiceType;
 }
 // --- Ende NEU ---

 // --- Hilfsmethoden ---

 /**
  * Fügt eine Liste von Tabellen zu diesem Dokument hinzu.
  * Nützlich zum Zusammenführen von Ergebnissen aus seitenweiser Extraktion.
  * Sortiert die Tabellenliste anschließend nach Seite und Index.
  * @param newTables Die hinzuzufügenden Tabellen. Kann null sein.
  */
 public void addTables(List<ExtrahierteTabelle> newTables) {
     if (this.tables == null) {
         this.tables = new ArrayList<>();
     }
     if (newTables != null) {
         this.tables.addAll(newTables);
         // Sortiere die Tabellen nach Seite (aufsteigend) und dann nach Index (aufsteigend)
         this.tables.sort(Comparator.comparingInt(ExtrahierteTabelle::getPage)
                                    .thenComparingInt(ExtrahierteTabelle::getIndex));
     }
 }

 /**
  * Interne Methode zum Parsen der Datumsstrings in LocalDate-Objekte.
  */
 private void parseDaten() {
     this.abrechnungszeitraumStart = versucheDatumParsen(this.abrechnungszeitraumStartStr);
     this.abrechnungszeitraumEnde = versucheDatumParsen(this.abrechnungszeitraumEndeStr);
 }

 /**
  * Versucht, einen Datumsstring im Format JJJJ-MM-TT zu parsen.
  */
 private LocalDate versucheDatumParsen(String datumStr) {
     if (datumStr != null && !datumStr.isBlank()) {
         try {
             return LocalDate.parse(datumStr);
         } catch (DateTimeParseException e) {
             System.err.println("Konnte Datum nicht parsen: " + datumStr); // Einfache Fehlerausgabe
             return null;
         }
     }
     return null;
 }

 // --- Standardmethoden: toString, compareTo, equals, hashCode ---

 @Override
 public String toString() {
     String anzeige = sourcePdf != null ? sourcePdf : "Unbekanntes PDF";
     if (abrechnungszeitraumStart != null) {
          anzeige += " (" + abrechnungszeitraumStart + ")";
     } else if (abrechnungszeitraumStartStr != null && !abrechnungszeitraumStartStr.isBlank()){
          anzeige += " (Datum: " + abrechnungszeitraumStartStr + ")";
     } else {
          anzeige += " (Kein Datum)";
     }
     if (error != null && !error.isBlank()) {
         anzeige += " [FEHLER]";
     }
     return anzeige;
 }

 @Override
 public int compareTo(PdfDokument other) {
      return Comparator.comparing(PdfDokument::getAbrechnungszeitraumStart, Comparator.nullsLast(LocalDate::compareTo))
                    .thenComparing(PdfDokument::getSourcePdf, Comparator.nullsLast(String::compareToIgnoreCase))
                    .compare(this, other);
 }

 @Override
 public boolean equals(Object o) {
     if (this == o) return true;
     if (o == null || getClass() != o.getClass()) return false;
     PdfDokument that = (PdfDokument) o;
     if (fullPath != null && !fullPath.isBlank() && that.fullPath != null && !that.fullPath.isBlank()) {
          return fullPath.equals(that.fullPath);
     }
     return Objects.equals(sourcePdf, that.sourcePdf);
 }

 @Override
 public int hashCode() {
     return Objects.hash(fullPath != null && !fullPath.isBlank() ? fullPath : sourcePdf);
 }
}