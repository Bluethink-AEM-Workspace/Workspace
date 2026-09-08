/*

AssetExpiryScheduler is an OSGi-based scheduler responsible for identifying DAM assets that are approaching their configured expiration date and sending an expiry report to the configured DAM administrators. It uses a configurable Quartz cron expression, notification window, DAM root path, and deduplication setting. During execution, it queries for expiring assets, validates their expiration dates, excludes assets that have already been reported, generates a CSV report, sends it through the notification service, and records successfully reported assets in the audit tracker. The scheduler also handles configuration changes, enables/disables scheduling dynamically, manages the service ResourceResolver, and cleans up temporary report files after processing.

*/

package com.workspace.core.schedulers;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workspace.core.services.AssetExpiryNotificationService;
import com.workspace.core.services.AssetExpiryQueryService;
import com.workspace.core.services.AssetExpiryReportService;
import com.workspace.core.services.AssetExpiryReportService.AssetExpiryReportEntry;
import com.workspace.core.services.DamAdminRecipientService;
import com.workspace.core.services.ExpiryNotificationAuditService;

@Component(
        service = Runnable.class,
        immediate = true
)
@Designate(ocd = AssetExpiryScheduler.Config.class)
public class AssetExpiryScheduler implements Runnable {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    AssetExpiryScheduler.class);

    private static final String JOB_NAME =
            "workspace-asset-expiry-scheduler";

    private static final String SUBSERVICE =
            "asset-expiry-service";

    private static final int DEFAULT_WINDOW_DAYS = 7;

    private static final String DEFAULT_DAM_ROOT_PATH =
            "/content/dam/workspace";

    @Reference
    private Scheduler scheduler;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private AssetExpiryQueryService assetExpiryQueryService;

    @Reference
    private AssetExpiryNotificationService assetExpiryNotificationService;

    @Reference
    private AssetExpiryReportService assetExpiryReportService;

    @Reference
    private DamAdminRecipientService damAdminRecipientService;

    @Reference
    private ExpiryNotificationAuditService expiryNotificationAuditService;

    private volatile boolean enabled;

    private volatile int notificationWindowDays;

    private volatile String cronExpression;

    private volatile String damRootPath;

    private volatile boolean deduplicationEnabled;

    @ObjectClassDefinition(
            name = "Workspace Asset Expiry Scheduler Configuration",
            description =
                    "Configuration for the DAM asset expiry report scheduler"
    )
    public @interface Config {

        @AttributeDefinition(
                name = "Enabled",
                description =
                        "Enable or disable asset expiry notifications"
        )
        boolean enabled() default true;

        @AttributeDefinition(
                name = "Cron Expression",
                description =
                        "Quartz cron expression used to run the scheduler"
        )
        String cronExpression() default "0 0 9 * * ?";

        @AttributeDefinition(
                name = "Notification Window Days",
                description =
                        "Number of calendar days ahead to check for expiring assets"
        )
        int notificationWindowDays() default 7;

        @AttributeDefinition(
                name = "DAM Root Path",
                description =
                        "DAM root path under which expiring assets are searched"
        )
        String damRootPath() default "/content/dam/workspace";

        @AttributeDefinition(
                name = "Deduplication Enabled",
                description =
                        "Prevent duplicate report entries for the same asset and expiration date"
        )
        boolean deduplicationEnabled() default true;
    }

    @Activate
    @Modified
    protected void activate(Config config) {

        enabled =
                config.enabled();

        cronExpression =
                config.cronExpression();

        notificationWindowDays =
                config.notificationWindowDays();

        damRootPath =
                config.damRootPath();

        deduplicationEnabled =
                config.deduplicationEnabled();

        if (notificationWindowDays < 0) {

            LOG.warn(
                    "Invalid notification window {}. Using default {} days.",
                    notificationWindowDays,
                    DEFAULT_WINDOW_DAYS);

            notificationWindowDays =
                    DEFAULT_WINDOW_DAYS;
        }

        if (damRootPath == null
                || damRootPath.trim().isEmpty()) {

            LOG.warn(
                    "DAM root path is empty. Using default {}.",
                    DEFAULT_DAM_ROOT_PATH);

            damRootPath =
                    DEFAULT_DAM_ROOT_PATH;
        }

        damRootPath =
                damRootPath.trim();

        LOG.info(
                "Asset expiry scheduler configuration loaded. " +
                "Enabled: {}, Cron: {}, Window: {} days, " +
                "DAM Root: {}, Deduplication: {}",
                enabled,
                cronExpression,
                notificationWindowDays,
                damRootPath,
                deduplicationEnabled);

        scheduleJob();
    }

    private void scheduleJob() {

        try {

            scheduler.unschedule(
                    JOB_NAME);

        } catch (Exception e) {

            LOG.debug(
                    "Existing asset expiry scheduler job was not found during reschedule",
                    e);
        }

        if (!enabled) {

            LOG.info(
                    "Asset expiry scheduler is disabled. " +
                    "Job will not be registered.");

            return;
        }

        try {

            ScheduleOptions options =
                    scheduler.EXPR(
                            cronExpression);

            options.name(
                    JOB_NAME);

            options.canRunConcurrently(
                    false);

            boolean scheduled =
                    scheduler.schedule(
                            this,
                            options);

            if (scheduled) {

                LOG.info(
                        "Asset expiry scheduler registered successfully. " +
                        "Job name: {}, Cron: {}, Window: {} days, DAM Root: {}",
                        JOB_NAME,
                        cronExpression,
                        notificationWindowDays,
                        damRootPath);

            } else {

                LOG.error(
                        "Asset expiry scheduler registration returned false. " +
                        "Job name: {}",
                        JOB_NAME);
            }

        } catch (Exception e) {

            LOG.error(
                    "Unable to register asset expiry scheduler. " +
                    "Job name: {}",
                    JOB_NAME,
                    e);
        }
    }

    @Deactivate
    protected void deactivate() {

        try {

            scheduler.unschedule(
                    JOB_NAME);

            LOG.info(
                    "Asset expiry scheduler unscheduled successfully. " +
                    "Job name: {}",
                    JOB_NAME);

        } catch (Exception e) {

            LOG.error(
                    "Unable to unschedule asset expiry scheduler. " +
                    "Job name: {}",
                    JOB_NAME,
                    e);
        }
    }

    @Override
    public void run() {

        LOG.info(
                "Asset expiry scheduler Runnable triggered");

        execute();
    }

    protected void execute() {

        Instant windowStart =
                Instant.now();

        Instant windowEnd =
                windowStart.plus(
                        notificationWindowDays,
                        ChronoUnit.DAYS);

        LOG.info(
                "Starting asset expiry report execution. " +
                "DAM Root: {}, Window start: {}, Window end: {}",
                damRootPath,
                windowStart,
                windowEnd);

        int foundCount = 0;
        int reportCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        int markedCount = 0;

        List<String> expiringAssetPaths;

        try {

            expiringAssetPaths =
                    assetExpiryQueryService.findExpiringAssets(
                            damRootPath,
                            windowStart,
                            windowEnd);

        } catch (Exception e) {

            LOG.error(
                    "Unable to query expiring assets under DAM root: {}",
                    damRootPath,
                    e);

            return;
        }

        if (expiringAssetPaths == null
                || expiringAssetPaths.isEmpty()) {

            LOG.info(
                    "Asset expiry report execution completed. " +
                    "Found: 0, Report: 0, Skipped: 0, Failed: 0, Marked: 0");

            return;
        }

        foundCount =
                expiringAssetPaths.size();

        LOG.info(
                "Found {} assets within expiry window under {}",
                foundCount,
                damRootPath);

        List<String> recipientList;

        try {

            recipientList =
                    damAdminRecipientService.getRecipientEmails();

        } catch (Exception e) {

            LOG.error(
                    "Unable to resolve DAM Admin recipients",
                    e);

            return;
        }

        if (recipientList == null
                || recipientList.isEmpty()) {

            LOG.error(
                    "No valid DAM Admin email exists. " +
                    "No expiry report will be generated or marked.");

            return;
        }

        String[] recipients =
                recipientList.toArray(
                        new String[recipientList.size()]);

        /*
         * These are the assets that will actually appear in the CSV.
         *
         * We keep the corresponding Resource and expiration date so
         * that after successful email delivery we can update the tracker.
         */
        List<Resource> reportAssets =
                new ArrayList<Resource>();

        List<LocalDate> reportExpirationDates =
                new ArrayList<LocalDate>();

        List<AssetExpiryReportEntry> reportEntries =
                new ArrayList<AssetExpiryReportEntry>();

        try (ResourceResolver resolver =
                     getServiceResourceResolver()) {

            LocalDate today =
                    LocalDate.now();

            LocalDate lastEligibleDate =
                    today.plusDays(
                            notificationWindowDays);

            for (String assetPath :
                    expiringAssetPaths) {

                try {

                    if (assetPath == null
                            || assetPath.trim().isEmpty()) {

                        skippedCount++;

                        LOG.warn(
                                "Skipping null or empty asset path");

                        continue;
                    }

                    Resource asset =
                            resolver.getResource(
                                    assetPath);

                    if (asset == null) {

                        failedCount++;

                        LOG.error(
                                "Unable to resolve asset path: {}",
                                assetPath);

                        continue;
                    }

                    LocalDate expirationDate =
                            getExpirationDate(
                                    asset);

                    /*
                     * No expiration date means the asset must not
                     * enter the report.
                     */
                    if (expirationDate == null) {

                        skippedCount++;

                        LOG.warn(
                                "Skipping asset because expiration date " +
                                "could not be determined: {}",
                                assetPath);

                        continue;
                    }

                    /*
                     * Double-check the current date against the
                     * notification window.
                     */
                    if (expirationDate.isBefore(today)
                            || expirationDate.isAfter(
                                    lastEligibleDate)) {

                        skippedCount++;

                        LOG.info(
                                "Skipping asset outside notification date window. " +
                                "Asset: {}, Expiration: {}",
                                assetPath,
                                expirationDate);

                        continue;
                    }

                    /*
                     * TRACKER CHECK
                     *
                     * If the same asset was already processed for
                     * this exact expiration date, do not add it
                     * to the new report.
                     *
                     * If the expiration date was changed, the audit
                     * lookup for the new date will return false,
                     * therefore the asset will be included again.
                     */
                    if (deduplicationEnabled
                            && expiryNotificationAuditService
                                    .wasNotificationSent(
                                            asset,
                                            expirationDate)) {

                        skippedCount++;

                        LOG.info(
                                "Skipping already-reported asset. " +
                                "Asset: {}, Expiration: {}",
                                assetPath,
                                expirationDate);

                        continue;
                    }

                    reportAssets.add(
                            asset);

                    reportExpirationDates.add(
                            expirationDate);

                    reportEntries.add(
                            new AssetExpiryReportEntry(
                                    asset.getName(),
                                    asset.getPath(),
                                    expirationDate));

                    reportCount++;

                    LOG.debug(
                            "Added asset to expiry report. " +
                            "Asset: {}, Expiration: {}",
                            assetPath,
                            expirationDate);

                } catch (Exception e) {

                    failedCount++;

                    LOG.error(
                            "Unexpected failure while evaluating asset {}. " +
                            "Remaining assets will continue.",
                            assetPath,
                            e);
                }
            }

            /*
             * Nothing eligible for the report.
             */
            if (reportEntries.isEmpty()) {

                LOG.info(
                        "No new eligible assets found for expiry report. " +
                        "Found: {}, Skipped: {}, Failed: {}",
                        foundCount,
                        skippedCount,
                        failedCount);

                return;
            }

            File reportFile = null;

            try {

                /*
                 * STEP 1:
                 * Generate CSV.
                 */
                reportFile =
                        assetExpiryReportService.generateReport(
                                reportEntries,
                                "author",
                                "http://localhost:4502");

                if (reportFile == null) {

                    failedCount++;

                    LOG.error(
                            "Expiry report generation returned null. " +
                            "No email will be sent.");

                    return;
                }

                /*
                 * STEP 2:
                 * Send ONE email containing the CSV.
                 */
                boolean reportSent =
                        assetExpiryNotificationService
                                .sendExpiryReport(
                                        reportFile,
                                        recipients,
                                        reportCount);

                if (!reportSent) {

                    failedCount++;

                    LOG.error(
                            "Expiry report email failed. " +
                            "No assets will be marked as reported.");

                    return;
                }

                /*
                 * STEP 3:
                 * Email was successfully handed to the gateway.
                 *
                 * Now update the tracker for each asset.
                 */
                if (deduplicationEnabled) {

                    for (int i = 0;
                            i < reportAssets.size();
                            i++) {

                        try {

                            boolean auditSaved =
                                    expiryNotificationAuditService
                                            .markNotificationSent(
                                                    reportAssets.get(i),
                                                    reportExpirationDates.get(i));

                            if (auditSaved) {

                                markedCount++;

                            } else {

                                failedCount++;

                                LOG.error(
                                        "Report was sent but tracker " +
                                        "could not be updated. Asset: {}, Expiration: {}",
                                        reportAssets.get(i).getPath(),
                                        reportExpirationDates.get(i));
                            }

                        } catch (Exception e) {

                            failedCount++;

                            LOG.error(
                                    "Report was sent but tracker update " +
                                    "failed for asset: {}",
                                    reportAssets.get(i).getPath(),
                                    e);
                        }
                    }

                } else {

                    markedCount =
                            reportCount;
                }

                LOG.info(
                        "Asset expiry report successfully sent. " +
                        "Assets included: {}, Assets marked: {}",
                        reportCount,
                        markedCount);

            } finally {

                /*
                 * The CSV is only required while sending the email.
                 * Delete the temporary file afterwards.
                 */
                if (reportFile != null
                        && reportFile.exists()) {

                    boolean deleted =
                            reportFile.delete();

                    if (!deleted) {

                        LOG.warn(
                                "Unable to delete temporary expiry report file: {}",
                                reportFile.getAbsolutePath());

                    } else {

                        LOG.debug(
                                "Temporary expiry report file deleted: {}",
                                reportFile.getAbsolutePath());
                    }
                }
            }

        } catch (Exception e) {

            LOG.error(
                    "Unable to obtain service ResourceResolver " +
                    "for asset expiry report processing",
                    e);

            failedCount +=
                    expiringAssetPaths.size();
        }

        LOG.info(
                "Asset expiry report execution summary. " +
                "Found: {}, Report: {}, Skipped: {}, Failed: {}, Marked: {}",
                foundCount,
                reportCount,
                skippedCount,
                failedCount,
                markedCount);
    }

    private LocalDate getExpirationDate(
            Resource asset) {

        Resource metadata =
                asset.getChild(
                        "jcr:content/metadata");

        if (metadata == null) {
            return null;
        }

        java.util.Calendar expirationCalendar =
                metadata.getValueMap().get(
                        "prism:expirationDate",
                        java.util.Calendar.class);

        if (expirationCalendar == null) {
            return null;
        }

        return LocalDate.of(
                expirationCalendar.get(
                        java.util.Calendar.YEAR),
                expirationCalendar.get(
                        java.util.Calendar.MONTH) + 1,
                expirationCalendar.get(
                        java.util.Calendar.DAY_OF_MONTH));
    }

    private ResourceResolver getServiceResourceResolver()
            throws Exception {

        Map<String, Object> authInfo =
                new HashMap<String, Object>();

        authInfo.put(
                ResourceResolverFactory.SUBSERVICE,
                SUBSERVICE);

        return resourceResolverFactory
                .getServiceResourceResolver(
                        authInfo);
    }
}