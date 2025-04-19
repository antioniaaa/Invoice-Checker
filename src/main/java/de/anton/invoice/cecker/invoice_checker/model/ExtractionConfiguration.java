package de.anton.invoice.cecker.invoice_checker.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Jackson Annotation

/**
 * Speichert eine benannte Konfiguration für die Tabellenextraktion,
 * einschließlich Modus (global/seitenspezifisch) und der Bereichsdefinitionen.
 * Ist Serializable für die Speicherung als JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true) // Ignoriert unbekannte Felder beim Lesen
public class ExtractionConfiguration implements Serializable {
    private static final long serialVersionUID = 2L; // Version erhöht wegen Feldänderungen

    private String name = "Unbenannt"; // Name der Konfiguration
    private boolean usePageSpecificAreas = true; // Default: Seitenspezifisch
    // WICHTIG: Klare Namen für Jackson Getter/Setter verwenden!
    private List<AreaDefinition> globalAreasList = new ArrayList<>(); // Bereiche für globalen Modus
    private Map<Integer, List<AreaDefinition>> pageSpecificAreasMap = new HashMap<>(); // Seite (1-basiert) -> Liste von Bereichen

    /** Standardkonstruktor (für Jackson). */
    public ExtractionConfiguration() {}

    /** Konstruktor mit Namen. */
    public ExtractionConfiguration(String name) {
        this.name = (name != null && !name.isBlank()) ? name : "Unbenannt";
    }

    /**
     * Gibt die für Camelot relevanten Bereichsdefinitionen für eine bestimmte Seite zurück,
     * basierend auf dem eingestellten Modus (global vs. seitenspezifisch).
     *
     * @param pageNumber Die 1-basierte Seitenzahl.
     * @return Eine Liste von Bereichs-Strings im Format "x1,y1,x2,y2" oder null,
     *         wenn für diesen Modus/Seite keine Bereiche definiert sind.
     */
    public List<String> getAreasForCamelot(int pageNumber) {
        List<AreaDefinition> areasToUse;
        if (usePageSpecificAreas) {
            // Nutze nur die Bereiche, die explizit für DIESE Seite definiert sind
            areasToUse = pageSpecificAreasMap.getOrDefault(pageNumber, Collections.emptyList());
        } else {
            // Nutze die globalen Bereiche
            areasToUse = globalAreasList;
        }

        // Wenn keine relevanten Bereiche gefunden wurden, gib null zurück
        if (areasToUse == null || areasToUse.isEmpty()) {
            return null;
        }

        // Konvertiere die AreaDefinition-Objekte in das von Camelot erwartete String-Format
        return areasToUse.stream()
                .map(AreaDefinition::toCamelotString) // Ruft "x1,y1,x2,y2" ab
                .collect(Collectors.toList());
    }

    // --- Methoden zum Verwalten der seitenspezifischen Bereiche ---

    public void addPageSpecificArea(int pageNumber, AreaDefinition area) {
        if (pageNumber <= 0 || area == null) return;
        // computeIfAbsent stellt sicher, dass eine Liste für die Seite existiert
        pageSpecificAreasMap.computeIfAbsent(pageNumber, k -> new ArrayList<>()).add(area);
    }

    public void removePageSpecificArea(int pageNumber, AreaDefinition area) {
        if (pageNumber <= 0 || area == null) return;
        // computeIfPresent bearbeitet die Liste nur, wenn sie existiert
        pageSpecificAreasMap.computeIfPresent(pageNumber, (k, v) -> {
            v.remove(area); // Entferne das spezifische Area-Objekt
            return v.isEmpty() ? null : v; // Entferne den Map-Eintrag, wenn die Liste leer ist
        });
    }

    public void setPageSpecificAreas(int pageNumber, List<AreaDefinition> areas) {
        if (pageNumber <= 0) return;
        if (areas == null || areas.isEmpty()) {
            pageSpecificAreasMap.remove(pageNumber); // Entferne Eintrag bei leerer Liste
        } else {
            pageSpecificAreasMap.put(pageNumber, new ArrayList<>(areas)); // Speichere Kopie der Liste
        }
    }

    public List<AreaDefinition> getPageSpecificAreas(int pageNumber) {
        // Gib immer eine Kopie zurück, um externe Änderungen zu verhindern
        return new ArrayList<>(pageSpecificAreasMap.getOrDefault(pageNumber, Collections.emptyList()));
    }

    // --- Methoden zum Verwalten der globalen Bereiche ---

    public void addGlobalArea(AreaDefinition area) {
        if (area == null) return;
        if (globalAreasList == null) globalAreasList = new ArrayList<>();
        globalAreasList.add(area);
    }

    public void removeGlobalArea(AreaDefinition area) {
        if (globalAreasList != null) globalAreasList.remove(area);
    }

    public void setGlobalAreas(List<AreaDefinition> areas) {
         if (areas == null) {
              this.globalAreasList = new ArrayList<>(); // Immer eine leere Liste, nie null
         } else {
              this.globalAreasList = new ArrayList<>(areas); // Speichere Kopie
         }
    }

    // --- Getter/Setter für Konfigurationsattribute & Jackson ---
    public String getName() { return name; }
    public void setName(String name) { this.name = (name != null && !name.isBlank()) ? name : "Unbenannt"; }

    public boolean isUsePageSpecificAreas() { return usePageSpecificAreas; }
    public void setUsePageSpecificAreas(boolean usePageSpecificAreas) { this.usePageSpecificAreas = usePageSpecificAreas; }

    // Getter/Setter für Jackson Serialisierung (müssen public sein)
    public List<AreaDefinition> getGlobalAreasList() {
        return globalAreasList != null ? globalAreasList : Collections.emptyList();
    }
    public void setGlobalAreasList(List<AreaDefinition> globalAreasList) {
        this.globalAreasList = (globalAreasList != null) ? globalAreasList : new ArrayList<>();
    }

    public Map<Integer, List<AreaDefinition>> getPageSpecificAreasMap() {
        return pageSpecificAreasMap != null ? pageSpecificAreasMap : new HashMap<>();
    }
    public void setPageSpecificAreasMap(Map<Integer, List<AreaDefinition>> pageSpecificAreasMap) {
        this.pageSpecificAreasMap = (pageSpecificAreasMap != null) ? pageSpecificAreasMap : new HashMap<>();
    }


    // --- Standardmethoden ---
    @Override
    public String toString() {
        // Wird in der ComboBox angezeigt
        return name != null ? name : "Unbenannte Konfig";
    }

     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtractionConfiguration that = (ExtractionConfiguration) o;
        // Eindeutigkeit primär über Namen (Groß/Kleinschreibung ignorieren?)
        return Objects.equals(name, that.name);
        // return name != null ? name.equalsIgnoreCase(that.name) : that.name == null; // Case-insensitive Vergleich
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
        // return name != null ? name.toLowerCase().hashCode() : 0; // Case-insensitive Hashcode
    }
}