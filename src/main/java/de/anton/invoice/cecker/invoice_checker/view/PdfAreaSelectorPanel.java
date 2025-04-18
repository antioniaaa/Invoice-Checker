package de.anton.invoice.cecker.invoice_checker.view;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination; // Import für Rendering Ziel
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.anton.invoice.cecker.invoice_checker.model.AreaDefinition;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections; // Import hinzugefügt
import java.util.List;

/**
 * Ein Panel zur Anzeige einer PDF-Seite und zur visuellen Auswahl
 * von rechteckigen Bereichen. Funktionierende Basisversion mit Vereinfachungen.
 */
public class PdfAreaSelectorPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(PdfAreaSelectorPanel.class);

    private PDDocument currentDocument;
    private PDFRenderer pdfRenderer;
    private int currentPageNumber = -1; // 0-basiert intern, -1 wenn nichts geladen
    private BufferedImage currentPageImage;
    private float renderScale = 1.5f; // Skalierung für bessere Lesbarkeit (höhere Auflösung intern)
    private float displayScale = 1.0f; // Skalierung für die Anzeige im Panel (wird angepasst)


    private List<AreaDefinition> areasOnPage = new ArrayList<>(); // Bereiche für die aktuelle Seite
    private Point startPointPixels; // Startpunkt beim Ziehen (in Pixel-Koordinaten)
    private Rectangle currentSelectionRectPixels; // Aktuell gezeichnetes Rechteck (in Pixel-Koordinaten)

    public PdfAreaSelectorPanel() {
        this.setBackground(Color.LIGHT_GRAY);
        AreaMouseListener listener = new AreaMouseListener();
        this.addMouseListener(listener);
        this.addMouseMotionListener(listener);
        this.setAutoscrolls(true); // Ermöglicht Scrollen in JScrollPane
    }

    public void loadDocument(PDDocument document) {
        closeDocument();
        this.currentDocument = document;
        if (this.currentDocument != null) {
            this.pdfRenderer = new PDFRenderer(this.currentDocument);
            setPage(0); // Zeige erste Seite
        } else {
            resetPanelState();
        }
    }

    public void setPage(int pageIndex) {
        if (pdfRenderer == null || pageIndex < 0 || pageIndex >= currentDocument.getNumberOfPages()) {
            log.warn("Ungültiger Seitenindex: {}", pageIndex);
            resetPanelState();
            return;
        }
        this.currentPageNumber = pageIndex;
        this.areasOnPage.clear();
        this.currentSelectionRectPixels = null;
        renderPage();
    }

    // Setzt die Bereiche, die für die aktuelle Seite gelten sollen
    public void setAreas(List<AreaDefinition> areas) {
        this.areasOnPage = new ArrayList<>(areas != null ? areas : Collections.emptyList());
        log.debug("Setze {} Bereiche für Seite {}", this.areasOnPage.size(), this.currentPageNumber + 1);
        repaint();
    }

    // Gibt die aktuell auf dem Panel definierten Bereiche zurück (für die aktuelle Seite)
    public List<AreaDefinition> getAreas() {
        return new ArrayList<>(this.areasOnPage);
    }

     public int getCurrentPageNumber() { // Gibt 0-basierten Index zurück
         return currentPageNumber;
     }

     public int getTotalPages() {
         return (currentDocument != null) ? currentDocument.getNumberOfPages() : 0;
     }

    // Rendert die aktuelle Seite
    private void renderPage() {
        if (pdfRenderer != null && currentPageNumber >= 0) {
            try {
            	// Wähle eine feste DPI für das Rendering
                int dpi = 150; // Gute Balance zwischen Qualität und Performance/Speicher
                log.debug("Rendere Seite {} mit {} DPI...", currentPageNumber + 1, dpi);
                currentPageImage = pdfRenderer.renderImageWithDPI(currentPageNumber, dpi);

                // Berechne den Skalierungsfaktor, der dieser DPI entspricht
                // Standard PDF DPI ist 72 points per inch
                this.renderScale = dpi / 72.0f;

                // Passe die bevorzugte Größe des Panels an das gerenderte Bild an
                setPreferredSize(new Dimension(currentPageImage.getWidth(), currentPageImage.getHeight()));
                revalidate(); // Wichtig für JScrollPane
                repaint();
                log.debug("Seite {} gerendert ({}x{} Pixel, entspricht Scale ~{:.2f})",
                          currentPageNumber + 1, currentPageImage.getWidth(), currentPageImage.getHeight(), renderScale);
            } catch (IOException e) {
            	log.error("Fehler beim Rendern der PDF-Seite {}: {}", currentPageNumber + 1, e.getMessage());
                currentPageImage = null; // Setze Bild zurück
                setPreferredSize(new Dimension(400, 500)); // Gehe zu Default zurück
                revalidate();
                repaint();
            }
        } else {
            // Kein Dokument oder keine gültige Seite ausgewählt
            currentPageImage = null;
            setPreferredSize(new Dimension(400, 500));
            revalidate();
            repaint();
       }
    }

    // Schließt das PDF und setzt Panel zurück
    public void closeDocument() {
        if (currentDocument != null) {
            try { currentDocument.close(); log.debug("PDDocument geschlossen."); }
            catch (IOException e) { log.error("Fehler beim Schließen des PDDocuments: {}", e.getMessage()); }
        }
        resetPanelState();
    }

    // Setzt den Zustand des Panels zurück
    private void resetPanelState() {
        currentDocument = null;
        pdfRenderer = null;
        currentPageImage = null;
        areasOnPage.clear();
        currentPageNumber = -1;
        startPointPixels = null;
        currentSelectionRectPixels = null;
        setPreferredSize(new Dimension(400, 500)); // Standardgröße
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentPageImage != null) {
            // Zeichne das gerenderte PDF-Bild
            g.drawImage(currentPageImage, 0, 0, this);

            Graphics2D g2d = (Graphics2D) g;
            g2d.setStroke(new BasicStroke(1)); // Dünne Linie

            // Zeichne bereits definierte Bereiche
            g2d.setColor(new Color(0, 0, 255, 60)); // Blau, halbtransparent für Füllung
            Stroke defaultStroke = g2d.getStroke();
            Stroke dashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3}, 0); // Gestrichelte Linie

            for (AreaDefinition area : areasOnPage) {
                Rectangle pixelRect = transformPdfAreaToSwingPixels(area);
                if (pixelRect != null) {
                    g2d.fill(pixelRect); // Fülle den Bereich
                    g2d.setColor(Color.BLUE); // Blaue Umrandung
                    g2d.setStroke(dashedStroke); // Gestrichelt
                    g2d.draw(pixelRect); // Zeichne Umrandung
                    g2d.setColor(new Color(0, 0, 255, 60)); // Farbe für nächste Füllung wiederherstellen
                    g2d.setStroke(defaultStroke); // Strich wieder normal
                }
            }

            // Zeichne das aktuell vom Benutzer gezogene Rechteck (rote Linie)
            if (currentSelectionRectPixels != null) {
                g2d.setColor(Color.RED);
                g2d.setStroke(defaultStroke); // Normale Linie
                g2d.draw(currentSelectionRectPixels);
            }
        } else {
            // Platzhaltertext, wenn kein PDF geladen ist
            g.setColor(Color.DARK_GRAY);
            g.drawString("Kein PDF geladen oder Seite kann nicht angezeigt werden.", 20, 30);
        }
    }

    // --- Koordinatentransformation (VEREINFACHT) ---

    /**
     * Transformiert einen Punkt von Swing-Pixel-Koordinaten (Ursprung oben links)
     * in PDF-Punkt-Koordinaten (Ursprung unten links).
     * VEREINFACHT: Ignoriert Rotation, geht von Standard-Boxen aus.
     */
    private Point2D.Float transformSwingPixelsToPdfPoints(Point swingPixels) {
        if (currentDocument == null || currentPageNumber < 0 || swingPixels == null || currentPageImage == null) return null;

        try {
            PDPage page = currentDocument.getPage(currentPageNumber);
            PDRectangle pageSize = page.getCropBox(); // Oder MediaBox
            if (pageSize == null) pageSize = page.getMediaBox();
            if (pageSize == null) return null;

            float pageHeightPdfPoints = pageSize.getHeight();

            // Skaliere Pixel zurück zu PDF-Punkten unter Berücksichtigung der Render-Skalierung
            // Annahme: Das gerenderte Bild (currentPageImage) entspricht der Größe der pageSize * renderScale
            float pdfX_relative = (float) (swingPixels.x / renderScale);
            float pdfY_relative_from_top = (float) (swingPixels.y / renderScale);

            // Transformiere Y-Koordinate (Oben-Links -> Unten-Links)
            float pdfY_relative_from_bottom = pageHeightPdfPoints - pdfY_relative_from_top;

            // Addiere den Ursprung der PDF-Box (untere linke Ecke)
            float finalX = pdfX_relative + pageSize.getLowerLeftX();
            float finalY = pdfY_relative_from_bottom + pageSize.getLowerLeftY();

            // TODO: Rotation berücksichtigen!

            return new Point2D.Float(finalX, finalY);

        } catch (Exception e) {
            log.error("Fehler bei Swing -> PDF Koordinatentransformation: {}", e.getMessage());
            return null;
        }
    }

     /**
      * Transformiert einen Bereich von PDF-Koordinaten (Ursprung unten links)
      * in Swing-Pixel-Koordinaten (Ursprung oben links).
      * VEREINFACHT: Ignoriert Rotation.
      */
    private Rectangle transformPdfAreaToSwingPixels(AreaDefinition pdfArea) {
        if (currentDocument == null || currentPageNumber < 0 || pdfArea == null || currentPageImage == null) return null;

        try {
            PDPage page = currentDocument.getPage(currentPageNumber);
            PDRectangle pageSize = page.getCropBox();
            if (pageSize == null) pageSize = page.getMediaBox();
            if (pageSize == null) return null;

            float pageHeightPdfPoints = pageSize.getHeight();

            // PDF-Koordinaten des Bereichs (relativ zum Seitenursprung 0,0 unten links)
            float pdfX1 = pdfArea.getX1();
            float pdfY1_bottom = pdfArea.getY1(); // Untere Y-Koordinate
            float pdfWidth = pdfArea.getWidth();
            float pdfHeight = pdfArea.getHeight();
            float pdfY2_top = pdfY1_bottom + pdfHeight; // Obere Y-Koordinate im PDF-System

            // Berücksichtige den Ursprung der PDF-Box
            float pdfX1_relative = pdfX1 - pageSize.getLowerLeftX();
            // float pdfY1_relative_from_bottom = pdfY1_bottom - pageSize.getLowerLeftY(); // Wird nicht direkt gebraucht
            float pdfY2_relative_from_bottom = pdfY2_top - pageSize.getLowerLeftY();

            // Transformiere obere Y-Koordinate von PDF (unten-links) zu Swing (oben-links)
            float pdfY2_relative_from_top = pageHeightPdfPoints - pdfY2_relative_from_bottom;

            // Skaliere zu Pixeln mit der Render-Skalierung
            int swingX = Math.round(pdfX1_relative * renderScale);
            int swingY = Math.round(pdfY2_relative_from_top * renderScale); // Obere linke Ecke Y in Pixeln
            int swingWidth = Math.round(pdfWidth * renderScale);
            int swingHeight = Math.round(pdfHeight * renderScale);

             // TODO: Rotation berücksichtigen!

            return new Rectangle(swingX, swingY, swingWidth, swingHeight);

        } catch (Exception e) {
            log.error("Fehler bei PDF -> Swing Koordinatentransformation: {}", e.getMessage());
            return null;
        }
    }

    // --- Mouse Listener für Bereichsauswahl ---
    private class AreaMouseListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (currentPageImage == null || !isEnabled()) return; // Nur wenn aktiv
            startPointPixels = e.getPoint();
            currentSelectionRectPixels = new Rectangle();
            log.trace("MousePressed bei Pixel: {}", startPointPixels);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (startPointPixels == null || currentPageImage == null || !isEnabled()) return;
            Point endPointPixels = e.getPoint();
            // Berechne Rechteck in Pixeln für die Anzeige
            int x = Math.min(startPointPixels.x, endPointPixels.x);
            int y = Math.min(startPointPixels.y, endPointPixels.y);
            int width = Math.abs(startPointPixels.x - endPointPixels.x);
            int height = Math.abs(startPointPixels.y - endPointPixels.y);
            currentSelectionRectPixels = new Rectangle(x, y, width, height);
            repaint(); // Panel neu zeichnen, um Auswahlrechteck anzuzeigen
        }

        @Override
        public void mouseReleased(MouseEvent e) {
             if (startPointPixels == null || currentSelectionRectPixels == null || currentPageImage == null || !isEnabled()) return;
             log.trace("MouseReleased");

             // Verhindere zu kleine Rechtecke
             if (currentSelectionRectPixels.width < 5 || currentSelectionRectPixels.height < 5) {
                 log.debug("Auswahlrechteck zu klein, ignoriert.");
             } else {
                 // Transformiere Start- und Endpunkte (in Pixeln) in PDF-Koordinaten
                 Point endPointPixels = e.getPoint();
                 Point2D.Float pdfP1 = transformSwingPixelsToPdfPoints(startPointPixels);
                 Point2D.Float pdfP2 = transformSwingPixelsToPdfPoints(endPointPixels);

                 if (pdfP1 != null && pdfP2 != null) {
                     // Erstelle AreaDefinition (Konstruktor sortiert Koordinaten x1<=x2, y1<=y2)
                     AreaDefinition newArea = new AreaDefinition(pdfP1.x, pdfP1.y, pdfP2.x, pdfP2.y);
                     log.info("Neuen Bereich hinzugefügt (PDF Koordinaten): {}", newArea);

                     // Füge Bereich zur Liste der aktuellen Seite hinzu
                     areasOnPage.add(newArea);

                     // Benachrichtige den Dialog, dass sich die Bereiche geändert haben
                     firePropertyChange("areasChanged", null, getAreas()); // Event feuern

                 } else {
                      log.error("Konnte Auswahl nicht in PDF-Koordinaten umwandeln.");
                      JOptionPane.showMessageDialog(PdfAreaSelectorPanel.this,
                              "Fehler bei der Koordinatentransformation.\nIst das PDF korrekt formatiert?",
                              "Transformationsfehler", JOptionPane.WARNING_MESSAGE);
                 }
             }
             // Reset für nächste Auswahl
             startPointPixels = null;
             currentSelectionRectPixels = null;
             repaint(); // Zeichne Panel neu (zeigt jetzt den hinzugefügten Bereich oder nichts)
        }
    }
}
