package com.workspace.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.workspace.core.services.AssetExpiryReportService.AssetExpiryReportEntry;

class AssetExpiryReportServiceTest {

    private AssetExpiryReportService service;

    private File generatedReport;

    @BeforeEach
    void setUp() {
        service =
                new AssetExpiryReportService();
    }

    @AfterEach
    void tearDown() {

        if (generatedReport != null
                && generatedReport.exists()) {

            generatedReport.delete();
        }
    }

    // =========================================================
    // generateReport() - null assets
    // =========================================================

    @Test
    void shouldReturnNullWhenAssetsAreNull()
            throws Exception {

        File result =
                service.generateReport(
                        null,
                        "author",
                        "http://localhost:4502");

        assertEquals(
                null,
                result);
    }

    // =========================================================
    // generateReport() - empty assets
    // =========================================================

    @Test
    void shouldReturnNullWhenAssetsAreEmpty()
            throws Exception {

        File result =
                service.generateReport(
                        Collections.<AssetExpiryReportEntry>emptyList(),
                        "author",
                        "http://localhost:4502");

        assertEquals(
                null,
                result);
    }

    // =========================================================
    // generateReport() - single asset
    // =========================================================

    @Test
    void shouldGenerateReportForSingleAsset()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        List<AssetExpiryReportEntry> assets =
                Collections.singletonList(entry);

        generatedReport =
                service.generateReport(
                        assets,
                        "author",
                        "http://localhost:4502");

        assertNotNull(
                generatedReport);

        assertTrue(
                generatedReport.exists());

        assertTrue(
                generatedReport.length() > 0);

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                2,
                lines.size());

        assertEquals(
                "Asset Name,Asset Path,Expiration Date,Environment,Author Link",
                lines.get(0));

        assertEquals(
                "product.pdf,/content/dam/workspace/product.pdf,"
                        + "2026-09-03,author,"
                        + "http://localhost:4502/content/dam/workspace/product.pdf",
                lines.get(1));
    }

    // =========================================================
    // generateReport() - multiple assets
    // =========================================================

    @Test
    void shouldGenerateReportForMultipleAssets()
            throws Exception {

        AssetExpiryReportEntry entry1 =
                new AssetExpiryReportEntry(
                        "product1.pdf",
                        "/content/dam/workspace/product1.pdf",
                        LocalDate.of(2026, 9, 3));

        AssetExpiryReportEntry entry2 =
                new AssetExpiryReportEntry(
                        "product2.jpg",
                        "/content/dam/workspace/product2.jpg",
                        LocalDate.of(2026, 9, 5));

        AssetExpiryReportEntry entry3 =
                new AssetExpiryReportEntry(
                        "product3.png",
                        "/content/dam/workspace/product3.png",
                        LocalDate.of(2026, 9, 7));

        List<AssetExpiryReportEntry> assets =
                Arrays.asList(
                        entry1,
                        entry2,
                        entry3);

        generatedReport =
                service.generateReport(
                        assets,
                        "author",
                        "http://localhost:4502");

        assertNotNull(
                generatedReport);

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                4,
                lines.size());

        assertTrue(
                lines.get(1).contains(
                        "product1.pdf"));

        assertTrue(
                lines.get(2).contains(
                        "product2.jpg"));

        assertTrue(
                lines.get(3).contains(
                        "product3.png"));
    }

    // =========================================================
    // generateReport() - null entry
    // =========================================================

    @Test
    void shouldSkipNullAssetEntry()
            throws Exception {

        AssetExpiryReportEntry validEntry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        List<AssetExpiryReportEntry> assets =
                Arrays.asList(
                        validEntry,
                        null);

        generatedReport =
                service.generateReport(
                        assets,
                        "author",
                        "http://localhost:4502");

        assertNotNull(
                generatedReport);

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                2,
                lines.size());

        assertTrue(
                lines.get(1).contains(
                        "product.pdf"));
    }

    // =========================================================
    // generateReport() - trailing slash
    // =========================================================

    @Test
    void shouldRemoveTrailingSlashFromAuthorUrl()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "http://localhost:4502////");

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                "product.pdf,/content/dam/workspace/product.pdf,"
                        + "2026-09-03,author,"
                        + "http://localhost:4502/content/dam/workspace/product.pdf",
                lines.get(1));
    }

    // =========================================================
    // generateReport() - null author URL
    // =========================================================

    @Test
    void shouldUseAssetPathWhenAuthorUrlIsNull()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        null);

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                "product.pdf,/content/dam/workspace/product.pdf,"
                        + "2026-09-03,author,"
                        + "/content/dam/workspace/product.pdf",
                lines.get(1));
    }

    // =========================================================
    // generateReport() - blank author URL
    // =========================================================

    @Test
    void shouldUseAssetPathWhenAuthorUrlIsBlank()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "   ");

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                "product.pdf,/content/dam/workspace/product.pdf,"
                        + "2026-09-03,author,"
                        + "/content/dam/workspace/product.pdf",
                lines.get(1));
    }

    // =========================================================
    // CSV escaping - comma
    // =========================================================

    @Test
    void shouldEscapeCommaInCsvValue()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "Product, Final.pdf",
                        "/content/dam/workspace/Product, Final.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "http://localhost:4502");

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                "\"Product, Final.pdf\","
                        + "\"/content/dam/workspace/Product, Final.pdf\","
                        + "2026-09-03,"
                        + "author,"
                        + "\"http://localhost:4502/content/dam/workspace/Product, Final.pdf\"",
                lines.get(1));
    }

    // =========================================================
    // CSV escaping - quotes
    // =========================================================

    @Test
    void shouldEscapeQuotesInCsvValue()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "Product \"Final\".pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "http://localhost:4502");

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertTrue(
                lines.get(1).contains(
                        "\"Product \"\"Final\"\".pdf\""));
    }

    // =========================================================
    // CSV escaping - newline
    // =========================================================

    @Test
    void shouldEscapeNewlineInCsvValue()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "Product\nFinal.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "http://localhost:4502");

        String content =
                new String(
                        Files.readAllBytes(
                                generatedReport.toPath()));

        assertTrue(
                content.contains(
                        "\"Product\nFinal.pdf\""));
    }

    // =========================================================
    // Null values in report entry
    // =========================================================

    @Test
    void shouldHandleNullValuesInReportEntry()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        null,
                        null,
                        null);

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "http://localhost:4502");

        assertNotNull(
                generatedReport);

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                2,
                lines.size());

        assertEquals(
                "Asset Name,Asset Path,Expiration Date,Environment,Author Link",
                lines.get(0));

        /*
         * Current AssetExpiryReportService behavior:
         * null expirationDate is written as "null".
         */
        assertEquals(
                ",,null,author,",
                lines.get(1));
    }

    // =========================================================
    // Null environment
    // =========================================================

    @Test
    void shouldHandleNullEnvironment()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        null,
                        "http://localhost:4502");

        List<String> lines =
                Files.readAllLines(
                        generatedReport.toPath());

        assertEquals(
                "product.pdf,/content/dam/workspace/product.pdf,"
                        + "2026-09-03,,"
                        + "http://localhost:4502/content/dam/workspace/product.pdf",
                lines.get(1));
    }

    // =========================================================
    // AssetExpiryReportEntry getters
    // =========================================================

    @Test
    void shouldReturnCorrectAssetExpiryReportEntryValues() {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 3);

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        expirationDate);

        assertEquals(
                "product.pdf",
                entry.getAssetName());

        assertEquals(
                "/content/dam/workspace/product.pdf",
                entry.getAssetPath());

        assertEquals(
                expirationDate,
                entry.getExpirationDate());
    }

    // =========================================================
    // Generated file should be CSV
    // =========================================================

    @Test
    void shouldGenerateCsvFile()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "http://localhost:4502");

        assertNotNull(
                generatedReport);

        assertTrue(
                generatedReport.getName()
                        .startsWith(
                                "workspace-asset-expiry-"));

        assertTrue(
                generatedReport.getName()
                        .endsWith(
                                ".csv"));
    }

    // =========================================================
    // Generated file should be readable
    // =========================================================

    @Test
    void shouldCreateReadableReportFile()
            throws Exception {

        AssetExpiryReportEntry entry =
                new AssetExpiryReportEntry(
                        "product.pdf",
                        "/content/dam/workspace/product.pdf",
                        LocalDate.of(2026, 9, 3));

        generatedReport =
                service.generateReport(
                        Collections.singletonList(entry),
                        "author",
                        "http://localhost:4502");

        assertNotNull(
                generatedReport);

        assertTrue(
                generatedReport.canRead());

        assertFalse(
                generatedReport.isDirectory());
    }
}

