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
    	 // Optional: Versuche, das native Look and Feel des Betriebssystems zu setzen
        // für ein konsistenteres Erscheinungsbild.
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            log.info("System Look and Feel gesetzt: {}", UIManager.getSystemLookAndFeelClassName());
        } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            // Falls das Setzen fehlschlägt (selten), wird das Standard-Java-Look-and-Feel verwendet.
            log.warn("System Look and Feel konnte nicht gesetzt werden. Verwende Standard.", e);
        }

        // GUI Erstellung im EDT
        SwingUtilities.invokeLater(() -> {
            log.info("Initialisiere Anwendung im EDT...");

            // 1. Modell erstellen
            AnwendungsModell model = new AnwendungsModell();
            log.debug("Modell erstellt.");

            // 2. Controller erstellen (braucht nur Modell)
            // *** KORREKTUR: Nur das Modell übergeben ***
            AppController controller = new AppController(model);
            log.debug("Controller erstellt.");

            // 3. View erstellen (braucht nur Modell)
            // *** KORREKTUR: Konstruktor OHNE Controller aufrufen ***
            MainFrame view = new MainFrame(model);
            log.debug("View (MainFrame) erstellt.");

            // 4. Listener im Controller initialisieren und View-Referenz setzen
            //    Der Controller kennt jetzt das Modell, die View wird hier übergeben.
            // *** KORREKTUR: Diese Methode ruft der Controller intern auf,
            //     wenn die View übergeben wird. ***
            controller.setViewAndInitializeListeners(view); // Übergib die View an den Controller
            log.debug("Controller Listener initialisiert.");

            // 5. Hauptfenster (View) sichtbar machen
            view.setVisible(true);
            log.info("Anwendung gestartet und Hauptfenster ist sichtbar.");
        });
    }
}
