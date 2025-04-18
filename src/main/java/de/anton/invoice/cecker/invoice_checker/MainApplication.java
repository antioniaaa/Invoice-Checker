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
            log.warn("Konnte System Look and Feel nicht setzen.", e);
        }

        // Sicherstellen, dass die GUI-Erstellung im Event Dispatch Thread (EDT) erfolgt
        SwingUtilities.invokeLater(() -> {
            log.info("Initialisiere Anwendung...");
            AnwendungsModell model = new AnwendungsModell();
            MainFrame view = new MainFrame(model);
            new AppController(model, view); // Controller verbindet Modell und View

            view.setVisible(true);
            log.info("Anwendung gestartet und View ist sichtbar.");
        });
    }
}
