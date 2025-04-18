import camelot
import sys
import json
import os
import re
from pathlib import Path
import traceback
import PyPDF2
from datetime import datetime
import warnings
import argparse

# warnings.filterwarnings("ignore") # Kann auskommentiert bleiben, stört nicht mehr

def finde_abrechnungszeitraum(pdf_pfad, max_seiten=3):
    # ... (Funktion bleibt unverändert) ...
    start_datum, end_datum = None, None
    try:
        reader = PyPDF2.PdfReader(pdf_pfad)
        num_pages_to_check = min(max_seiten, len(reader.pages))
        if num_pages_to_check == 0: return None, None
        for i in range(num_pages_to_check):
            try:
                text = reader.pages[i].extract_text()
                if text:
                    match = re.search(r'Abrechnung\s+von\s+(\d{2}\.\d{2}\.\d{4})\s+bis\s+(\d{2}\.\d{2}\.\d{4})', text, re.IGNORECASE)
                    if match:
                        start_str, end_str = match.group(1), match.group(2)
                        start_datum = datetime.strptime(start_str, '%d.%m.%Y').strftime('%Y-%m-%d')
                        end_datum = datetime.strptime(end_str, '%d.%m.%Y').strftime('%Y-%m-%d')
                        return start_datum, end_datum
            except Exception as page_ex:
                 # Nur loggen, wenn Debugging aktiv ist
                 # print(f"WARNUNG Python: Fehler beim Extrahieren von Text Seite {i+1}: {page_ex}", file=sys.stderr)
                 pass
    except Exception as e:
        # Nur loggen, wenn Debugging aktiv ist
        # print(f"FEHLER Python: Fehler beim Lesen PDF für Datum: {e}", file=sys.stderr)
        pass
    return start_datum, end_datum


def extrahiere_tabellen_nach_json(pdf_pfad, flavor_param, row_tol_str):
    ergebnis = {
        "source_pdf": str(Path(pdf_pfad).name), "full_path": str(pdf_pfad),
        "billing_period_start": None, "billing_period_end": None,
        "tables": [], "error": None
    }
    tabellen_gefunden = False

    try:
        if not os.path.exists(pdf_pfad):
             raise FileNotFoundError(f"Eingabe-PDF nicht gefunden: {pdf_pfad}")

        start_datum, end_datum = finde_abrechnungszeitraum(pdf_pfad)
        ergebnis["billing_period_start"] = start_datum
        ergebnis["billing_period_end"] = end_datum

        aktiver_flavor = flavor_param # Der von Java übergebene Flavor

        # --- Camelot Parameter vorbereiten ---
        camelot_kwargs = {
            'pages': 'all',
            'flavor': aktiver_flavor,
            'suppress_stdout': True
        }
        # Füge row_tol hinzu, WENN flavor 'stream' ist
        if aktiver_flavor == 'stream' and row_tol_str:
            try:
                camelot_kwargs['row_tol'] = int(row_tol_str)
            except ValueError:
                 print(f"WARNUNG Python: Ungültiger row_tol '{row_tol_str}'. Ignoriere.", file=sys.stderr)

        # --- Camelot Aufruf ---
        print(f"INFO Python: Versuche camelot.read_pdf mit flavor='{aktiver_flavor}'...", file=sys.stderr)
        try:
            tabellen = camelot.read_pdf(pdf_pfad, **camelot_kwargs)
            print(f"INFO Python: Camelot ({aktiver_flavor}) hat {tabellen.n} Tabellen gefunden.", file=sys.stderr)

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
                tabellen_gefunden = True # Wichtig: Flag hier setzen!
            else:
                 print(f"WARNUNG Python: Camelot ({aktiver_flavor}) hat keine Tabellen gefunden.", file=sys.stderr)
                 # *** KORREKTUR: Nur wenn KEINE Tabellen mit dem PRIMÄREN Flavor gefunden wurden UND der primäre Flavor LATTICE war, versuche STREAM ***
                 if aktiver_flavor == 'lattice':
                      print(f"INFO Python: Lattice fand nichts, versuche jetzt explizit mit flavor='stream'...", file=sys.stderr)
                      stream_kwargs = {
                          'pages': 'all',
                          'flavor': 'stream',
                          'suppress_stdout': True
                      }
                      if row_tol_str: # Nutze row_tol nur für den Stream-Versuch
                          try: stream_kwargs['row_tol'] = int(row_tol_str)
                          except ValueError: pass # Warnung wurde ggf. schon oben ausgegeben

                      try:
                          tabellen_stream = camelot.read_pdf(pdf_pfad, **stream_kwargs)
                          print(f"INFO Python: Camelot (stream Fallback) hat {tabellen_stream.n} Tabellen gefunden.", file=sys.stderr)
                          if tabellen_stream.n > 0:
                               # Verarbeite Stream Tabellen
                               for i, tabelle in enumerate(tabellen_stream):
                                   kopfzeile = [str(kopf) for kopf in tabelle.df.columns.values.tolist()]
                                   daten_zeilen = [[str(zelle) for zelle in reihe] for reihe in tabelle.df.values.tolist()]
                                   tabellen_daten = [kopfzeile] + daten_zeilen
                                   ergebnis["tables"].append({
                                       "index": i, "page": tabelle.page, "accuracy": tabelle.accuracy,
                                       "whitespace": tabelle.whitespace, "flavor": "stream", # Korrekt als Stream markieren
                                       "data": tabellen_daten
                                   })
                               tabellen_gefunden = True # Wichtig: Flag auch hier setzen!
                      except Exception as e_stream_fallback:
                           print(f"FEHLER Python: Fehler beim Stream-Fallback: {e_stream_fallback}", file=sys.stderr)
                           if ergebnis["error"] is None: ergebnis["error"] = f"Stream Fallback Fehler: {e_stream_fallback}"

        except Exception as e_camelot:
            print(f"FEHLER Python: Fehler bei camelot.read_pdf mit flavor='{aktiver_flavor}': {e_camelot}", file=sys.stderr)
            print(traceback.format_exc(), file=sys.stderr)
            if ergebnis["error"] is None:
                 ergebnis["error"] = f"Camelot ({aktiver_flavor}) Fehler: {e_camelot}"


    # --- Allgemeine Fehlerbehandlung (wie zuvor) ---
    except FileNotFoundError as fnf: #...
         ergebnis["error"] = str(fnf); print(f"FEHLER Python: {fnf}", file=sys.stderr)
    except ImportError as imp_err: #...
         ergebnis["error"] = f"ImportError: {imp_err}"; print(f"FEHLER Python: ImportError: {imp_err}", file=sys.stderr)
    except Exception as e: #...
        tb_lines = traceback.format_exc().splitlines(); short_tb = "\n".join(tb_lines[:15]+["..."]); ergebnis["error"] = f"Allg. Fehler: {e}\n{short_tb}"; print(f"FEHLER Python: Allg.: {e}", file=sys.stderr); print(traceback.format_exc(), file=sys.stderr)

    # --- JSON-Ausgabe ---
    print(json.dumps(ergebnis, indent=2))


# --- Hauptausführungsteil (wie zuvor) ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Extrahiert Tabellen aus PDF.')
    parser.add_argument('--pdf-path', required=True)
    parser.add_argument('--flavor', default='lattice', choices=['lattice', 'stream'])
    parser.add_argument('--row-tol', default=None)
    try:
        args = parser.parse_args()
        extrahiere_tabellen_nach_json(args.pdf_path, args.flavor, args.row_tol)
    except SystemExit: pass
    except Exception as e:
        print(f"FATALER FEHLER Python: {e}", file=sys.stderr); print(traceback.format_exc(), file=sys.stderr)
        error_json = { "error": f"Fataler Skriptfehler: {e}", "tables": [] }; print(json.dumps(error_json, indent=2)); sys.exit(1)