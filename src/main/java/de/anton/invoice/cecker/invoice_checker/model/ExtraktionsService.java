package de.anton.invoice.cecker.invoice_checker.model;

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
 * Parameter zur Steuerung von Camelot.
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
        // Hier wird der Pfad zum Python-Interpreter fest codiert.
        // TODO: Dies sollte idealerweise konfigurierbar sein (z.B. über eine Konfigurationsdatei).
        // Beispiel Windows (Pfad anpassen! Doppelte Backslashes verwenden!):
        this.pythonAusfuehrbar = "C:\\Python\\Python3\\python.exe"; // <- Sicherstellen, dass dies korrekt ist
        // Beispiel Linux/Mac (Pfad anpassen!):
        // this.pythonAusfuehrbar = "/usr/bin/python3"; // Oder /usr/local/bin/python3 etc.

        log.info("Verwende Python-Interpreter (festgelegt): {}", this.pythonAusfuehrbar);

        // --- Konfiguration: Pfad zum Python-Skript finden ---
        // Versucht zuerst, das Skript im 'target/scripts'-Verzeichnis zu finden (nach Maven-Build).
        // Als Fallback wird das 'scripts'-Verzeichnis im Projektstamm gesucht (nützlich beim Ausführen aus der IDE).
        String zielSkriptPfad = Paths.get("target", "scripts", "tabellen_extraktor.py").toString();
        if (!Files.exists(Paths.get(zielSkriptPfad))) {
            String ideSkriptPfad = Paths.get("scripts", "tabellen_extraktor.py").toString();
            if (Files.exists(Paths.get(ideSkriptPfad))) {
                this.skriptPfad = ideSkriptPfad;
            } else {
                log.error("Python-Skript nicht gefunden unter {} oder {}",
                        Paths.get(zielSkriptPfad).toAbsolutePath(),
                        Paths.get(ideSkriptPfad).toAbsolutePath());
                // Setze den Pfad trotzdem, um NPE zu vermeiden, aber die Extraktion wird fehlschlagen.
                this.skriptPfad = zielSkriptPfad;
            }
        } else {
            this.skriptPfad = zielSkriptPfad;
        }
        log.info("Verwende Python-Skript: {}", Paths.get(this.skriptPfad).toAbsolutePath());

        // --- Konfiguration: ObjectMapper initialisieren ---
        // Konfiguriert Jackson, um unbekannte Felder im JSON zu ignorieren und Java Date/Time zu unterstützen.
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(new JavaTimeModule());
    }

    /**
     * Führt das Python-Skript mit spezifischen Parametern aus, um Tabellen
     * aus der angegebenen PDF-Datei zu extrahieren. Liest stdout (für JSON)
     * und stderr (für Warnungen/Fehler) getrennt.
     *
     * @param pdfPfad Der Pfad zur PDF-Datei, die verarbeitet werden soll.
     * @param parameter Eine Map mit Parametern für das Python-Skript (z.B. "flavor", "row_tol").
     *                  Werte sollten Strings sein. Kann null sein.
     * @return Ein PdfDokument-Objekt, das die extrahierten Daten oder eine Fehlermeldung enthält.
     */
    public PdfDokument extrahiereTabellenAusPdf(Path pdfPfad, Map<String, String> parameter) {
        log.info("Starte Extraktion für: {} mit Parametern: {}", pdfPfad, parameter);
        // PdfDokument-Objekt für potenzielle Fehler vorbereiten
        PdfDokument fehlerDok = new PdfDokument();
        fehlerDok.setSourcePdf(pdfPfad.getFileName().toString());
        fehlerDok.setFullPath(pdfPfad.toString());

        // --- Vorprüfungen: Skript und PDF-Datei vorhanden? ---
        if (!Files.exists(Paths.get(skriptPfad))) {
            log.error("Python-Skript nicht gefunden unter: {}", Paths.get(skriptPfad).toAbsolutePath());
            fehlerDok.setError("Konfigurationsfehler: Python-Skript nicht gefunden.");
            return fehlerDok;
        }
        if (!Files.exists(pdfPfad)) {
            log.error("Eingabe-PDF nicht gefunden unter: {}", pdfPfad.toAbsolutePath());
            fehlerDok.setError("Eingabe-PDF-Datei nicht gefunden.");
            return fehlerDok;
        }

        // --- Kommandozeile dynamisch aufbauen ---
        List<String> command = new ArrayList<>();
        command.add(pythonAusfuehrbar); // Python-Interpreter
        command.add(skriptPfad);        // Das auszuführende Skript
        command.add("--pdf-path");      // Benanntes Argument für den PDF-Pfad
        command.add(pdfPfad.toAbsolutePath().toString()); // Der Wert für --pdf-path

        // Füge Camelot-Parameter hinzu, falls in der Map vorhanden
        if (parameter != null) {
            // Flavor Parameter
            String flavor = parameter.getOrDefault("flavor", "lattice"); // Default ist lattice
            command.add("--flavor");
            command.add(flavor);

            // Row Tolerance Parameter (nur wenn Flavor 'stream' ist)
            if ("stream".equalsIgnoreCase(flavor)) {
                String rowTol = parameter.get("row_tol");
                if (rowTol != null && !rowTol.isBlank()) {
                    try {
                        // Kleine Validierung, ob es eine Zahl ist (wird im Python nochmal gemacht)
                        Integer.parseInt(rowTol);
                        command.add("--row-tol");
                        command.add(rowTol);
                        log.debug("--> Füge --row-tol {} für stream-Flavor hinzu.", rowTol);
                    } catch (NumberFormatException nfe) {
                        log.warn("Ungültiger Wert für row_tol '{}' angegeben, wird ignoriert.", rowTol);
                    }
                }
            } else if (parameter.containsKey("row_tol") && parameter.get("row_tol") != null && !parameter.get("row_tol").isBlank()){
                // Logge eine Warnung, wenn row_tol fälschlicherweise für lattice angegeben wurde
                log.warn("Parameter 'row_tol' ('{}') wird für flavor '{}' ignoriert.", parameter.get("row_tol"), flavor);
            }

            // TODO: Hier weitere Parameter hinzufügen, wenn benötigt (z.B. col_tol, edge_tol)
            // Beispiel:
            // String colTol = parameter.get("col_tol");
            // if (colTol != null && !colTol.isBlank()) {
            //     command.add("--col-tol");
            //     command.add(colTol);
            // }
        } else {
             // Fallback, wenn keine Parameter übergeben wurden (setze Default Flavor)
             command.add("--flavor");
             command.add("lattice");
        }
        log.debug("Ausführendes Kommando: {}", command);
        // --- Ende Kommandozeilenaufbau ---

        // --- Prozessausführung und Ergebnisauswertung ---
        ProcessBuilder processBuilder = new ProcessBuilder(command); // Verwende die Command-Liste
        // Fehlerstrom NICHT umleiten
        // processBuilder.redirectErrorStream(true);

        StringBuilder processStdOutput = new StringBuilder(); // Sammelt stdout (JSON)
        AtomicReference<String> firstStderrLine = new AtomicReference<>(null); // Speichert die erste Zeile von stderr

        Process process = null; // Deklariere außerhalb des try für finally
        Thread stderrReaderThread = null; // Deklariere außerhalb des try für finally

        try {
            // Starte den Python-Prozess
            process = processBuilder.start();

            // Erstelle eine finale Referenz auf den Prozess für den Lambda-Ausdruck
            final Process finalProcess = process;

            // --- Separater Thread zum Lesen von stderr ---
            stderrReaderThread = new Thread(() -> {
                try (BufferedReader stderrReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = stderrReader.readLine()) != null) {
                        log.warn("Python stderr: {}", line);
                        firstStderrLine.compareAndSet(null, line.trim());
                    }
                } catch (IOException e) { log.error("Fehler beim Lesen von Python stderr: {}", e.getMessage()); }
                  catch (Exception e) { log.error("Unerwarteter Fehler im stderr-Reader-Thread: {}", e.getMessage(), e); }
            });
            stderrReaderThread.start(); // Starte den Thread, der stderr liest

            // --- Lese stdout (hier erwarten wir das JSON) ---
            try (BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stdoutReader.readLine()) != null) {
                    processStdOutput.append(line).append(System.lineSeparator());
                }
            }

            // --- Warte auf Prozessende und stderr-Leser ---
            boolean finished = process.waitFor(90, TimeUnit.SECONDS); // Warte bis zu 90 Sekunden
            if (stderrReaderThread != null) {
                 try {
                      stderrReaderThread.join(2000); // Warte bis zu 2 Sekunden auf den stderr-Leser
                 } catch (InterruptedException e) {
                      Thread.currentThread().interrupt(); // Interrupt-Status wiederherstellen
                      log.warn("Warten auf stderr-Reader-Thread wurde unterbrochen.");
                 }
            }

            int exitCode = finished ? process.exitValue() : -1; // -1 bei Timeout

            // --- Prozess-Ergebnis prüfen ---
            if (exitCode != 0 || !finished) {
                log.error("Ausführung des Python-Skripts fehlgeschlagen. Exit-Code: {}, Timeout: {}", exitCode, !finished);
                log.error("Skript stdout (letzte Zeilen):\n{}", processStdOutput.substring(Math.max(0, processStdOutput.length() - 1000)));
                log.error("Skript erste stderr Zeile: {}", firstStderrLine.get());
                fehlerDok.setError("Python-Skript fehlgeschlagen (Exit-Code: " + exitCode + "). " + (firstStderrLine.get() != null ? firstStderrLine.get() : "Details siehe Log."));
                return fehlerDok; // Gib das Fehlerobjekt zurück
            }

            // Prozess war erfolgreich (Exit-Code 0)
            log.debug("Python-Skript stdout:\n{}", processStdOutput); // Logge die reine stdout-Ausgabe

            // --- JSON-Verarbeitung ---
            String jsonString = processStdOutput.toString().trim(); // Bereinige die stdout-Ausgabe
            if (jsonString.isEmpty()) {
                log.error("Python-Skript lieferte leere stdout-Ausgabe trotz Exit-Code 0.");
                log.error("Skript erste stderr Zeile: {}", firstStderrLine.get()); // Prüfe stderr für Hinweise
                fehlerDok.setError("Python-Skript lieferte leere Ausgabe." + (firstStderrLine.get() != null ? " Möglicher Hinweis: " + firstStderrLine.get() : ""));
                return fehlerDok;
            }

            // Versuche, die JSON-Zeichenkette (aus stdout) in ein PdfDokument-Objekt zu parsen
            PdfDokument doc = objectMapper.readValue(jsonString, PdfDokument.class);
            log.info("Daten erfolgreich extrahiert und geparst für: {}", pdfPfad.getFileName());

            // Prüfe, ob das Python-Skript selbst einen Fehler im JSON-Objekt gemeldet hat
            if (doc.getError() != null && !doc.getError().isBlank()) {
                log.warn("Python-Skript meldete einen internen Fehler im JSON für {}: {}", pdfPfad.getFileName(), doc.getError());
                // Das Dokument wird trotzdem zurückgegeben, der Fehler steht im Objekt.
            }
            return doc; // Erfolgreich geparst, gib das Ergebnis zurück

        } catch (InterruptedException e) {
            // Wird ausgelöst, wenn das Warten auf den Prozess oder den stderr-Thread unterbrochen wird
            Thread.currentThread().interrupt(); // Setze den Interrupt-Status für den aufrufenden Code
            log.error("Warten auf Python-Prozess/stderr unterbrochen: {}", e.getMessage(), e);
            fehlerDok.setError("Java Fehler: Warten auf Python-Prozess/stderr unterbrochen.");
            return fehlerDok;
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
            fehlerDok.setError(errorMsg);
            return fehlerDok;
        } catch (IOException e) {
            // Fängt andere IOExceptions ab (z.B. von process.start(), InputStream/ErrorStream lesen)
            log.error("I/O Fehler beim Ausführen oder Lesen vom Python-Skript: {}", e.getMessage(), e);
            fehlerDok.setError("Java I/O Fehler: Konnte Python-Skript nicht ausführen/lesen: " + e.getMessage());
            return fehlerDok;
        } finally {
             // --- Aufräumen im finally-Block ---
             // Stellen Sie sicher, dass der externe Prozess zerstört wird, falls er noch läuft
             if (process != null && process.isAlive()) {
                 log.warn("Zerstöre noch laufenden Python-Prozess.");
                 process.destroy();
                 // Optional: process.destroyForcibly() nach einer Wartezeit hinzufügen
                 try {
                      if (!process.waitFor(1, TimeUnit.SECONDS)) {
                           log.warn("Prozess reagierte nicht auf destroy(), versuche destroyForcibly().");
                           process.destroyForcibly();
                      }
                 } catch (InterruptedException ex) {
                      Thread.currentThread().interrupt(); // Interrupt-Status wiederherstellen
                      process.destroyForcibly(); // Trotzdem versuchen zu beenden
                 }
             }
             // Stellen Sie sicher, dass der stderr-Leserthread beendet ist
             if (stderrReaderThread != null && stderrReaderThread.isAlive()) {
                 log.warn("Unterbreche noch laufenden stderr-Reader-Thread.");
                 stderrReaderThread.interrupt();
             }
        }
    }
}