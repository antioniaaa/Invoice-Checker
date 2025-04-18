package de.anton.invoice.cecker.invoice_checker.model;

import java.awt.geom.Rectangle2D;
import java.io.Serializable; // Für Speicherung
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Locale;          // Um das Zahlenformat (Dezimalpunkt) festzulegen

/**
 * Repräsentiert einen rechteckigen Bereich auf einer PDF-Seite
 * in PDF-Punkt-Koordinaten (Ursprung normalerweise unten links).
 * Stellt Methoden zur Konvertierung in verschiedene Formate bereit.
 * Ist Serializable, um in Konfigurationen gespeichert werden zu können.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AreaDefinition implements Serializable {
    // serialVersionUID wird für die Serialisierung verwendet, um Versionen zu verwalten.
    private static final long serialVersionUID = 1L;

    // Koordinaten der unteren linken (x1, y1) und oberen rechten (x2, y2) Ecke.
    private float x1;
    private float y1; // Untere Y-Koordinate
    private float x2;
    private float y2; // Obere Y-Koordinate

    /**
     * Standard-Konstruktor (parameterlos).
     * Wird von JSON-Bibliotheken wie Jackson benötigt.
     */
    public AreaDefinition() {}

    /**
     * Konstruktor zum Erstellen einer AreaDefinition mit Koordinaten.
     * Stellt sicher, dass x1 <= x2 und y1 <= y2 gespeichert werden.
     *
     * @param x1 X-Koordinate der ersten Ecke.
     * @param y1 Y-Koordinate der ersten Ecke.
     * @param x2 X-Koordinate der zweiten Ecke.
     * @param y2 Y-Koordinate der zweiten Ecke.
     */
    public AreaDefinition(float x1, float y1, float x2, float y2) {
        // Sortiere die Koordinaten, um sicherzustellen, dass (x1, y1) immer unten links ist
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2); // y1 ist der kleinere (untere) Y-Wert
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2); // y2 ist der größere (obere) Y-Wert
    }

    /**
     * Konvertiert die Koordinaten in das von Camelot erwartete String-Format: "x1,y1,x2,y2".
     * Verwendet Kommas als Trennzeichen zwischen den Zahlen und Punkte als Dezimaltrennzeichen.
     * Die Zahlen werden auf zwei Nachkommastellen formatiert.
     *
     * @return Der formatierte Bereichs-String für den 'table_areas'-Parameter von Camelot.
     */
    public String toCamelotString() {
        // Camelot erwartet: x1,y1,x2,y2 (bottom-left x, bottom-left y, top-right x, top-right y)
        // Locale.US erzwingt den Punkt als Dezimaltrennzeichen.
        // %.2f formatiert die float-Zahl auf 2 Nachkommastellen.
        // Die Kommas im Format-String trennen die vier Zahlen.
        return String.format(Locale.US, "%.2f,%.2f,%.2f,%.2f", x1, y1, x2, y2);
    }

    /**
     * Konvertiert die PDF-Bereichskoordinaten in ein {@link Rectangle2D.Float}-Objekt,
     * das für Zeichenoperationen in Java Swing/AWT verwendet werden kann.
     * Behält das PDF-Koordinatensystem bei (Ursprung unten links).
     * Die Transformation in Swing-Pixelkoordinaten muss separat erfolgen.
     *
     * @return Ein Rectangle2D.Float-Objekt mit den Koordinaten des Bereichs.
     */
    public Rectangle2D.Float toRectangle2D() {
        // Erstellt ein Rechteck mit x, y (untere linke Ecke), Breite und Höhe.
        return new Rectangle2D.Float(x1, y1, getWidth(), getHeight());
    }

    // --- Getter und Setter (notwendig für Jackson und allgemeinen Zugriff) ---

    public float getX1() { return x1; }
    public void setX1(float x1) { this.x1 = x1; }

    public float getY1() { return y1; }
    public void setY1(float y1) { this.y1 = y1; }

    public float getX2() { return x2; }
    public void setX2(float x2) { this.x2 = x2; }

    public float getY2() { return y2; }
    public void setY2(float y2) { this.y2 = y2; }

    /**
     * Berechnet die Breite des Bereichs.
     * @return Die Breite (immer positiv).
     */
    public float getWidth() {
        return Math.abs(x2 - x1);
    }

    /**
     * Berechnet die Höhe des Bereichs.
     * @return Die Höhe (immer positiv).
     */
    public float getHeight() {
        return Math.abs(y2 - y1);
    }

    /**
     * Gibt eine String-Repräsentation des Bereichs zurück, nützlich für Logging und Debugging.
     * Verwendet das US-Locale für konsistente Formatierung.
     * @return Ein String im Format "[x1, y1, x2, y2]".
     */
    @Override
    public String toString() {
        // Formatierung mit einer Nachkommastelle für bessere Lesbarkeit in Logs/Listen
        return String.format(Locale.US, "[%.1f, %.1f, %.1f, %.1f]", x1, y1, x2, y2);
    }

    /**
     * Vergleicht dieses AreaDefinition-Objekt mit einem anderen Objekt auf Gleichheit.
     * Zwei Bereiche sind gleich, wenn alle ihre Koordinaten übereinstimmen.
     * @param o Das zu vergleichende Objekt.
     * @return true, wenn die Objekte gleich sind, andernfalls false.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // Identisches Objekt
        if (o == null || getClass() != o.getClass()) return false; // Null oder anderer Typ
        AreaDefinition that = (AreaDefinition) o;
        // Vergleiche alle vier Koordinaten auf Gleichheit (mit Float.compare für Genauigkeit)
        return Float.compare(that.x1, x1) == 0 &&
               Float.compare(that.y1, y1) == 0 &&
               Float.compare(that.x2, x2) == 0 &&
               Float.compare(that.y2, y2) == 0;
    }

    /**
     * Generiert einen Hashcode für das AreaDefinition-Objekt.
     * Basiert auf den vier Koordinatenwerten.
     * @return Der Hashcode des Objekts.
     */
    @Override
    public int hashCode() {
        // Verwendet die Koordinaten zur Hashcode-Berechnung
        return Objects.hash(x1, y1, x2, y2);
    }
}