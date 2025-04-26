package de.anton.invoice.checker.invoice_checker.model;

// Jackson Imports für JSON-Verarbeitung und Fehlerbehandlung
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // Für Java Date/Time Typen

// Logging Imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Java IO und NIO Imports für Dateizugriff und Prozesssteuerung
import java.io.BufferedReader;
// import java.io.File; // Nicht mehr benötigt für PATH-Manipulation
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets; // Für explizite Zeichensatzangabe
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Java Util Imports
import java.util.ArrayList; // Für Kommandozeilenliste
import java.util.List;    // Für Kommandozeilenliste
import java.util.Map;     // Für Parameterübergabe

// Java Concurrency Imports für Prozess-Timeout und Atomare Referenz
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
* Diese Klasse ist verantwortlich für das Aufrufen des externen Python-Skripts
* zur Extraktion von Tabellen aus PDF-Dateien und das Parsen der Ergebnisse.
* Sie liest stdout und stderr des Python-Prozesses getrennt und akzeptiert
* Parameter zur Steuerung von Camelot, einschließlich optionaler Bereiche und Seitenauswahl.
*/
public class ExtraktionsService {

 private static final Logger log = LoggerFactory.getLogger(ExtraktionsService.class);
 private final ObjectMapper objectMapper; // Zum Parsen der JSON-Antwort des Python-Skripts
 private final String pythonAusfuehrbar; // Der Befehl oder Pfad zum Python-Interpreter
 private final String skriptPfad;       // Der Pfad zum Python-Extraktionsskript

 /**
  * Konstruktor für den ExtraktionsService.
  * Initialisiert den Python-Pfad, den Skript-Pfad und den ObjectMapper.
  */
 public ExtraktionsService() {
     // --- Konfiguration: Python-Interpreter festlegen ---
     // TODO: Pfad konfigurierbar machen!
     this.pythonAusfuehrbar = "C:\\Python\\Python3\\python.exe"; // Beispielpfad, bitte anpassen!
     log.info("Verwende Python-Interpreter (festgelegt): {}", this.pythonAusfuehrbar);

     // --- Konfiguration: Pfad zum Python-Skript finden ---
     String zielSkriptPfad = Paths.get("target", "scripts", "tabellen_extraktor.py").toString();
     if (!Files.exists(Paths.get(zielSkriptPfad))) {
         String ideSkriptPfad = Paths.get("scripts", "tabellen_extraktor.py").toString();
         if (Files.exists(Paths.get(ideSkriptPfad))) {
             this.skriptPfad = ideSkriptPfad;
         } else {
             log.error("Python-Skript nicht gefunden unter {} oder {}",
                     Paths.get(zielSkriptPfad).toAbsolutePath(),
                     Paths.get(ideSkriptPfad).toAbsolutePath());
             this.skriptPfad = zielSkriptPfad; // Setzen, um NPE zu vermeiden, aber es wird fehlschlagen
         }
     } else {
         this.skriptPfad = zielSkriptPfad;
     }
     // Stelle sicher, dass skriptPfad nicht null ist (wichtig für Log)
     if (this.skriptPfad != null) {
          log.info("Verwende Python-Skript: {}", Paths.get(this.skriptPfad).toAbsolutePath());
     } else {
          log.error("SkriptPfad konnte nicht initialisiert werden!");
          // Hier wäre evtl. eine Exception sinnvoll, um den Start zu verhindern
     }


     // --- Konfiguration: ObjectMapper initialisieren ---
     this.objectMapper = new ObjectMapper()
             .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) // Tolerant gegenüber neuen JSON-Feldern
             .registerModule(new JavaTimeModule()); // Unterstützung für Java 8+ Datum/Zeit
 }

 /**
  * Führt das Python-Skript mit spezifischen Parametern, optionalen Bereichen und Seitenauswahl aus,
  * um Tabellen aus der angegebenen PDF-Datei zu extrahieren.
  * Liest stdout (für JSON) und stderr (für Warnungen/Fehler) getrennt.
  *
  * @param pdfPfad Der Pfad zur PDF-Datei, die verarbeitet werden soll. Darf nicht null sein.
  * @param parameter Eine Map mit Parametern für das Python-Skript (z.B. "flavor", "row_tol").
  *                  Werte sollten Strings sein. Kann null sein.
  * @param tableAreas Eine Liste von Bereichsdefinitionen im Format "x1,y1,x2,y2" oder null.
  * @param pageString Die Seitenzahl(en), die verarbeitet werden sollen (z.B. "1", "1,3", "all"). Darf nicht null/leer sein.
  * @return Ein PdfDokument-Objekt, das die extrahierten Daten oder eine Fehlermeldung enthält. Gibt nie null zurück.
  */
 public PdfDokument extrahiereTabellenAusPdf(Path pdfPfad, Map<String, String> parameter, List<String> tableAreas, String pageString) {
     // Erstelle initiales Ergebnisobjekt (wird auch im Fehlerfall zurückgegeben)
     PdfDokument ergebnisDokument = new PdfDokument();
     if (pdfPfad != null) {
          ergebnisDokument.setSourcePdf(pdfPfad.getFileName().toString());
          ergebnisDokument.setFullPath(pdfPfad.toString());
     } else {
          log.error("PDF-Pfad ist null, Extraktion nicht möglich.");
          ergebnisDokument.setError("Interner Fehler: Kein PDF-Pfad übergeben.");
          return ergebnisDokument;
     }

     log.info("Starte Extraktion für: {} mit Parametern: {}, Bereichen: {}, Seite(n): {}", pdfPfad, parameter, tableAreas, pageString);

     // --- Vorprüfungen ---
     if (this.skriptPfad == null || !Files.exists(Paths.get(this.skriptPfad))) {
         log.error("Python-Skript nicht gefunden oder Pfad nicht initialisiert: {}", this.skriptPfad);
         ergebnisDokument.setError("Konfigurationsfehler: Python-Skript nicht gefunden.");
         return ergebnisDokument;
     }
     if (!Files.exists(pdfPfad)) {
         log.error("Eingabe-PDF nicht gefunden unter: {}", pdfPfad.toAbsolutePath());
         ergebnisDokument.setError("Eingabe-PDF-Datei nicht gefunden.");
         return ergebnisDokument;
     }
     if (pageString == null || pageString.isBlank()) {
         log.error("Ungültige Seitenspezifikation (null oder leer) erhalten. Verwende 'all'.");
         pageString = "all"; // Fallback auf 'all'
     }

     // --- Kommandozeile dynamisch aufbauen ---
     List<String> command = new ArrayList<>();
     command.add(pythonAusfuehrbar); // Python-Interpreter
     command.add(skriptPfad);        // Das auszuführende Skript
     command.add("--pdf-path");      // Benanntes Argument für den PDF-Pfad
     command.add(pdfPfad.toAbsolutePath().toString()); // Der Wert für --pdf-path

     // Füge Camelot-Parameter hinzu
     if (parameter != null) {
         String flavor = parameter.getOrDefault("flavor", "lattice"); // Default ist lattice
         command.add("--flavor");
         command.add(flavor);

         // Füge row_tol NUR hinzu, wenn der Flavor 'stream' ist
         if ("stream".equalsIgnoreCase(flavor)) {
             String rowTol = parameter.get("row_tol");
             if (rowTol != null && !rowTol.isBlank()) {
                 try {
                     Integer.parseInt(rowTol); // Kleine Validierung
                     command.add("--row-tol");
                     command.add(rowTol);
                     log.debug("--> Füge --row-tol {} für stream-Flavor hinzu.", rowTol);
                 } catch (NumberFormatException nfe) {
                     log.warn("Ungültiger Wert für row_tol '{}' angegeben, wird ignoriert.", rowTol);
                 }
             }
         } else if (parameter.containsKey("row_tol") && parameter.get("row_tol") != null && !parameter.get("row_tol").isBlank()){
             // Logge Warnung, wenn row_tol für nicht-stream angegeben wurde
             log.warn("Parameter 'row_tol' ('{}') wird für flavor '{}' ignoriert.", parameter.get("row_tol"), flavor);
         }
         // TODO: Hier weitere optionale Parameter (col_tol, edge_tol etc.) hinzufügen
     } else {
          // Fallback, wenn keine Parameter übergeben wurden
          command.add("--flavor");
          command.add("lattice"); // Default Flavor
     }

     // Füge die Seitenspezifikation hinzu (korrigierter Teil)
     command.add("--page");
     command.add(pageString); // Nutze den übergebenen Wert

     // Füge die Tabellenbereiche hinzu, wenn vorhanden
     if (tableAreas != null && !tableAreas.isEmpty()) {
         command.add("--table-areas");
         command.addAll(tableAreas); // Fügt alle Strings aus der Liste als separate Argumente hinzu
         log.debug("--> Füge --table-areas hinzu: {}", tableAreas);
     }
     // --- Ende Kommandozeilenaufbau ---

     log.debug("Ausführendes Kommando: {}", command);
     ProcessBuilder processBuilder = new ProcessBuilder(command); // Verwende die Command-Liste

     // Fehlerstrom NICHT umleiten, um stdout und stderr getrennt zu lesen
     // processBuilder.redirectErrorStream(true); // AUSKOMMENTIERT/ENTFERNT

     // --- Prozessausführung und Stream-Handling ---
     StringBuilder processStdOutput = new StringBuilder(); // Sammelt stdout (erwartetes JSON)
     AtomicReference<String> firstStderrLine = new AtomicReference<>(null); // Speichert die erste Zeile von stderr als Hinweis

     Process process = null; // Deklariere außerhalb des try für finally-Block
     Thread stderrReaderThread = null; // Deklariere außerhalb des try für finally-Block

     try {
         // Starte den Python-Prozess
         process = processBuilder.start();

         // Erstelle eine finale Referenz auf den Prozess für den Lambda-Ausdruck des stderr-Readers
         final Process finalProcess = process;

         // --- Separater Thread zum Lesen von stderr ---
         stderrReaderThread = new Thread(() -> {
             try (BufferedReader stderrReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                 String line;
                 while ((line = stderrReader.readLine()) != null) {
                     log.warn("Python stderr: {}", line); // Logge alle stderr-Zeilen als Warnung
                     firstStderrLine.compareAndSet(null, line.trim()); // Speichere die erste Zeile
                 }
             } catch (IOException e) {
                 // Fehler beim Lesen selbst loggen
                 log.error("Fehler beim Lesen von Python stderr: {}", e.getMessage());
             } catch (Exception e) {
                 // Unerwartete Fehler im Thread fangen
                  log.error("Unerwarteter Fehler im stderr-Reader-Thread: {}", e.getMessage(), e);
             }
         });
         stderrReaderThread.start(); // Starte den Thread

         // --- Lese stdout (hier erwarten wir das JSON) ---
         try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
             String line;
             while ((line = stdoutReader.readLine()) != null) {
                 processStdOutput.append(line).append(System.lineSeparator());
             }
         }

         // --- Warte auf Prozessende und stderr-Leser ---
         boolean finished = process.waitFor(90, TimeUnit.SECONDS); // Warte bis zu 90 Sekunden auf den Python-Prozess
         if (stderrReaderThread != null) {
              try {
                   stderrReaderThread.join(2000); // Warte bis zu 2 Sekunden auf den stderr-Leser-Thread
              } catch (InterruptedException e) {
                   Thread.currentThread().interrupt(); // Setze Interrupt-Status wieder
                   log.warn("Warten auf stderr-Reader-Thread wurde unterbrochen.");
              }
         }

         int exitCode = finished ? process.exitValue() : -1; // -1 bei Timeout

         // --- Prozess-Ergebnis prüfen ---
         if (exitCode != 0 || !finished) {
             log.error("Ausführung des Python-Skripts fehlgeschlagen. Exit-Code: {}, Timeout: {}", exitCode, !finished);
             // Logge Ende von stdout und erste Zeile von stderr für Debugging
             log.error("Skript stdout (letzte Zeilen):\n{}", processStdOutput.substring(Math.max(0, processStdOutput.length() - 1000)));
             log.error("Skript erste stderr Zeile: {}", firstStderrLine.get());
             // Setze Fehlermeldung im Rückgabeobjekt
             ergebnisDokument.setError("Python-Skript fehlgeschlagen (Exit-Code: " + exitCode + "). " + (firstStderrLine.get() != null ? firstStderrLine.get() : "Details siehe Log."));
             return ergebnisDokument; // Gib das Fehlerobjekt zurück
         }

         // Prozess war erfolgreich (Exit-Code 0)
         log.debug("Python-Skript stdout:\n{}", processStdOutput); // Logge die reine stdout-Ausgabe

         // --- JSON-Verarbeitung ---
         String jsonString = processStdOutput.toString().trim(); // Bereinige die stdout-Ausgabe
         if (jsonString.isEmpty()) {
             log.error("Python-Skript lieferte leere stdout-Ausgabe trotz Exit-Code 0.");
             log.error("Skript erste stderr Zeile: {}", firstStderrLine.get()); // Prüfe stderr für Hinweise
             ergebnisDokument.setError("Python-Skript lieferte leere Ausgabe." + (firstStderrLine.get() != null ? " Möglicher Hinweis: " + firstStderrLine.get() : ""));
             return ergebnisDokument;
         }

         // Versuche, die JSON-Zeichenkette (aus stdout) in ein PdfDokument-Objekt zu parsen
         // Der Konstruktor von PdfDokument sollte die interne Liste initialisieren
         PdfDokument doc = objectMapper.readValue(jsonString, PdfDokument.class);
         log.info("Daten erfolgreich extrahiert und geparst für: {}", pdfPfad.getFileName());

         // Prüfe, ob das Python-Skript selbst einen Fehler im JSON-Objekt gemeldet hat
         if (doc.getError() != null && !doc.getError().isBlank()) {
             log.warn("Python-Skript meldete einen internen Fehler im JSON für {}: {}", pdfPfad.getFileName(), doc.getError());
             // Wir geben das Dokument trotzdem zurück, der Fehler steht im Objekt.
         }
         return doc; // Erfolgreich geparst, gib das Ergebnis zurück

     } catch (InterruptedException e) {
         // Wird ausgelöst, wenn das Warten auf den Prozess oder den stderr-Thread unterbrochen wird
         Thread.currentThread().interrupt(); // Setze den Interrupt-Status für den aufrufenden Code
         log.error("Warten auf Python-Prozess/stderr unterbrochen: {}", e.getMessage(), e);
         ergebnisDokument.setError("Java Fehler: Warten auf Python-Prozess/stderr unterbrochen.");
         return ergebnisDokument;
     } catch (JsonParseException | JsonMappingException e) {
         // Wird ausgelöst, wenn Jacksons ObjectMapper die stdout-Ausgabe nicht parsen/mappen kann
         log.error("Fehler beim Parsen der JSON-Ausgabe vom Python-Skript (stdout): {}", e.getMessage());
         log.error("Empfangener String vom Skript (stdout):\n{}", processStdOutput);
         // Versuche, eine nützliche Fehlermeldung zu generieren, nutze ggf. stderr Hinweis
         String errorMsg = "Java Fehler: Ungültige JSON-Ausgabe vom Python-Skript ";
         if(firstStderrLine.get() != null) {
             errorMsg += "(stderr Hinweis: '" + firstStderrLine.get() + "').";
         } else {
             errorMsg += ": " + e.getOriginalMessage();
         }
         ergebnisDokument.setError(errorMsg);
         return ergebnisDokument;
     } catch (IOException e) {
         // Fängt andere IOExceptions ab (z.B. von process.start(), InputStream/ErrorStream lesen)
         log.error("I/O Fehler beim Ausführen oder Lesen vom Python-Skript: {}", e.getMessage(), e);
         ergebnisDokument.setError("Java I/O Fehler: Konnte Python-Skript nicht ausführen/lesen: " + e.getMessage());
         return ergebnisDokument;
     } finally {
          // --- Aufräumen im finally-Block ---
          // Stellen Sie sicher, dass der externe Prozess zerstört wird, falls er noch läuft
          // (z.B. bei einer Exception vor process.waitFor() oder Timeout)
          if (process != null && process.isAlive()) {
              log.warn("Zerstöre noch laufenden Python-Prozess.");
              process.destroy(); // Versucht, den Prozess normal zu beenden
              // Optional: Warte kurz und erzwinge dann das Beenden
              try {
                   if (!process.waitFor(1, TimeUnit.SECONDS)) { // Warte 1 Sekunde
                        log.warn("Prozess reagierte nicht auf destroy(), versuche destroyForcibly().");
                        process.destroyForcibly(); // Erzwingt das Beenden
                   }
              } catch (InterruptedException ex) {
                   Thread.currentThread().interrupt(); // Interrupt-Status wiederherstellen
                   process.destroyForcibly(); // Trotzdem versuchen zu beenden
              }
          }
          // Stellen Sie sicher, dass der stderr-Leserthread beendet ist
          if (stderrReaderThread != null && stderrReaderThread.isAlive()) {
              log.warn("Unterbreche noch laufenden stderr-Reader-Thread.");
              stderrReaderThread.interrupt(); // Sende Interrupt-Signal
          }
     }
 }

  /**
   * Überladene Methode für Kompatibilität und einfachere Aufrufe ohne explizite
   * Bereichs- und Seitenangabe. Ruft die Hauptmethode mit Standardwerten auf.
   *
   * @param pdfPfad Der Pfad zur PDF-Datei.
   * @param parameter Eine Map mit Parametern (z.B. "flavor", "row_tol").
   * @return Ein PdfDokument-Objekt.
   */
  public PdfDokument extrahiereTabellenAusPdf(Path pdfPfad, Map<String, String> parameter) {
       // Ruft die Hauptmethode ohne Bereiche (null) und mit Seitenspezifikation 'all' auf
       return extrahiereTabellenAusPdf(pdfPfad, parameter, null, "all");
  }
}