package de.anton.invoice.checker.invoice_checker.view;

import de.anton.invoice.checker.invoice_checker.model.AreaDefinition;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Panel zur Anzeige einer PDF-Seite und zur visuellen Auswahl
 * von rechteckigen Bereichen. Implementiert einfaches Zoomen und Bereichsmanagement.
 * VEREINFACHUNGEN: Keine Rotation, einfache Koordinatentransformation.
 */
public class PdfAreaSelectorPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(PdfAreaSelectorPanel.class);

    private PDDocument currentDocument; // Das aktuell geladene PDFBox Dokument
    private PDFRenderer pdfRenderer;    // Zum Rendern der Seiten
    private int currentPageNumber = -1; // Aktuell angezeigte Seite (0-basiert), -1 wenn keine
    private BufferedImage pageImage;    // Das gerenderte Bild der aktuellen Seite
    private float currentRenderScale = 1.5f; // Aktuelle Skalierung für das interne Rendering
    // Transformation wird nicht mehr direkt benötigt, da wir mit Pixeln arbeiten und dann umrechnen
    // private AffineTransform screenDeviceTransform = new AffineTransform();

    private List<AreaDefinition> areasOnPage = new ArrayList<>(); // Definierte Bereiche für die AKTUELLE Seite
    private Point dragStartPointPixels; // Startpunkt des Mausziehens in Pixeln
    private Point dragEndPointPixels;   // Endpunkt des Mausziehens in Pixeln
    private boolean isDragging = false; // Flag, ob gerade ein Rechteck gezogen wird

    /**
     * Konstruktor. Setzt Hintergrund und Maus-Listener.
     */
    public PdfAreaSelectorPanel() {
        this.setBackground(Color.DARK_GRAY); // Hintergrund für besseren Kontrast zum weißen PDF
        AreaMouseListener listener = new AreaMouseListener(); // Eigener Listener für Mausaktionen
        this.addMouseListener(listener);
        this.addMouseMotionListener(listener);
        this.setAutoscrolls(true); // Wichtig für die Verwendung in JScrollPane
    }

    // --- PDF Dokumenten-Handling ---

    /**
     * Lädt ein neues PDF-Dokument zur Anzeige. Schließt ein eventuell vorher geladenes.
     * Zeigt standardmäßig die erste Seite an.
     *
     * @param document Das PDDocument, das geladen werden soll.
     */
    public void loadDocument(PDDocument document) {
        closeDocument(); // Schließe ggf. altes Dokument
        this.currentDocument = document;
        if (this.currentDocument != null) {
            this.pdfRenderer = new PDFRenderer(this.currentDocument);
            setPage(0); // Zeige erste Seite (Index 0)
        } else {
            resetPanelState(); // Kein Dokument -> Zustand zurücksetzen
        }
    }

    /**
     * Schließt das aktuell geladene PDF-Dokument und gibt Ressourcen frei.
     * Setzt den Zustand des Panels zurück.
     */
    public void closeDocument() {
        if (currentDocument != null) {
            try {
                currentDocument.close();
                log.debug("PDDocument geschlossen.");
            } catch (IOException e) {
                log.error("Fehler beim Schließen des PDDocuments: {}", e.getMessage());
            }
        }
        resetPanelState(); // Setzt alle internen Variablen zurück
    }

    /**
     * Setzt den Zustand des Panels zurück (kein Dokument, kein Bild etc.).
     */
    private void resetPanelState() {
        currentDocument = null;
        pdfRenderer = null;
        pageImage = null;
        areasOnPage.clear(); // Keine Bereiche mehr anzeigen
        currentPageNumber = -1; // Keine Seite ausgewählt
        dragStartPointPixels = null;
        dragEndPointPixels = null;
        isDragging = false;
        setPreferredSize(new Dimension(400, 500)); // Setze eine Standardgröße
        revalidate(); // Layout neu berechnen
        repaint();    // Neu zeichnen (zeigt dann "Kein PDF geladen")
    }

    // --- Seiten-Handling ---

    /**
     * Setzt die aktuell anzuzeigende Seite.
     * Löscht vorhandene Bereichsmarkierungen und rendert die neue Seite.
     *
     * @param pageIndex Der 0-basierte Index der anzuzeigenden Seite.
     */
    public void setPage(int pageIndex) {
        if (pdfRenderer == null || currentDocument == null || pageIndex < 0 || pageIndex >= currentDocument.getNumberOfPages()) {
            log.warn("Ungültiger Seitenindex angefordert: {} (Gesamtseiten: {})", pageIndex, getTotalPages());
            resetPanelState(); // Bei ungültigem Index alles zurücksetzen
            return;
        }
        log.debug("Wechsle zu Seite {}", pageIndex + 1);
        this.currentPageNumber = pageIndex;
        this.areasOnPage.clear(); // Bereiche der alten Seite löschen (müssen extern verwaltet werden)
        this.dragStartPointPixels = null; // Aktuelle Auswahl zurücksetzen
        this.dragEndPointPixels = null;
        this.isDragging = false;
        renderPage(); // Neue Seite rendern
    }

    /**
     * Gibt den 0-basierten Index der aktuell angezeigten Seite zurück.
     *
     * @return Der Seitenindex oder -1, wenn keine Seite angezeigt wird.
     */
    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    /**
     * Gibt die Gesamtzahl der Seiten im aktuell geladenen Dokument zurück.
     *
     * @return Die Seitenanzahl oder 0, wenn kein Dokument geladen ist.
     */
    public int getTotalPages() {
        return (currentDocument != null) ? currentDocument.getNumberOfPages() : 0;
    }


    // --- Bereichs-Handling (Areas) ---

    /**
     * Gibt eine Kopie der Liste der aktuell auf dem Panel definierten Bereiche zurück
     * (diese gelten nur für die aktuell angezeigte Seite).
     *
     * @return Eine Kopie der Liste der Bereiche.
     */
    public List<AreaDefinition> getAreas() {
        // Gib immer eine Kopie zurück, um externe Modifikationen zu verhindern
        return new ArrayList<>(this.areasOnPage);
    }

    /**
     * Setzt die Liste der Bereiche, die auf der aktuell angezeigten Seite
     * dargestellt werden sollen. Ersetzt alle vorhandenen Bereiche und
     * feuert ein "areasChanged" PropertyChangeEvent.
     *
     * @param newAreas Eine Liste von {@link AreaDefinition}-Objekten. Kann null oder leer sein.
     */
    public void setAreas(List<AreaDefinition> newAreas) {
        // Erstelle eine Kopie der alten Liste für das Event
        List<AreaDefinition> oldAreas = new ArrayList<>(this.areasOnPage);
        // Setze die neue Liste (immer eine neue Instanz, nie null)
        this.areasOnPage = new ArrayList<>(newAreas != null ? newAreas : Collections.emptyList());

        log.debug("Setze {} Bereiche für Seite {}", this.areasOnPage.size(), this.currentPageNumber + 1);
        repaint(); // Zeichne Panel neu, um die (neuen) Bereiche anzuzeigen

        // --- NEU: Feuere das PropertyChangeEvent ---
        // Feuere das Event, um Listener (wie den ConfigurationDialog) zu benachrichtigen.
        // Es ist wichtig, Kopien zu übergeben, falls Listener die Listen ändern könnten.
        firePropertyChange("areasChanged", oldAreas, new ArrayList<>(this.areasOnPage));
        // ------------------------------------------
    }

    /**
     * Entfernt einen spezifischen Bereich aus der Anzeige auf der aktuellen Seite.
     *
     * @param area Der zu entfernende Bereich.
     */
    public void removeArea(AreaDefinition area) {
        if (area != null) {
            // Erstelle eine Kopie der alten Liste für das Event
            List<AreaDefinition> oldAreas = new ArrayList<>(this.areasOnPage);
            boolean removed = this.areasOnPage.remove(area); // Entferne aus interner Liste

            if (removed) {
                log.debug("Bereich entfernt: {}", area);
                repaint(); // Zeichne Panel neu
                // Feuere Event, damit der Dialog die JList aktualisieren kann
                // Übergib die alte und die (kopierte) neue Liste
                firePropertyChange("areasChanged", oldAreas, new ArrayList<>(this.areasOnPage)); // Event auch hier feuern!
            } else {
                log.warn("Zu entfernender Bereich {} nicht in der Liste gefunden.", area);
            }
        }
    }

    // --- Zoom Handling ---

    /**
     * Vergrößert die Anzeige der aktuellen Seite.
     */
    public void zoomIn() {
        changeZoom(1.25f); // Faktor für Vergrößerung (z.B. 25%)
    }

    /**
     * Verkleinert die Anzeige der aktuellen Seite.
     */
    public void zoomOut() {
        changeZoom(0.8f); // Faktor für Verkleinerung (z.B. 20%, entspricht 1 / 1.25)
    }

    /**
     * Ändert die Render-Skalierung und rendert die Seite neu.
     *
     * @param factor Der Faktor, um den die Skalierung geändert wird (> 1 für Zoom In, < 1 für Zoom Out).
     */
    private void changeZoom(float factor) {
        if (pageImage == null) return; // Zoomen nur möglich, wenn Bild da ist
        float newScale = currentRenderScale * factor;
        // Begrenze den Zoom auf sinnvolle Werte (optional)
        newScale = Math.max(0.2f, Math.min(newScale, 8.0f)); // Min 20%, Max 800%
        if (Math.abs(newScale - currentRenderScale) > 0.01f) { // Nur neu rendern, wenn sich Skala ändert
            log.info("Ändere Zoom von {} auf {}", currentRenderScale, newScale);
            currentRenderScale = newScale;
            renderPage(); // Seite mit neuer Skalierung neu rendern
        }
    }

    // --- Rendering ---

    /**
     * Rendert die aktuelle Seite als Bild mit der aktuellen {@code currentRenderScale}.
     */
    private void renderPage() {
        if (pdfRenderer != null && currentPageNumber >= 0) {
            try {
                // Rendere mit der aktuellen Skalierung
                log.debug("Rendere Seite {} mit Skalierung {}...", currentPageNumber + 1, currentRenderScale);
                // renderImage verwendet die Skalierung direkt
                pageImage = pdfRenderer.renderImage(currentPageNumber, currentRenderScale);
                // Passe die bevorzugte Größe des Panels an das gerenderte Bild an
                setPreferredSize(new Dimension(pageImage.getWidth(), pageImage.getHeight()));
                revalidate(); // Teile dem ScrollPane die neue Größe mit
                repaint();    // Zeichne das neue Bild
                log.debug("Seite {} gerendert ({}x{} Pixel @ Scale {})",
                        currentPageNumber + 1, pageImage.getWidth(), pageImage.getHeight(), currentRenderScale);
            } catch (IOException e) {
                log.error("Fehler beim Rendern der PDF-Seite {}: {}", currentPageNumber + 1, e.getMessage());
                resetPanelState(); // Bei Fehler zurücksetzen
            }
        } else {
            resetPanelState(); // Kein Dokument -> zurücksetzen
        }
    }

    /**
     * Zeichnet die Komponente: das PDF-Seitenbild und die Auswahlrechtecke.
     *
     * @param g Das Graphics-Objekt zum Zeichnen.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // Rufe die Superklasse auf (wichtig!)
        Graphics2D g2d = (Graphics2D) g.create(); // Erstelle eine Kopie des Graphics-Objekts

        try {
            if (pageImage != null) {
                // Zeichne das gerenderte PDF-Bild an Position (0,0)
                g2d.drawImage(pageImage, 0, 0, this);

                // --- Zeichne definierte Bereiche (blau, halbtransparent) ---
                g2d.setStroke(new BasicStroke(1)); // Dünne Linie für Umrandung
                Color areaFillColor = new Color(0, 0, 255, 60); // Blau, halbtransparent
                Color areaBorderColor = Color.BLUE;

                for (AreaDefinition area : areasOnPage) {
                    // Transformiere PDF-Bereichskoordinaten in Pixelkoordinaten für die Anzeige
                    Rectangle pixelRect = transformPdfAreaToSwingPixels(area);
                    if (pixelRect != null) {
                        g2d.setColor(areaFillColor);
                        g2d.fill(pixelRect); // Fülle den Bereich
                        g2d.setColor(areaBorderColor);
                        g2d.draw(pixelRect); // Zeichne die Umrandung
                    }
                }

                // --- Zeichne das aktuelle Auswahlrechteck (während des Ziehens, rot) ---
                if (isDragging && dragStartPointPixels != null && dragEndPointPixels != null) {
                    g2d.setColor(Color.RED);
                    g2d.setStroke(new BasicStroke(1)); // Normale, durchgezogene Linie
                    // Berechne die Eckpunkte und Größe des Rechtecks in Pixeln
                    int x = Math.min(dragStartPointPixels.x, dragEndPointPixels.x);
                    int y = Math.min(dragStartPointPixels.y, dragEndPointPixels.y);
                    int width = Math.abs(dragStartPointPixels.x - dragEndPointPixels.x);
                    int height = Math.abs(dragStartPointPixels.y - dragEndPointPixels.y);
                    g2d.drawRect(x, y, width, height); // Zeichne das Rechteck
                }
            } else {
                // Zeige Platzhaltertext, wenn kein PDF geladen ist
                g2d.setColor(Color.WHITE); // Helle Farbe auf dunklem Hintergrund
                FontMetrics fm = g2d.getFontMetrics();
                String text = "Kein Vorschau-PDF geladen.";
                int x = (getWidth() - fm.stringWidth(text)) / 2;
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2d.drawString(text, x, y);
            }
        } finally {
            g2d.dispose(); // Gib die Graphics-Kopie immer frei!
        }
    }

    // --- Koordinatentransformation (VEREINFACHT: Keine Rotation) ---

    /**
     * Transformiert einen Punkt von Swing-Pixel-Koordinaten (Ursprung oben links)
     * in PDF-Punkt-Koordinaten (Ursprung unten links).
     * Berücksichtigt die aktuelle Render-Skalierung und die PDF-Seitenbox.
     * VEREINFACHT: Ignoriert Seitenrotation.
     *
     * @param swingPixels Der Punkt in Pixelkoordinaten relativ zum Panel.
     * @return Der Punkt in PDF-Koordinaten oder null bei Fehlern.
     */
    private Point2D.Float transformSwingPixelsToPdfPoints(Point swingPixels) {
        if (currentDocument == null || currentPageNumber < 0 || swingPixels == null || pageImage == null) return null;

        try {
            PDPage page = currentDocument.getPage(currentPageNumber);
            // Verwende CropBox als primäre Referenz, sonst MediaBox
            PDRectangle box = page.getCropBox();
            if (box == null) box = page.getMediaBox();
            if (box == null) {
                log.warn("Keine Page Size Box gefunden für Seite {}", currentPageNumber + 1);
                return null;
            }

            float pageHeightPdfPoints = box.getHeight(); // Höhe der Seite in PDF-Punkten

            // Skaliere Pixel zurück zu relativen PDF-Einheiten (bezogen auf den Box-Ursprung)
            // basierend auf der Skalierung, mit der das Bild gerendert wurde.
            float pdfXRel = swingPixels.x / currentRenderScale;
            float pdfYRelFromTop = swingPixels.y / currentRenderScale;

            // Konvertiere Y-Koordinate von "Abstand von oben" zu "Abstand von unten"
            float pdfYRelFromBottom = pageHeightPdfPoints - pdfYRelFromTop;

            // Addiere den Ursprung der PDF-Box (untere linke Ecke) hinzu
            float finalX = pdfXRel + box.getLowerLeftX();
            float finalY = pdfYRelFromBottom + box.getLowerLeftY(); // Y von unten

            // TODO: Seitenrotation muss hier berücksichtigt werden für korrekte Koordinaten!

            return new Point2D.Float(finalX, finalY);

        } catch (Exception e) {
            log.error("Fehler bei Swing -> PDF Koordinatentransformation: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Transformiert einen Bereich von PDF-Koordinaten (Ursprung unten links)
     * in Swing-Pixel-Koordinaten (Ursprung oben links) für die Anzeige.
     * Berücksichtigt die aktuelle Render-Skalierung und die PDF-Seitenbox.
     * VEREINFACHT: Ignoriert Seitenrotation.
     *
     * @param pdfArea Der Bereich in PDF-Koordinaten.
     * @return Das Rechteck in Swing-Pixelkoordinaten oder null bei Fehlern.
     */
    private Rectangle transformPdfAreaToSwingPixels(AreaDefinition pdfArea) {
        if (currentDocument == null || currentPageNumber < 0 || pdfArea == null || pageImage == null) return null;

        try {
            PDPage page = currentDocument.getPage(currentPageNumber);
            PDRectangle box = page.getCropBox();
            if (box == null) box = page.getMediaBox();
            if (box == null) return null;

            float pageHeightPdfPoints = box.getHeight();

            // PDF-Koordinaten des Bereichs (absolut, Ursprung unten links)
            float pdfX1 = pdfArea.getX1();
            float pdfY1_bottom = pdfArea.getY1(); // Untere Y-Koordinate
            float pdfWidth = pdfArea.getWidth();
            float pdfHeight = pdfArea.getHeight();
            float pdfY2_top = pdfY1_bottom + pdfHeight; // Obere Y-Koordinate im PDF-System

            // Mache Koordinaten relativ zum Ursprung der Box (oft nicht 0,0)
            float pdfX1_relative = pdfX1 - box.getLowerLeftX();
            float pdfY2_relative_from_bottom = pdfY2_top - box.getLowerLeftY(); // Obere Y relativ zu unten links der Box

            // Transformiere obere Y-Koordinate zu "Abstand von oben" (relativ zur Box)
            float pdfY_relative_from_top = pageHeightPdfPoints - pdfY2_relative_from_bottom;

            // Skaliere zu Pixeln mit der aktuellen Render-Skalierung
            // Runde auf ganze Pixel für die Darstellung
            int swingX = Math.round(pdfX1_relative * currentRenderScale);
            int swingY = Math.round(pdfY_relative_from_top * currentRenderScale); // Obere linke Ecke Y in Pixeln
            int swingWidth = Math.round(pdfWidth * currentRenderScale);
            int swingHeight = Math.round(pdfHeight * currentRenderScale);

            // TODO: Seitenrotation muss hier berücksichtigt werden!

            return new Rectangle(swingX, swingY, swingWidth, swingHeight);

        } catch (Exception e) {
            log.error("Fehler bei PDF -> Swing Koordinatentransformation: {}", e.getMessage());
            return null;
        }
    }

    // --- Innerer Mouse Listener für Bereichsauswahl ---
    private class AreaMouseListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            // Beginne nur mit dem Ziehen, wenn ein Bild angezeigt wird und das Panel aktiv ist
            if (pageImage == null || !isEnabled()) return;
            dragStartPointPixels = e.getPoint(); // Merke Startpunkt in Pixeln
            dragEndPointPixels = dragStartPointPixels;   // Endpunkt initial auf Startpunkt
            isDragging = true; // Markiere, dass gezogen wird
            log.trace("MousePressed bei Pixel: {}", dragStartPointPixels);
            repaint(); // Zeichne neu (könnte nützlich sein, um alte Auswahl zu löschen)
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            // Aktualisiere nur, wenn gerade gezogen wird
            if (!isDragging || pageImage == null || !isEnabled()) return;
            dragEndPointPixels = e.getPoint(); // Aktualisiere Endpunkt in Pixeln
            repaint(); // Zeichne Panel neu, um das rote Auswahlrechteck anzuzeigen
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            // Beende nur, wenn gerade gezogen wurde
            if (!isDragging || dragStartPointPixels == null || pageImage == null || !isEnabled()) {
                // Setze isDragging zurück, auch wenn nichts hinzugefügt wird
                isDragging = false;
                repaint(); // Um ggf. rotes Rechteck zu entfernen
                return;
            }
            isDragging = false; // Ziehen ist beendet
            dragEndPointPixels = e.getPoint(); // Finaler Endpunkt in Pixeln
            log.trace("MouseReleased bei Pixel: {}", dragEndPointPixels);

            // Berechne das finale Auswahlrechteck in Pixeln
            int x = Math.min(dragStartPointPixels.x, dragEndPointPixels.x);
            int y = Math.min(dragStartPointPixels.y, dragEndPointPixels.y);
            int width = Math.abs(dragStartPointPixels.x - dragEndPointPixels.x);
            int height = Math.abs(dragStartPointPixels.y - dragEndPointPixels.y);
            Rectangle selectionPx = new Rectangle(x, y, width, height);

            // Verhindere zu kleine oder invalide Rechtecke
            if (selectionPx.width < 5 || selectionPx.height < 5) {
                log.debug("Auswahlrechteck zu klein, ignoriert.");
            } else {
                // Transformiere die Pixel-Eckpunkte in PDF-Koordinaten
                Point2D.Float pdfP1 = transformSwingPixelsToPdfPoints(dragStartPointPixels);
                Point2D.Float pdfP2 = transformSwingPixelsToPdfPoints(dragEndPointPixels);

                // Nur fortfahren, wenn Transformation erfolgreich war
                if (pdfP1 != null && pdfP2 != null) {
                    // Erstelle das AreaDefinition-Objekt (Konstruktor sortiert Ecken)
                    AreaDefinition newArea = new AreaDefinition(pdfP1.x, pdfP1.y, pdfP2.x, pdfP2.y);
                    log.info("Neuen Bereich hinzugefügt (PDF Koordinaten): {}", newArea);

                    // Erstelle eine Kopie der alten Liste *bevor* der neue Bereich hinzugefügt wird
                    List<AreaDefinition> oldAreas = new ArrayList<>(areasOnPage);

                    // Füge den neuen Bereich zur Liste für die aktuelle Seite hinzu
                    areasOnPage.add(newArea);

                    // Feuere ein Event, um den Dialog (z.B. die JList) zu benachrichtigen
                    // Übergibt die (aktualisierte) Liste der Bereiche für DIESE Seite
                    //firePropertyChange("areasChanged", null, getAreas());

                    firePropertyChange("areasChanged", oldAreas, new ArrayList<>(areasOnPage)); // Geänderte Liste übergeben
                } else {
                    // Fehler bei der Transformation
                    log.error("Konnte Auswahl nicht in PDF-Koordinaten umwandeln.");
                    JOptionPane.showMessageDialog(PdfAreaSelectorPanel.this,
                            "Fehler bei der Koordinatentransformation.\nIst das PDF korrekt formatiert oder gedreht?",
                            "Transformationsfehler", JOptionPane.WARNING_MESSAGE);
                }
            }
            // Setze Zieh-Variablen zurück für die nächste Auswahl
            dragStartPointPixels = null;
            dragEndPointPixels = null;
            repaint(); // Zeichne Panel neu (ohne rotes Rechteck, aber mit neuem blauen Bereich)
        }
    }
}