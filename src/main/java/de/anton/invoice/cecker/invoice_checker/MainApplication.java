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

         // Erstellung der GUI immer im Event Dispatch Thread (EDT) sicherstellen
         SwingUtilities.invokeLater(() -> {
             log.info("Initialisiere Anwendung...");

             // 1. Modell erstellen (enthält die Daten und Geschäftslogik)
             AnwendungsModell model = new AnwendungsModell();

             // 2. Controller erstellen (benötigt nur das Modell für die Logik)
             // Die View-Referenz wird später gesetzt.
             AppController controller = new AppController(model);

             // 3. View erstellen (benötigt das Modell, um Daten anzuzeigen)
             // Der Controller wird hier NICHT übergeben.
             MainFrame view = new MainFrame(model);

             // 4. Listener im Controller initialisieren und View-Referenz setzen
             // Jetzt, da die View existiert, kann der Controller seine Listener registrieren.
             controller.initializeListeners(view);

             // 5. Hauptfenster sichtbar machen
             view.setVisible(true);
             log.info("Anwendung gestartet und Hauptfenster ist sichtbar.");
        });
    }
}
