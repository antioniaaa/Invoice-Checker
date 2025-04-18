import camelot
import sys
import json
import os
import re
from pathlib import Path
import traceback
import PyPDF2 # Zum Extrahieren von Text für Datumssuche
from datetime import datetime
import warnings # Zum optionalen Unterdrücken von Warnungen
import argparse # Zum Verarbeiten von Kommandozeilenargumenten

# --- Optional: Warnungen unterdrücken ---
# Aktivieren Sie diese Zeile, wenn Sie Warnungen wie "CropBox missing"
# nicht in den Java-Logs (stderr) sehen möchten.
# warnings.filterwarnings("ignore")

def finde_abrechnungszeitraum(pdf_pfad, max_seiten=3):
    """
    Versucht, den Abrechnungszeitraum im Format
    'Abrechnung von TT.MM.JJJJ bis TT.MM.JJJJ' auf den ersten 'max_seiten' zu finden.
    Gibt start_datum, end_datum als ISO-Strings (JJJJ-MM-TT) zurück, oder None, None.
    Gibt Fehler/Warnungen nach stderr aus, was in Java geloggt wird.
    """
    start_datum, end_datum = None, None
    try:
        # Öffne das PDF mit PyPDF2
        reader = PyPDF2.PdfReader(pdf_pfad)
        # Bestimme die Anzahl der zu prüfenden Seiten (maximal 'max_seiten')
        num_pages_to_check = min(max_seiten, len(reader.pages))

        if num_pages_to_check == 0:
            print(f"WARNUNG Python: PDF {pdf_pfad} hat keine Seiten.", file=sys.stderr)
            return None, None # Keine Seiten zum Prüfen

        # Iteriere durch die ersten Seiten
        for i in range(num_pages_to_check):
            try:
                # Extrahiere Text von der aktuellen Seite
                text = reader.pages[i].extract_text()
                if text:
                    # Suche nach dem Regex-Muster für den Abrechnungszeitraum
                    match = re.search(r'Abrechnung\s+von\s+(\d{2}\.\d{2}\.\d{4})\s+bis\s+(\d{2}\.\d{2}\.\d{4})', text, re.IGNORECASE)
                    if match:
                        # Wenn Muster gefunden, extrahiere Daten und formatiere sie
                        start_str = match.group(1)
                        end_str = match.group(2)
                        start_datum = datetime.strptime(start_str, '%d.%m.%Y').strftime('%Y-%m-%d')
                        end_datum = datetime.strptime(end_str, '%d.%m.%Y').strftime('%Y-%m-%d')
                        print(f"INFO Python: Abrechnungszeitraum auf Seite {i+1} gefunden: {start_datum} bis {end_datum}", file=sys.stderr)
                        return start_datum, end_datum # Erfolgreich gefunden, gib Daten zurück
            except Exception as page_ex:
                 # Fehler beim Extrahieren einer einzelnen Seite loggen, aber weitermachen
                 print(f"WARNUNG Python: Fehler beim Extrahieren von Text auf Seite {i+1} von {pdf_pfad}: {page_ex}", file=sys.stderr)
                 continue # Versuche die nächste Seite

        # Wenn die Schleife durchläuft, ohne das Muster zu finden
        print(f"WARNUNG Python: Abrechnungszeitraum-Muster nicht auf den ersten {num_pages_to_check} Seiten von {pdf_pfad} gefunden.", file=sys.stderr)

    except Exception as e:
        # Fange allgemeine Fehler beim Öffnen/Lesen des PDFs für die Datumssuche ab
        print(f"FEHLER Python: Fehler beim Lesen des PDFs {pdf_pfad} für Datumssuche: {e}", file=sys.stderr)
        print(traceback.format_exc(), file=sys.stderr) # Gib den vollen Traceback aus

    # Gib None, None zurück, wenn nichts gefunden wurde oder ein Fehler auftrat
    return start_datum, end_datum


def extrahiere_tabellen_nach_json(pdf_pfad, flavor_param, row_tol_str, table_area_strings=None, page_string='all'):
    """
    Extrahiert Tabellen aus einem PDF mit Camelot unter Verwendung der übergebenen Parameter.
    Akzeptiert flavor, row_tol, optionale table_areas und eine Seitenspezifikation.
    Versucht einen Fallback auf 'stream', wenn 'lattice' keine Tabellen findet,
    wobei die übergebenen Bereiche und Seitenspezifikation berücksichtigt werden.
    Gibt das Ergebnis als JSON-String nach stdout aus. Fehler/Logs nach stderr.
    """
    # Initialisiere das Ergebnis-Dictionary
    ergebnis = {
        "source_pdf": str(Path(pdf_pfad).name),
        "full_path": str(pdf_pfad),
        "billing_period_start": None,
        "billing_period_end": None,
        "tables": [], # Liste für die gefundenen Tabellen
        "error": None # Feld für Fehlermeldungen
    }
    tabellen_gefunden = False # Flag, ob mindestens eine Tabelle gefunden wurde

    try:
        # --- Existenzprüfung der PDF-Datei ---
        if not os.path.exists(pdf_pfad):
             raise FileNotFoundError(f"Eingabe-PDF nicht gefunden: {pdf_pfad}")

        # --- Abrechnungszeitraum finden ---
        start_datum, end_datum = finde_abrechnungszeitraum(pdf_pfad)
        ergebnis["billing_period_start"] = start_datum
        ergebnis["billing_period_end"] = end_datum

        # --- Camelot Parameter vorbereiten (für ersten Versuch) ---
        aktiver_flavor = flavor_param # Der primär zu verwendende Flavor
        camelot_kwargs = {
            'pages': page_string, # <<< KORREKTUR: Verwende den übergebenen page_string <<<
            'flavor': aktiver_flavor,
            'suppress_stdout': True # Verhindert, dass Camelot selbst nach stdout schreibt
        }
        # Füge row_tol hinzu, aber NUR wenn der aktive Flavor 'stream' ist
        if aktiver_flavor == 'stream' and row_tol_str:
            try:
                camelot_kwargs['row_tol'] = int(row_tol_str)
                print(f"INFO Python: Verwende row_tol={camelot_kwargs['row_tol']} für Stream.", file=sys.stderr)
            except ValueError:
                 print(f"WARNUNG Python: Ungültiger row_tol '{row_tol_str}'. Ignoriere.", file=sys.stderr)

        # Füge table_areas hinzu, wenn sie übergeben wurden
        if table_area_strings:
            camelot_kwargs['table_areas'] = table_area_strings
            print(f"INFO Python: Verwende table_areas={table_area_strings} für Seite(n) '{page_string}'", file=sys.stderr) # Korrektes Logging


        # --- Erster Camelot Aufruf (mit primärem Flavor) ---
        # Log-Ausgabe KORRIGIERT, um tatsächlichen page_string anzuzeigen
        print(f"INFO Python: Versuche camelot.read_pdf mit flavor='{aktiver_flavor}' für Seite(n) '{page_string}'...", file=sys.stderr)
        try:
            # Führe die Extraktion mit den vorbereiteten Argumenten aus
            tabellen = camelot.read_pdf(pdf_pfad, **camelot_kwargs) # Übergibt korrekte kwargs
            # Log-Ausgabe KORRIGIERT, um tatsächlichen page_string anzuzeigen
            print(f"INFO Python: Camelot ({aktiver_flavor}) hat {tabellen.n} Tabellen auf Seite(n) '{page_string}' gefunden.", file=sys.stderr)

            # Verarbeite die gefundenen Tabellen
            if tabellen.n > 0:
                for i, tabelle in enumerate(tabellen):
                    kopfzeile = [str(kopf) for kopf in tabelle.df.columns.values.tolist()]
                    daten_zeilen = [[str(zelle) for zelle in reihe] for reihe in tabelle.df.values.tolist()]
                    tabellen_daten = [kopfzeile] + daten_zeilen
                    ergebnis["tables"].append({
                        "index": i, "page": tabelle.page, "accuracy": tabelle.accuracy,
                        "whitespace": tabelle.whitespace, "flavor": aktiver_flavor,
                        "data": tabellen_daten
                    })
                tabellen_gefunden = True # Setze Flag, da Tabellen gefunden wurden

            # --- Fallback auf Stream (wenn primär Lattice war und nichts fand) ---
            # Führe den Fallback nur aus, wenn der ursprüngliche Flavor 'lattice' war
            # UND wenn bisher keine Tabellen gefunden wurden.
            elif aktiver_flavor == 'lattice':
                 # Log-Ausgabe KORRIGIERT, um tatsächlichen page_string anzuzeigen
                 print(f"INFO Python: Lattice fand nichts, versuche jetzt explizit mit flavor='stream' für Seite(n) '{page_string}'...", file=sys.stderr)
                 # Baue Argumente für den Stream-Versuch
                 stream_kwargs = {
                     'pages': page_string, # <<< KORREKTUR: Behalte die Seitenspezifikation bei! <<<
                     'flavor': 'stream',
                     'suppress_stdout': True
                 }
                 # Füge row_tol hinzu, wenn vorhanden und gültig
                 if row_tol_str:
                     try: stream_kwargs['row_tol'] = int(row_tol_str)
                     except ValueError: pass # Warnung wurde ggf. schon oben ausgegeben
                 # Füge table_areas hinzu, wenn vorhanden! (WICHTIGE KORREKTUR)
                 if table_area_strings:
                     stream_kwargs['table_areas'] = table_area_strings
                     # Log-Ausgabe KORRIGIERT, um tatsächlichen page_string anzuzeigen
                     print(f"INFO Python: Verwende auch für Stream-Fallback table_areas={table_area_strings} für Seite(n) '{page_string}'", file=sys.stderr)

                 # Versuche die Extraktion mit Stream
                 try:
                     tabellen_stream = camelot.read_pdf(pdf_pfad, **stream_kwargs) # Rufe mit Stream-kwargs auf
                     # Log-Ausgabe KORRIGIERT, um tatsächlichen page_string anzuzeigen
                     print(f"INFO Python: Camelot (stream Fallback) hat {tabellen_stream.n} Tabellen auf Seite(n) '{page_string}' gefunden.", file=sys.stderr)
                     if tabellen_stream.n > 0:
                          # Verarbeite die Stream-Tabellen
                          for i, tabelle in enumerate(tabellen_stream):
                              kopfzeile = [str(kopf) for kopf in tabelle.df.columns.values.tolist()]
                              daten_zeilen = [[str(zelle) for zelle in reihe] for reihe in tabelle.df.values.tolist()]
                              tabellen_daten = [kopfzeile] + daten_zeilen
                              ergebnis["tables"].append({
                                  "index": i, "page": tabelle.page, "accuracy": tabelle.accuracy,
                                  "whitespace": tabelle.whitespace, "flavor": "stream", # Korrekt als Stream markieren
                                  "data": tabellen_daten
                              })
                          tabellen_gefunden = True # Setze Flag, da Tabellen gefunden wurden
                 except Exception as e_stream_fallback:
                      # Fehler nur beim Stream-Fallback loggen
                      print(f"FEHLER Python: Fehler beim Stream-Fallback: {e_stream_fallback}", file=sys.stderr)
                      # Setze den Fehler im Ergebnis nur, wenn noch kein Fehler von Lattice vorhanden ist
                      if ergebnis["error"] is None: ergebnis["error"] = f"Stream Fallback Fehler: {e_stream_fallback}"
            else:
                 # Primärer Flavor war 'stream' und hat nichts gefunden -> Kein Fallback nötig
                 # Log-Ausgabe KORRIGIERT, um tatsächlichen page_string anzuzeigen
                 print(f"WARNUNG Python: Camelot ({aktiver_flavor}) hat keine Tabellen auf Seite(n) '{page_string}' gefunden.", file=sys.stderr)

        except Exception as e_camelot:
            # Fange Fehler vom ersten (primären) Camelot-Aufruf ab
            print(f"FEHLER Python: Fehler bei camelot.read_pdf mit flavor='{aktiver_flavor}': {e_camelot}", file=sys.stderr)
            print(traceback.format_exc(), file=sys.stderr)
            # Setze Fehler im Ergebnis, falls noch keiner gesetzt ist
            if ergebnis["error"] is None:
                 ergebnis["error"] = f"Camelot ({aktiver_flavor}) Fehler: {e_camelot}"

    # --- Allgemeine Fehlerbehandlung (fängt Fehler außerhalb von Camelot) ---
    except FileNotFoundError as fnf:
         ergebnis["error"] = str(fnf)
         print(f"FEHLER Python: {fnf}", file=sys.stderr)
    except ImportError as imp_err:
         # Sollte nach korrekter pip Installation nicht mehr passieren
         fehler_msg = f"ImportError: Camelot/Abhängigkeiten nicht korrekt installiert. {imp_err}"
         ergebnis["error"] = fehler_msg
         print(f"FEHLER Python: {fehler_msg}", file=sys.stderr)
    except Exception as e:
        # Fange alle anderen unerwarteten Fehler ab
        tb_lines = traceback.format_exc().splitlines()
        # Kürze den Traceback für das JSON-Ergebnis
        short_tb = "\n".join(tb_lines[:15] + ["... (Traceback gekürzt) ..."])
        error_msg = f"Allgemeiner Verarbeitungsfehler für {pdf_pfad}: {e}\n{short_tb}"
        ergebnis["error"] = error_msg
        # Gib den Fehler und den vollen Traceback nach stderr aus (für Java-Logs)
        print(f"FEHLER Python: Allgemeiner Fehler bei Verarbeitung von {pdf_pfad}: {e}", file=sys.stderr)
        print(traceback.format_exc(), file=sys.stderr)

    # --- JSON-Ausgabe ---
    # Gib das Ergebnis-Dictionary (das entweder Tabellen oder einen Fehler enthält)
    # als JSON formatierten String **ausschließlich** nach stdout aus.
    print(json.dumps(ergebnis, indent=2))


# --- Hauptausführungsteil des Skripts ---
if __name__ == "__main__":
    # Definiere die erwarteten Kommandozeilenargumente
    parser = argparse.ArgumentParser(
        description='Extrahiert Tabellen aus einer PDF-Datei mit Camelot, '
                    'unterstützt verschiedene Flavors, Toleranzen und Bereichsangaben.'
    )
    # Pflichtargument: Pfad zur PDF-Datei
    parser.add_argument('--pdf-path', required=True, help='Pfad zur Eingabe-PDF-Datei.')
    # Optionales Argument: Extraktionsmethode (Flavor)
    parser.add_argument('--flavor', default='lattice', choices=['lattice', 'stream'],
                        help="Camelot Extraktionsmethode ('lattice' oder 'stream'). Default: lattice")
    # Optionales Argument: Zeilentoleranz (nur für Stream relevant)
    parser.add_argument('--row-tol', default=None,
                        help="(Optional) Zeilentoleranz für Stream-Flavor (Ganzzahl).")
    # Optionales Argument: Tabellenbereiche (kann mehrfach angegeben werden)
    parser.add_argument('--table-areas', nargs='+', default=None,
                        help='Liste von Bereichen im Format "x1,y1,x2,y2", getrennt durch Leerzeichen. '
                             'Koordinatenursprung ist unten links.')
    # Optionales Argument: Seite(n), auf die sich die Extraktion/Bereiche beziehen
    parser.add_argument('--page', default='all',
                        help='Seitenzahl(en), z.B. "1", "1,3", "1-3,5", "all". Default: "all".')

    try:
        # Parse die übergebenen Argumente
        args = parser.parse_args()

        # Rufe die Hauptfunktion zur Extraktion mit den geparsten Argumenten auf
        extrahiere_tabellen_nach_json(
            args.pdf_path,
            args.flavor,
            args.row_tol,
            args.table_areas, # Liste von Strings oder None
            args.page         # String wie "1" oder "all"
        )

    except SystemExit:
        # Wird von argparse bei Fehlern wie -h ausgelöst, keine Aktion nötig
        pass
    except Exception as e:
        # Fange alle unerwarteten Fehler beim Parsen oder Ausführen ab
        print(f"FATALER FEHLER Python: {e}", file=sys.stderr)
        print(traceback.format_exc(), file=sys.stderr)
        # Versuche, ein minimales Fehler-JSON nach stdout auszugeben, damit Java nicht abstürzt
        error_json = { "error": f"Fataler Skriptfehler: {e}", "tables": [] }
        try:
            print(json.dumps(error_json, indent=2))
        except Exception:
             print('{"error": "Fataler Skriptfehler, JSON-Ausgabe fehlgeschlagen", "tables": []}') # Letzter Ausweg
        sys.exit(1) # Beende mit Fehlercode