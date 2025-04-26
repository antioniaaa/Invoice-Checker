package de.anton.invoice.checker.invoice_checker.model;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook; // Für .xlsx Format
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter; // Für Datumsformatierung
import java.util.List; // Für die Liste der Dokumente

/**
 * Diese Klasse ist verantwortlich für den Export der extrahierten Tabellendaten
 * aus mehreren PdfDokument-Objekten in eine einzelne Excel-Datei (.xlsx).
 */
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);
    // Definiere ein konsistentes Datumsformat für die Ausgabe in Excel
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Exportiert die Daten aller Tabellen aus der Liste der PdfDokumente in eine Excel-Datei.
     * Jede Zeile einer extrahierten Tabelle wird zu einer Zeile in der Excel-Datei,
     * ergänzt um Metadaten wie Quelldatei, Datum, Seite etc.
     *
     * @param dokumente Die Liste der zu exportierenden PdfDokument-Objekte.
     * @param ausgabePfad Der Pfad zur zu erstellenden Excel-Datei.
     * @throws IOException Wenn ein Fehler beim Schreiben der Datei auftritt oder keine Dokumente vorhanden sind.
     */
    public void exportiereNachExcel(List<PdfDokument> dokumente, Path ausgabePfad) throws IOException {
        log.info("Starte Excel-Export von {} Dokumenten nach: {}", (dokumente != null ? dokumente.size() : 0), ausgabePfad);

        // Prüfe, ob überhaupt Daten zum Exportieren vorhanden sind
        if (dokumente == null || dokumente.isEmpty()) {
            log.warn("Keine Dokumente zum Exportieren vorhanden.");
            // Werfe einen Fehler oder beende die Methode, da keine Datei erstellt werden kann.
            throw new IOException("Keine Dokumente zum Exportieren angegeben.");
            // return; // Alternative: Einfach nichts tun und keine Datei erstellen.
        }

        // Verwende try-with-resources, um sicherzustellen, dass Workbook und FileOutputStream geschlossen werden
        try (Workbook workbook = new XSSFWorkbook(); // Erstelle eine neue .xlsx Arbeitsmappe
             FileOutputStream fileOut = new FileOutputStream(ausgabePfad.toFile())) { // Öffne den Ausgabestream

            // Erstelle ein neues Arbeitsblatt
            Sheet sheet = workbook.createSheet("Extrahierte Tabellen");

            // Index für die aktuelle Zeile im Excel-Blatt
            int rowIndex = 0;
            // Definiere Zellstile für Header und Datum
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            // --- Header-Zeile erstellen ---
            Row headerRow = sheet.createRow(rowIndex++);
            int cellIndex = 0;
            // Statische Metadaten-Spalten
            createCell(headerRow, cellIndex++, "Quelldatei", headerStyle);
            createCell(headerRow, cellIndex++, "Abrechnungszeitraum Start", headerStyle);
            createCell(headerRow, cellIndex++, "Abrechnungszeitraum Ende", headerStyle);
            createCell(headerRow, cellIndex++, "Seite (PDF)", headerStyle);
            createCell(headerRow, cellIndex++, "Tabellenindex (Camelot)", headerStyle);
            createCell(headerRow, cellIndex++, "Flavor (Camelot)", headerStyle); // Camelot Flavor hinzugefügt
            createCell(headerRow, cellIndex++, "Zeilenindex (Original)", headerStyle); // Index der Zeile innerhalb der Originaltabelle

            // Finde die maximale Anzahl von Datenspalten über alle Tabellen hinweg
            int basisSpaltenAnzahl = cellIndex; // Anzahl der Metadaten-Spalten
            int maxDatenSpalten = 0;
            for (PdfDokument doc : dokumente) {
                 if(doc.getTables() != null){
                      for(ExtrahierteTabelle tabelle : doc.getTables()){
                           if(tabelle.getData() != null){
                                for(List<String> rowData : tabelle.getData()){
                                     // Vergleiche die Größe jeder Zeile (inkl. Header der Originaltabelle)
                                     maxDatenSpalten = Math.max(maxDatenSpalten, rowData.size());
                                }
                           }
                      }
                 }
            }
            log.debug("Maximale Anzahl an Datenspalten gefunden: {}", maxDatenSpalten);

            // Füge Header für die dynamischen Datenspalten hinzu
            for (int i = 0; i < maxDatenSpalten; i++) {
                createCell(headerRow, cellIndex++, "Daten Spalte " + (i + 1), headerStyle);
            }


            // --- Datenzeilen füllen ---
            // Iteriere durch jedes verarbeitete PDF-Dokument
            for (PdfDokument doc : dokumente) {
                // Überspringe Dokumente ohne Tabellen oder mit Verarbeitungsfehler (optional)
                if (doc.getTables() == null || doc.getTables().isEmpty()) {
                     log.warn("Überspringe Dokument '{}' beim Export, da keine Tabellen gefunden wurden oder ein Fehler aufgetreten ist.", doc.getSourcePdf());
                     // Optional: Eine Zeile mit der Fehlermeldung schreiben
                     if (doc.getError() != null && !doc.getError().isBlank()) {
                          Row errorRow = sheet.createRow(rowIndex++);
                          createCell(errorRow, 0, doc.getSourcePdf(), null);
                          createCell(errorRow, basisSpaltenAnzahl, "FEHLER: " + doc.getError(), null); // Fehler in erster Datenspalte
                     }
                    continue; // Nächstes Dokument
                }

                // Iteriere durch jede extrahierte Tabelle in diesem Dokument
                for (ExtrahierteTabelle tabelle : doc.getTables()) {
                    // Überspringe Tabellen ohne Daten
                    if (tabelle.getData() == null || tabelle.getData().isEmpty()) {
                        continue; // Nächste Tabelle
                    }

                    // Iteriere durch jede Zeile der Originaltabelle (inklusive des Original-Headers)
                    for (int originalRowIndex = 0; originalRowIndex < tabelle.getData().size(); originalRowIndex++) {
                         List<String> rowData = tabelle.getData().get(originalRowIndex); // Die Daten der aktuellen Zeile
                         Row dataRow = sheet.createRow(rowIndex++); // Neue Zeile im Excel-Blatt erstellen
                         cellIndex = 0; // Spaltenindex zurücksetzen

                         // Schreibe die Metadaten in die ersten Spalten
                         createCell(dataRow, cellIndex++, doc.getSourcePdf(), null);
                         createCell(dataRow, cellIndex++, doc.getAbrechnungszeitraumStart() != null ? doc.getAbrechnungszeitraumStart().format(DATE_FORMATTER) : "", dateStyle);
                         createCell(dataRow, cellIndex++, doc.getAbrechnungszeitraumEnde() != null ? doc.getAbrechnungszeitraumEnde().format(DATE_FORMATTER) : "", dateStyle);
                         createCell(dataRow, cellIndex++, String.valueOf(tabelle.getPage()), null); // Seite als String
                         createCell(dataRow, cellIndex++, String.valueOf(tabelle.getIndex()), null); // Index als String
                         createCell(dataRow, cellIndex++, tabelle.getFlavor(), null); // Flavor (lattice/stream)
                         createCell(dataRow, cellIndex++, String.valueOf(originalRowIndex), null); // Original-Zeilenindex als String

                         // Schreibe die eigentlichen Tabellendaten in die nachfolgenden Spalten
                         for (int dataColIndex = 0; dataColIndex < rowData.size(); dataColIndex++) {
                             // Stelle sicher, dass der Index gültig ist (sollte durch maxDatenSpalten abgedeckt sein)
                             if (cellIndex < basisSpaltenAnzahl + maxDatenSpalten) {
                                createCell(dataRow, cellIndex++, rowData.get(dataColIndex), null);
                             } else {
                                 log.warn("Zu viele Datenzellen in Zeile {} von Tabelle {}({}) in Datei '{}'. Überspringe Zelle '{}'.",
                                           originalRowIndex, tabelle.getIndex(), tabelle.getFlavor(), doc.getSourcePdf(), rowData.get(dataColIndex));
                             }
                         }
                         // Fülle ggf. restliche Datenspalten mit Leerstrings, wenn diese Zeile weniger Spalten hatte
                         while (cellIndex < basisSpaltenAnzahl + maxDatenSpalten) {
                             createCell(dataRow, cellIndex++, "", null);
                         }
                    }
                }
            }

             // Spaltenbreiten automatisch anpassen (optional, kann bei sehr großen Dateien dauern)
             log.debug("Passe Spaltenbreiten an...");
             for (int i = 0; i < basisSpaltenAnzahl + maxDatenSpalten; i++) {
                try {
                    sheet.autoSizeColumn(i);
                } catch (Exception e) {
                    // Fange potenzielle Fehler bei autoSizeColumn ab (z.B. bei sehr breiten Zellen)
                    log.warn("Fehler beim Anpassen der Spaltenbreite für Spalte {}: {}", i, e.getMessage());
                    // Setze eine Standardbreite als Fallback
                    // sheet.setColumnWidth(i, 20 * 256); // Beispiel: 20 Zeichen breit
                }
             }

            // Schreibe die gesamte Arbeitsmappe in die Ausgabedatei
            workbook.write(fileOut);
            log.info("Excel-Export nach {} erfolgreich abgeschlossen.", ausgabePfad);

        } catch (IOException e) {
             // Fange Fehler beim Erstellen/Schreiben der Datei
             log.error("Fehler beim Schreiben der Excel-Datei nach {}: {}", ausgabePfad, e.getMessage(), e);
             throw e; // Leite den Fehler weiter, damit der Aufrufer ihn behandeln kann
        }
    }

    /**
     * Hilfsmethode zum Erstellen einer Zelle in einer Zeile mit gegebenem Wert und Stil.
     * Behandelt Null-Werte für den Wert.
     *
     * @param row Die Zeile, in der die Zelle erstellt werden soll.
     * @param columnIndex Der 0-basierte Index der Spalte.
     * @param value Der Wert, der in die Zelle geschrieben werden soll (wird in String umgewandelt).
     * @param style Der anzuwendende Zellstil (kann null sein).
     */
    private void createCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value != null ? value : ""); // Stelle sicher, dass kein Null geschrieben wird
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

     /**
      * Erstellt einen Zellstil für Header-Zellen (fett, zentriert).
      *
      * @param workbook Die Arbeitsmappe, für die der Stil erstellt wird.
      * @return Der erstellte Header-Zellstil.
      */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true); // Fett
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER); // Zentriert
        // Optional: Füllung, Rahmen etc. hinzufügen
        // style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        // style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

     /**
      * Erstellt einen Zellstil für Datumszellen im Format TT.MM.JJJJ.
      *
      * @param workbook Die Arbeitsmappe, für die der Stil erstellt wird.
      * @return Der erstellte Datums-Zellstil.
      */
    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        // Definiere das Datumsformat für Excel
        style.setDataFormat(createHelper.createDataFormat().getFormat("dd.mm.yyyy"));
        return style;
    }
}