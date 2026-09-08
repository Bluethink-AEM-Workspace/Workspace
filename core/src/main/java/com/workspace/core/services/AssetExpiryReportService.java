/*

AssetExpiryReportService is responsible for generating a temporary CSV report containing details of all eligible expiring DAM assets. It creates the report with asset name, asset path, expiration date, environment, and Author asset link, while applying proper CSV escaping to handle special characters such as commas, quotes, and line breaks. The service writes the report to a temporary file to avoid keeping large reports in memory and returns the generated file for email attachment. It also handles empty or null asset entries and provides utility logic for constructing valid Author asset links.

*/

package com.workspace.core.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;


import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = AssetExpiryReportService.class)
public class AssetExpiryReportService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    AssetExpiryReportService.class);

    private static final String CSV_HEADER =
            "Asset Name,Asset Path,Expiration Date,Environment,Author Link";

    /**
     * Creates a CSV report containing all eligible expiring assets.
     *
     * The file is created as a temporary file so that large reports
     * do not have to be kept entirely in memory.
     *
     * @param assets eligible DAM assets
     * @param environment AEM environment
     * @param authorUrl AEM author URL
     * @return generated CSV file
     * @throws IOException if report generation fails
     */
    public File generateReport(
            List<AssetExpiryReportEntry> assets,
            String environment,
            String authorUrl) throws IOException {

        if (assets == null || assets.isEmpty()) {
            LOG.warn(
                    "Cannot generate expiry report because no assets were supplied");
            return null;
        }

        File reportFile =
                File.createTempFile(
                        "workspace-asset-expiry-",
                        ".csv");

        LOG.info(
                "Generating asset expiry CSV report: {}",
                reportFile.getAbsolutePath());

        try (BufferedWriter writer =
                     new BufferedWriter(
                             new FileWriter(reportFile))) {

            writer.write(CSV_HEADER);
            writer.newLine();

            for (AssetExpiryReportEntry asset : assets) {

                if (asset == null) {
                    continue;
                }

                String assetLink =
                        buildAuthorAssetLink(
                                authorUrl,
                                asset.getAssetPath());

                writer.write(
                        csv(asset.getAssetName()));
                writer.write(",");

                writer.write(
                        csv(asset.getAssetPath()));
                writer.write(",");

                writer.write(
                        csv(String.valueOf(
                                asset.getExpirationDate())));
                writer.write(",");

                writer.write(
                        csv(environment));
                writer.write(",");

                writer.write(
                        csv(assetLink));

                writer.newLine();
            }
        }

        LOG.info(
                "Asset expiry CSV report generated successfully. " +
                "File: {}, Size: {} bytes",
                reportFile.getAbsolutePath(),
                reportFile.length());

        return reportFile;
    }

    private String buildAuthorAssetLink(
            String authorUrl,
            String assetPath) {

        if (assetPath == null) {
            return "";
        }

        if (authorUrl == null
                || authorUrl.trim().isEmpty()) {

            return assetPath;
        }

        String cleanAuthorUrl =
                authorUrl.trim();

        while (cleanAuthorUrl.endsWith("/")) {
            cleanAuthorUrl =
                    cleanAuthorUrl.substring(
                            0,
                            cleanAuthorUrl.length() - 1);
        }

        return cleanAuthorUrl + assetPath;
    }

    /**
     * Escapes a value according to CSV rules.
     */
    private String csv(String value) {

        if (value == null) {
            return "";
        }

        String escaped =
                value.replace(
                        "\"",
                        "\"\"");

        if (escaped.contains(",")
                || escaped.contains("\"")
                || escaped.contains("\n")
                || escaped.contains("\r")) {

            return "\"" + escaped + "\"";
        }

        return escaped;
    }

    /**
     * Represents one row in the expiry report.
     */
    public static class AssetExpiryReportEntry {

        private final String assetName;
        private final String assetPath;
        private final LocalDate expirationDate;

        public AssetExpiryReportEntry(
                String assetName,
                String assetPath,
                LocalDate expirationDate) {

            this.assetName = assetName;
            this.assetPath = assetPath;
            this.expirationDate = expirationDate;
        }

        public String getAssetName() {
            return assetName;
        }

        public String getAssetPath() {
            return assetPath;
        }

        public LocalDate getExpirationDate() {
            return expirationDate;
        }
    }
}