package de.anton.invoice.cecker.invoice_checker.model;

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
* (Dateiname, Pfad, Abrechnungszeitraum) und den darin gefundenen Tabellen.
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

 // --- Konstruktoren ---
 /**
  * Standardkonstruktor (wird von Jackson benötigt).
  */
 public PdfDokument() {
     this.tables = new ArrayList<>(); // Sicherstellen, dass Liste nie null ist
 }

 // --- Getter und Setter ---
 // Getter und Setter für alle Felder, die von Jackson gemappt werden sollen
 // oder von anderen Klassen benötigt werden.

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
     // Gib eine Kopie oder eine unveränderliche Liste zurück, wenn externe Änderungen verhindert werden sollen
     // Hier: Gib die interne Liste direkt zurück (einfacher, aber änderbar von außen)
     return tables != null ? tables : Collections.emptyList(); // Stelle sicher, dass nie null zurückgegeben wird
 }
 public void setTables(List<ExtrahierteTabelle> tables) {
     // Ersetze die interne Liste (erstelle Kopie für Sicherheit)
     this.tables = (tables != null) ? new ArrayList<>(tables) : new ArrayList<>();
 }

 public String getError() { return error; }
 public void setError(String error) { this.error = error; }

 // Getter für die geparsten (transienten) Datumsfelder
 @JsonIgnore // Sicherstellen, dass Jackson diese Getter nicht als Property interpretiert
 public LocalDate getAbrechnungszeitraumStart() { return abrechnungszeitraumStart; }
 @JsonIgnore // Sicherstellen, dass Jackson diese Getter nicht als Property interpretiert
 public LocalDate getAbrechnungszeitraumEnde() { return abrechnungszeitraumEnde; }

 // --- Hilfsmethoden ---

 /**
  * Fügt eine Liste von Tabellen zu diesem Dokument hinzu.
  * Nützlich zum Zusammenführen von Ergebnissen aus seitenweiser Extraktion.
  * @param newTables Die hinzuzufügenden Tabellen. Kann null sein.
  */
 public void addTables(List<ExtrahierteTabelle> newTables) {
     if (this.tables == null) {
         this.tables = new ArrayList<>(); // Initialisiere, falls noch nicht geschehen
     }
     if (newTables != null) {
         this.tables.addAll(newTables); // Füge alle neuen Tabellen hinzu
         // Optional: Tabellen nach Seite und Index neu sortieren, um Konsistenz zu wahren
         this.tables.sort(Comparator.comparingInt(ExtrahierteTabelle::getPage)
                                    .thenComparingInt(ExtrahierteTabelle::getIndex));
     }
 }

 /**
  * Interne Methode zum Parsen der Datumsstrings in LocalDate-Objekte.
  * Wird nach dem Setzen der String-Repräsentationen aufgerufen.
  */
 private void parseDaten() {
     this.abrechnungszeitraumStart = versucheDatumParsen(this.abrechnungszeitraumStartStr);
     this.abrechnungszeitraumEnde = versucheDatumParsen(this.abrechnungszeitraumEndeStr);
 }

 /**
  * Versucht, einen Datumsstring im Format JJJJ-MM-TT zu parsen.
  * @param datumStr Der zu parsende String.
  * @return Das LocalDate-Objekt oder null bei Fehlern oder leerem/null String.
  */
 private LocalDate versucheDatumParsen(String datumStr) {
     if (datumStr != null && !datumStr.isBlank()) {
         try {
             return LocalDate.parse(datumStr); // Annahme: JJJJ-MM-TT Format
         } catch (DateTimeParseException e) {
             // Fehler loggen wäre gut, aber hier erstmal nur null zurückgeben
             System.err.println("Konnte Datum nicht parsen: " + datumStr); // Einfache Fehlerausgabe
             return null;
         }
     }
     return null; // Gib null zurück für null oder leere Strings
 }

 // --- Standardmethoden: toString, compareTo, equals, hashCode ---

 /**
  * Gibt eine benutzerfreundliche Darstellung des Dokuments zurück (primär für ComboBox).
  */
 @Override
 public String toString() {
     String anzeige = sourcePdf != null ? sourcePdf : "Unbekanntes PDF";
     if (abrechnungszeitraumStart != null) {
          anzeige += " (" + abrechnungszeitraumStart + ")"; // Zeige geparstes Datum
     } else if (abrechnungszeitraumStartStr != null && !abrechnungszeitraumStartStr.isBlank()){
          anzeige += " (Datum: " + abrechnungszeitraumStartStr + ")"; // Fallback auf String
     } else {
          anzeige += " (Kein Datum)";
     }
     if (error != null && !error.isBlank()) {
         anzeige += " [FEHLER]"; // Markiere Dokumente mit Fehlern
     }
     return anzeige;
 }

 /**
  * Vergleicht dieses Dokument mit einem anderen, primär basierend auf dem Startdatum
  * des Abrechnungszeitraums (aufsteigend, nulls last). Bei gleichem oder fehlendem
  * Datum wird nach Dateiname sortiert.
  */
 @Override
 public int compareTo(PdfDokument other) {
      // Vergleiche primär nach Startdatum (nulls last = ohne Datum ans Ende)
      // Sekundär nach Dateiname (nulls last = ohne Namen ans Ende)
      return Comparator.comparing(PdfDokument::getAbrechnungszeitraumStart, Comparator.nullsLast(LocalDate::compareTo))
                    .thenComparing(PdfDokument::getSourcePdf, Comparator.nullsLast(String::compareToIgnoreCase)) // Ignoriere Groß/Kleinschreibung beim Namen
                    .compare(this, other);
 }

 /**
  * Prüft auf Gleichheit basierend auf dem vollständigen Pfad (falls vorhanden)
  * oder alternativ auf dem Dateinamen.
  */
 @Override
 public boolean equals(Object o) {
     if (this == o) return true;
     if (o == null || getClass() != o.getClass()) return false;
     PdfDokument that = (PdfDokument) o;
     // Nutze vollen Pfad zur Eindeutigkeitsprüfung, falls verfügbar
     if (fullPath != null && !fullPath.isBlank() && that.fullPath != null && !that.fullPath.isBlank()) {
          return fullPath.equals(that.fullPath);
     }
     // Fallback auf Dateinamen (weniger sicher, aber besser als nichts)
     return Objects.equals(sourcePdf, that.sourcePdf);
 }

 /**
  * Generiert einen Hashcode basierend auf dem vollständigen Pfad (falls vorhanden)
  * oder alternativ auf dem Dateinamen.
  */
 @Override
 public int hashCode() {
      // Nutze vollen Pfad für Hashcode, falls verfügbar
     return Objects.hash(fullPath != null && !fullPath.isBlank() ? fullPath : sourcePdf);
 }
}