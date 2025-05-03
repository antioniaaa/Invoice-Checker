package de.anton.invoice.checker.invoice_checker.model;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class AnwendungsModell {
    // Konstanten für Property-Namen
    public static final String DOCUMENTS_UPDATED_PROPERTY = "documentsUpdated";
    public static final String SELECTED_DOCUMENT_PROPERTY = "selectedDocument";
    public static final String SELECTED_TABLE_PROPERTY = "selectedTable";
    public static final String ACTIVE_CONFIG_PROPERTY = "activeConfig";
    public static final String PROGRESS_UPDATE_PROPERTY = "progressUpdate"; // Nicht als Event genutzt
    public static final String SINGLE_DOCUMENT_REPROCESSED_PROPERTY = "singleDocumentReprocessed";
    private static final Logger log = LoggerFactory.getLogger(AnwendungsModell.class);
    // Zustand
    private final List<PdfDokument> dokumente = Collections.synchronizedList(new ArrayList<>());
    // Services
    private final ExtraktionsService extraktionsService;
    private final ExcelExportService excelExportService;
    private final ConfigurationService configurationService;
    private final InvoiceTypeService invoiceTypeService;
    // MVC & Threads
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private PdfDokument ausgewaehltesDokument = null;
    private ExtrahierteTabelle ausgewaehlteTabelle = null;
    private ExtractionConfiguration aktiveKonfiguration = null;

    public AnwendungsModell() {
        this.extraktionsService = new ExtraktionsService();
        this.excelExportService = new ExcelExportService();
        this.configurationService = new ConfigurationService();
        this.invoiceTypeService = new InvoiceTypeService();
    }

    // --- PropertyChange Support ---
    public void addPropertyChangeListener(PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    public void removePropertyChangeListener(PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }

    // --- Getter ---
    public List<PdfDokument> getDokumente() {
        synchronized (dokumente) {
            return new ArrayList<>(dokumente);
        }
    }

    public PdfDokument getAusgewaehltesDokument() {
        return ausgewaehltesDokument;
    }

    // --- Setter ---
    public void setAusgewaehltesDokument(PdfDokument selectedDocument) {
        log.info("Setze ausgew. Dok: {}", (selectedDocument != null ? selectedDocument.getSourcePdf() : "null"));
        PdfDokument oldSelection = this.ausgewaehltesDokument;
        if (!Objects.equals(oldSelection, selectedDocument)) {
            this.ausgewaehltesDokument = selectedDocument;
            log.info("--> PDF geändert. Event '{}'", SELECTED_DOCUMENT_PROPERTY);
            SwingUtilities.invokeLater(() -> support.firePropertyChange(SELECTED_DOCUMENT_PROPERTY, oldSelection, this.ausgewaehltesDokument));
            List<ExtrahierteTabelle> tables = getVerfuegbareTabellen();
            setAusgewaehlteTabelle(!tables.isEmpty() ? tables.get(0) : null);
        } else {
            log.debug("--> PDF nicht geändert.");
        }
    }

    public ExtrahierteTabelle getAusgewaehlteTabelle() {
        return ausgewaehlteTabelle;
    }

    public void setAusgewaehlteTabelle(ExtrahierteTabelle selectedTable) {
        log.info("Setze ausgew. Tabelle: {}", selectedTable);
        ExtrahierteTabelle oldSelection = this.ausgewaehlteTabelle;
        if (!Objects.equals(oldSelection, selectedTable)) {
            this.ausgewaehlteTabelle = selectedTable;
            log.info("--> Tabelle geändert. Event '{}'", SELECTED_TABLE_PROPERTY);
            SwingUtilities.invokeLater(() -> support.firePropertyChange(SELECTED_TABLE_PROPERTY, oldSelection, this.ausgewaehlteTabelle));
        } else {
            log.debug("--> Tabelle nicht geändert.");
        }
    }

    public Optional<List<List<String>>> getAusgewaehlteTabellenDaten() {
        if (this.ausgewaehlteTabelle != null && this.ausgewaehlteTabelle.getData() != null)
            return Optional.of(this.ausgewaehlteTabelle.getData());
        return Optional.empty();
    }

    public List<ExtrahierteTabelle> getVerfuegbareTabellen() {
        if (ausgewaehltesDokument != null && ausgewaehltesDokument.getTables() != null)
            return new ArrayList<>(ausgewaehltesDokument.getTables());
        return Collections.emptyList();
    }

    public ExtractionConfiguration getAktiveKonfiguration() {
        return aktiveKonfiguration;
    }

    public void setAktiveKonfiguration(ExtractionConfiguration aktiveKonfiguration) {
        log.info("Setze aktive Bereichs-Konfig: {}", (aktiveKonfiguration != null ? aktiveKonfiguration.getName() : "null"));
        ExtractionConfiguration oldConfig = this.aktiveKonfiguration;
        if (!Objects.equals(oldConfig, aktiveKonfiguration)) {
            this.aktiveKonfiguration = aktiveKonfiguration;
            log.info("--> Aktive Bereichs-Konfig geändert. Event '{}'", ACTIVE_CONFIG_PROPERTY);
            SwingUtilities.invokeLater(() -> support.firePropertyChange(ACTIVE_CONFIG_PROPERTY, oldConfig, this.aktiveKonfiguration));
        } else {
            log.debug("--> Aktive Bereichs-Konfig nicht geändert.");
        }
    }

    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    public InvoiceTypeService getInvoiceTypeService() {
        return invoiceTypeService;
    }

    // --- Kernfunktionalität: Laden und Exportieren ---

    public void ladeUndVerarbeitePdfs(List<Path> pdfPfade, Map<String, String> parameterGui, Consumer<PdfDokument> onSingleDocumentProcessedForStatus, Consumer<Double> progressCallback) {
        ladeUndVerarbeitePdfsMitKonfiguration(pdfPfade, parameterGui, this.aktiveKonfiguration, onSingleDocumentProcessedForStatus, progressCallback);
    }

    public void ladeUndVerarbeitePdfsMitKonfiguration(List<Path> pdfPfade, Map<String, String> parameterGui, ExtractionConfiguration config, Consumer<PdfDokument> onSingleDocumentProcessedForStatus, Consumer<Double> progressCallback) {
        final ExtractionConfiguration aktuelleBereichsKonfig = config;
        log.info("Starte Ladevorgang für {} PDFs mit GUI-Parametern: {} und Bereichs-Konfig: {}", pdfPfade.size(), parameterGui, (aktuelleBereichsKonfig != null ? aktuelleBereichsKonfig.getName() : "Keine"));
        final int totalPdfs = pdfPfade.size();
        final AtomicInteger processedCount = new AtomicInteger(0);
        if (progressCallback != null) SwingUtilities.invokeLater(() -> progressCallback.accept(0.0));

        for (Path pdfPfad : pdfPfade) {
            final Map<String, String> aktuelleGuiParameter = (parameterGui != null) ? new HashMap<>(parameterGui) : Collections.emptyMap();
            final Path aktuellerPdfPfad = pdfPfad;
            log.info("Reiche PDF zur Verarbeitung ein: {}", aktuellerPdfPfad);

            executorService.submit(() -> {
                PdfDokument finalesErgebnisDokument = null;
                boolean listUpdated = false;
                StringBuilder gesammelteFehler = new StringBuilder();
                PDDocument pdDocForKeyword = null;
                boolean isSelectedDocumentBeingReprocessed = false;

                try {
                    finalesErgebnisDokument = new PdfDokument();
                    finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                    finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());

                    synchronized (dokumente) {
                        if (this.ausgewaehltesDokument != null && this.ausgewaehltesDokument.getFullPath() != null && this.ausgewaehltesDokument.getFullPath().equals(aktuellerPdfPfad.toString()))
                            isSelectedDocumentBeingReprocessed = true;
                    }

                    InvoiceTypeConfig typConfig = null;
                    try {
                        pdDocForKeyword = PDDocument.load(aktuellerPdfPfad.toFile());
                        typConfig = invoiceTypeService.findConfigForPdf(pdDocForKeyword);
                    } catch (IOException e) {
                        log.error("Fehler Laden Keyword: {}", e.getMessage());
                        gesammelteFehler.append("Fehler Keyword-Suche; ");
                        typConfig = invoiceTypeService.getDefaultConfig();
                    } finally {
                        if (pdDocForKeyword != null) try {
                            pdDocForKeyword.close();
                        } catch (IOException e) {
                            log.error("Fehler Schliessen PDF", e);
                        }
                    }
                    log.info("--> Ermittelter Typ: {}", (typConfig != null ? typConfig.getType() : "Default"));
                    finalesErgebnisDokument.setDetectedInvoiceType(typConfig != null ? typConfig : invoiceTypeService.getDefaultConfig()); // Typ im Dokument speichern

                    Map<String, String> finaleParameter = new HashMap<>();
                    InvoiceTypeConfig effectiveTypConfig = finalesErgebnisDokument.getDetectedInvoiceType(); // Nimm den gerade gesetzten
                    finaleParameter.put("flavor", aktuelleGuiParameter.getOrDefault("flavor", effectiveTypConfig.getDefaultFlavor()));
                    finaleParameter.put("row_tol", aktuelleGuiParameter.getOrDefault("row_tol", effectiveTypConfig.getDefaultRowTol()));
                    log.debug("--> Finale Extraktionsparameter: {}", finaleParameter);

                    List<PdfDokument> teilErgebnisse = new ArrayList<>();
                    String pageStringToProcess = "all";
                    List<String> areasForPython = null;

                    if (aktuelleBereichsKonfig == null) {
                        log.debug("--> Keine Bereichs-Konfig aktiv.");
                        PdfDokument erg = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, null, "all");
                        if (erg != null) teilErgebnisse.add(erg);
                        else gesammelteFehler.append("Extraktion null; ");
                    } else if (!aktuelleBereichsKonfig.isUsePageSpecificAreas()) {
                        List<AreaDefinition> globalAreas = aktuelleBereichsKonfig.getGlobalAreasList();
                        if (globalAreas != null && !globalAreas.isEmpty()) {
                            areasForPython = globalAreas.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList());
                            log.info("--> Verwende {} globale Bereiche.", areasForPython.size());
                        } else log.debug("--> Global, aber keine Bereiche.");
                        PdfDokument erg = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, areasForPython, "all");
                        if (erg != null) teilErgebnisse.add(erg);
                        else gesammelteFehler.append("Extraktion (global) null; ");
                    } else {
                        log.info("--> Seitenspezifischer Modus aktiv.");
                        Map<Integer, List<AreaDefinition>> map = aktuelleBereichsKonfig.getPageSpecificAreasMap();
                        if (map == null || map.isEmpty()) {
                            log.warn("--> Seitenspezifisch, aber KEINE Bereiche definiert.");
                            PdfDokument erg = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, null, "all");
                            if (erg != null) teilErgebnisse.add(erg);
                            else gesammelteFehler.append("Extraktion (Fallback) null; ");
                        } else {
                            Set<Integer> seiten = map.keySet();
                            log.info("--> Verarbeite spezifische Seiten: {}", seiten);
                            for (Integer pageNum : seiten) {
                                List<AreaDefinition> areas = map.get(pageNum);
                                if (areas == null || areas.isEmpty()) continue;
                                List<String> areasStr = areas.stream().map(AreaDefinition::toCamelotString).collect(Collectors.toList());
                                log.info("----> Verarbeite Seite {} mit {} Bereichen...", pageNum, areasStr.size());
                                PdfDokument se = extraktionsService.extrahiereTabellenAusPdf(aktuellerPdfPfad, finaleParameter, areasStr, String.valueOf(pageNum));
                                if (se != null) teilErgebnisse.add(se);
                                else gesammelteFehler.append("Seite ").append(pageNum).append(" null; ");
                            }
                        }
                    }

                    log.info("Führe Ergebnisse von {} Aufruf(en) zusammen.", teilErgebnisse.size());
                    boolean first = true;
                    for (PdfDokument te : teilErgebnisse) {
                        if (te.getTables() != null) finalesErgebnisDokument.addTables(te.getTables());
                        if (first && te.getAbrechnungszeitraumStartStr() != null) {
                            finalesErgebnisDokument.setAbrechnungszeitraumStartStr(te.getAbrechnungszeitraumStartStr());
                            finalesErgebnisDokument.setAbrechnungszeitraumEndeStr(te.getAbrechnungszeitraumEndeStr());
                            first = false;
                        }
                        if (te.getError() != null) gesammelteFehler.append(te.getError()).append("; ");
                    }
                    log.info("Zusammengeführtes Ergebnis enthält {} Tabellen.", finalesErgebnisDokument.getTables().size());

                    if (gesammelteFehler.length() > 0) {
                        finalesErgebnisDokument.setError(gesammelteFehler.toString().trim());
                    }

                    synchronized (dokumente) {
                        final String pfadStr = aktuellerPdfPfad.toString();
                        dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(pfadStr));
                        dokumente.add(finalesErgebnisDokument);
                        Collections.sort(dokumente);
                        listUpdated = true;
                        if (isSelectedDocumentBeingReprocessed) {
                            log.debug("Aktualisiere Referenzen für ausgewaehltesDokument/Tabelle.");
                            this.ausgewaehltesDokument = finalesErgebnisDokument;
                            List<ExtrahierteTabelle> nt = this.ausgewaehltesDokument.getTables();
                            this.ausgewaehlteTabelle = (nt != null && !nt.isEmpty()) ? nt.get(0) : null;
                        }
                    }
                    if (onSingleDocumentProcessedForStatus != null) {
                        onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument);
                    }

                } catch (Exception e) {
                    log.error("Unerwarteter Fehler Verarbeitung {}: {}", aktuellerPdfPfad, e.getMessage(), e);
                    if (finalesErgebnisDokument == null) {
                        finalesErgebnisDokument = new PdfDokument();
                        finalesErgebnisDokument.setSourcePdf(aktuellerPdfPfad.getFileName().toString());
                        finalesErgebnisDokument.setFullPath(aktuellerPdfPfad.toString());
                    }
                    finalesErgebnisDokument.setError("Interner Fehler: " + e.getMessage());
                    finalesErgebnisDokument.setDetectedInvoiceType(invoiceTypeService.getDefaultConfig()); // Setze Default Typ
                    synchronized (dokumente) {
                        final String ps = aktuellerPdfPfad.toString();
                        dokumente.removeIf(d -> d.getFullPath() != null && d.getFullPath().equals(ps));
                        dokumente.add(finalesErgebnisDokument);
                        Collections.sort(dokumente);
                        listUpdated = true;
                        if (isSelectedDocumentBeingReprocessed) {
                            this.ausgewaehltesDokument = finalesErgebnisDokument;
                            this.ausgewaehlteTabelle = null;
                        }
                    }
                    if (onSingleDocumentProcessedForStatus != null) {
                        onSingleDocumentProcessedForStatus.accept(finalesErgebnisDokument);
                    }
                } finally {
                    int processed = processedCount.incrementAndGet();
                    double progress = (totalPdfs > 0) ? (double) processed / totalPdfs : 1.0;
                    if (progressCallback != null) SwingUtilities.invokeLater(() -> progressCallback.accept(progress));
                    if (listUpdated) {
                        SwingUtilities.invokeLater(() -> support.firePropertyChange(DOCUMENTS_UPDATED_PROPERTY, null, getDokumente()));
                    }
                    if (finalesErgebnisDokument != null) {
                        final PdfDokument docToSend = finalesErgebnisDokument;
                        SwingUtilities.invokeLater(() -> support.firePropertyChange(SINGLE_DOCUMENT_REPROCESSED_PROPERTY, null, docToSend));
                    }
                }
            }); // Ende Runnable
        } // Ende for Schleife
    }

    // --- Excel Export und Shutdown ---
    public void exportiereAlleNachExcel(Path zielPfad) throws IOException {
        excelExportService.exportiereNachExcel(getDokumente(), zielPfad);
    }

    public void shutdownExecutor() {
        log.info("Shutdown executor.");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) executorService.shutdownNow();
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}