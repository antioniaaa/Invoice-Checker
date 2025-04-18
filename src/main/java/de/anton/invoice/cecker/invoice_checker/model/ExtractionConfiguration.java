package de.anton.invoice.cecker.invoice_checker.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Speichert eine benannte Konfiguration für die Tabellenextraktion,
 * einschließlich globaler und seitenspezifischer Bereiche.
 */
public class ExtractionConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name = "Standard"; // Name der Konfiguration
    private boolean usePageSpecificAreas = true; // Steuert, ob seitenspezifisch oder global gilt
    private List<AreaDefinition> globalAreas = new ArrayList<>(); // Bereiche, die auf jeder Seite gelten (wenn !usePageSpecificAreas)
    private Map<Integer, List<AreaDefinition>> pageSpecificAreas = new HashMap<>(); // Seite -> Liste von Bereichen

    // Standardkonstruktor
    public ExtractionConfiguration() {}

    public ExtractionConfiguration(String name) {
        this.name = name;
    }

    /**
     * Gibt die für Camelot relevanten Bereichsdefinitionen für eine bestimmte Seite zurück.
     * Berücksichtigt, ob globale oder seitenspezifische Bereiche aktiv sind.
     *
     * @param pageNumber Die 1-basierte Seitenzahl.
     * @return Eine Liste von Bereichs-Strings im Format "x1,y1,x2,y2" oder null, wenn keine Bereiche gelten.
     */
    public List<String> getAreasForCamelot(int pageNumber) {
        List<AreaDefinition> areasToUse;
        if (usePageSpecificAreas) {
            areasToUse = pageSpecificAreas.getOrDefault(pageNumber, Collections.emptyList());
            // Wenn keine spezifischen für die Seite, aber globale vorhanden sind -> NICHT globale nehmen! (explizit seitenspezifisch)
        } else {
            areasToUse = globalAreas; // Globale Bereiche für alle Seiten verwenden
        }

        if (areasToUse == null || areasToUse.isEmpty()) {
            return null; // Keine Bereiche für diese Seite/Modus definiert
        }

        // Konvertiere AreaDefinition-Objekte in Strings
        return areasToUse.stream()
                .map(AreaDefinition::toCamelotString)
                .collect(Collectors.toList());
    }

    // --- Methoden zum Verwalten der Bereiche ---

    public void addPageSpecificArea(int pageNumber, AreaDefinition area) {
        pageSpecificAreas.computeIfAbsent(pageNumber, k -> new ArrayList<>()).add(area);
    }
    public void removePageSpecificArea(int pageNumber, AreaDefinition area) {
        pageSpecificAreas.computeIfPresent(pageNumber, (k, v) -> {
            v.remove(area);
            return v.isEmpty() ? null : v; // Entferne Liste, wenn leer
        });
    }
    public void setPageSpecificAreas(int pageNumber, List<AreaDefinition> areas) {
        if (areas == null || areas.isEmpty()) {
            pageSpecificAreas.remove(pageNumber);
        } else {
            pageSpecificAreas.put(pageNumber, new ArrayList<>(areas)); // Kopie speichern
        }
    }
    public List<AreaDefinition> getPageSpecificAreas(int pageNumber) {
        return new ArrayList<>(pageSpecificAreas.getOrDefault(pageNumber, Collections.emptyList())); // Kopie zurückgeben
    }

    public void addGlobalArea(AreaDefinition area) {
        if (globalAreas == null) globalAreas = new ArrayList<>();
        globalAreas.add(area);
    }
    public void removeGlobalArea(AreaDefinition area) {
        if (globalAreas != null) globalAreas.remove(area);
    }
    public List<AreaDefinition> getGlobalAreas() {
         if (globalAreas == null) globalAreas = new ArrayList<>();
        return new ArrayList<>(globalAreas); // Kopie zurückgeben
    }
    public void setGlobalAreas(List<AreaDefinition> areas) {
         if (areas == null) {
              this.globalAreas = new ArrayList<>();
         } else {
              this.globalAreas = new ArrayList<>(areas); // Kopie speichern
         }
    }


    // --- Getter/Setter für Konfigurationsattribute ---
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isUsePageSpecificAreas() { return usePageSpecificAreas; }
    public void setUsePageSpecificAreas(boolean usePageSpecificAreas) { this.usePageSpecificAreas = usePageSpecificAreas; }
    // Getter/Setter für die Maps/Listen (wichtig für Jackson)
    public Map<Integer, List<AreaDefinition>> getPageSpecificAreasMap() { return pageSpecificAreas; }
    public void setPageSpecificAreasMap(Map<Integer, List<AreaDefinition>> pageSpecificAreas) { this.pageSpecificAreas = pageSpecificAreas; }
    public List<AreaDefinition> getGlobalAreasList() { return globalAreas; } // Eindeutiger Name für Jackson
    public void setGlobalAreasList(List<AreaDefinition> globalAreas) { this.globalAreas = globalAreas; }


    @Override
    public String toString() {
        // Wird in der ComboBox angezeigt
        return name;
    }

     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractionConfiguration that = (ExtractionConfiguration) o;
        return Objects.equals(name, that.name); // Eindeutigkeit über Namen
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
