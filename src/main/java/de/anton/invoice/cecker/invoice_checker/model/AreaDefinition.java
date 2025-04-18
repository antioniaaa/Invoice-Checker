package de.anton.invoice.cecker.invoice_checker.model;

import java.awt.geom.Rectangle2D;
import java.io.Serializable; // Für Speicherung
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Repräsentiert einen rechteckigen Bereich auf einer PDF-Seite
 * in PDF-Koordinaten (Ursprung unten links).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AreaDefinition implements Serializable {
    private static final long serialVersionUID = 1L; // Für Serialisierung

    private float x1;
    private float y1; // Untere linke Ecke Y
    private float x2;
    private float y2; // Obere rechte Ecke Y

    // Standardkonstruktor für Jackson
    public AreaDefinition() {}

    public AreaDefinition(float x1, float y1, float x2, float y2) {
        // Sicherstellen, dass x1 <= x2 und y1 <= y2
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2); // y1 ist der untere Wert
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2); // y2 ist der obere Wert
    }

    // Konvertiert zu Camelots String-Format "x1,y1,x2,y2"
    public String toCamelotString() {
        // Camelot erwartet: x1,y1,x2,y2 (bottom-left x, bottom-left y, top-right x, top-right y)
        return String.format("%.2f,%.2f,%.2f,%.2f", x1, y1, x2, y2).replace(',', '.'); // Punkte statt Komma
    }

    // Konvertiert zu einem Rectangle2D für Zeichenzwecke (anpassbar an Koordinatensystem)
    public Rectangle2D.Float toRectangle2D() {
        return new Rectangle2D.Float(x1, y1, getWidth(), getHeight());
    }

    public float getX1() { return x1; }
    public void setX1(float x1) { this.x1 = x1; }
    public float getY1() { return y1; }
    public void setY1(float y1) { this.y1 = y1; }
    public float getX2() { return x2; }
    public void setX2(float x2) { this.x2 = x2; }
    public float getY2() { return y2; }
    public void setY2(float y2) { this.y2 = y2; }
    public float getWidth() { return Math.abs(x2 - x1); }
    public float getHeight() { return Math.abs(y2 - y1); }

    @Override
    public String toString() {
        return String.format("[%.1f, %.1f, %.1f, %.1f]", x1, y1, x2, y2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AreaDefinition that = (AreaDefinition) o;
        return Float.compare(that.x1, x1) == 0 && Float.compare(that.y1, y1) == 0 && Float.compare(that.x2, x2) == 0 && Float.compare(that.y2, y2) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x1, y1, x2, y2);
    }
}
