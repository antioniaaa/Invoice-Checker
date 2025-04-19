package de.anton.invoice.cecker.invoice_checker;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.anton.invoice.cecker.invoice_checker.controller.AppController;
import de.anton.invoice.cecker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.cecker.invoice_checker.view.MainFrame;

import javax.swing.*;

public class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    public static void main(String[] args) {
        // Look and Feel setzen (optional, für besseres Aussehen der UI)
    	 try {
             UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         } catch (Exception e) {
             log.warn("System Look and Feel konnte nicht gesetzt werden.", e);
         }

         SwingUtilities.invokeLater(() -> {
             log.info("Initialisiere Anwendung...");

             // 1. Modell erstellen
             AnwendungsModell model = new AnwendungsModell();

             // --- KORREKTE REIHENFOLGE ---
             // 2. View erstellen (braucht Modell)
             // Der Controller wird hier noch nicht übergeben, da er noch nicht existiert
             // Die View braucht den Controller aber auch nicht im Konstruktor.
             MainFrame view = new MainFrame(model); // Konstruktor OHNE Controller aufrufen

             // 3. Controller erstellen (braucht Modell UND die existierende View)
             AppController controller = new AppController(model, view); // Übergib BEIDES

             // 4. Listener werden jetzt IM Controller-Konstruktor initialisiert
             // controller.initializeListeners(view); // Nicht mehr nötig hier aufzurufen

             // 5. View sichtbar machen
             view.setVisible(true);
             log.info("Anwendung gestartet und Hauptfenster ist sichtbar.");
        });
    }
}
