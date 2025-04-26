package de.anton.invoice.checker.invoice_checker;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.anton.invoice.checker.invoice_checker.controller.AppController;
import de.anton.invoice.checker.invoice_checker.model.AnwendungsModell;
import de.anton.invoice.checker.invoice_checker.view.MainFrame;

import javax.swing.*;

public class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    public static void main(String[] args) {
    	 // Optional: Versuche, das native Look and Feel des Betriebssystems zu setzen
        // für ein konsistenteres Erscheinungsbild.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            log.info("System Look and Feel gesetzt: {}", UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            // Falls das Setzen fehlschlägt (selten), wird das Standard-Java-Look-and-Feel verwendet.
            log.warn("System Look and Feel konnte nicht gesetzt werden. Verwende Standard.", e);
        }

        SwingUtilities.invokeLater(() -> {
            log.info("Initialisiere Anwendung im EDT...");
            AnwendungsModell model = new AnwendungsModell();
            log.debug("Modell erstellt.");

            // *** KORREKTUR: Reihenfolge und Konstruktor-Aufrufe ***
            // 1. View erstellen (braucht nur Modell)
            MainFrame view = new MainFrame(model);
            log.debug("View (MainFrame) erstellt.");

            // 2. Controller erstellen (braucht Modell UND View)
            AppController controller = new AppController(model, view); // Übergib Model und View
            log.debug("Controller erstellt und Listener initialisiert.");

            // 3. View sichtbar machen
            view.setVisible(true);
            log.info("Anwendung gestartet und Hauptfenster ist sichtbar.");
        });
    }
}
