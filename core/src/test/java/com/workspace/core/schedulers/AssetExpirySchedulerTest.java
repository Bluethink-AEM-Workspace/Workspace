package com.workspace.core.schedulers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.workspace.core.services.AssetExpiryNotificationService;
import com.workspace.core.services.AssetExpiryQueryService;
import com.workspace.core.services.AssetExpiryReportService;
import com.workspace.core.services.DamAdminRecipientService;
import com.workspace.core.services.ExpiryNotificationAuditService;

class AssetExpirySchedulerTest {

    private static final String TEST_DAM_ROOT_PATH =
            "/content/dam/workspace";

    private static final String ASSET_1 =
            "/content/dam/workspace/asset1.jpg";

    private static final String ASSET_2 =
            "/content/dam/workspace/asset2.jpg";

    private static final String ASSET_3 =
            "/content/dam/workspace/asset3.jpg";

    private static final String METADATA_NODE =
            "jcr:content/metadata";

    private static final String EXPIRATION_PROPERTY =
            "prism:expirationDate";

    private static final String JOB_NAME =
            "workspace-asset-expiry-scheduler";

    @Mock
    private Scheduler scheduler;

    @Mock
    private ScheduleOptions scheduleOptions;

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private AssetExpiryQueryService assetExpiryQueryService;

    @Mock
    private AssetExpiryNotificationService assetExpiryNotificationService;

    @Mock
    private AssetExpiryReportService assetExpiryReportService;

    @Mock
    private DamAdminRecipientService damAdminRecipientService;

    @Mock
    private ExpiryNotificationAuditService expiryNotificationAuditService;

    @Mock
    private Resource asset1;

    @Mock
    private Resource asset2;

    @Mock
    private Resource asset3;

    @Mock
    private Resource metadata;

    /*
     * IMPORTANT:
     * Mockito returns null from metadata.getValueMap()
     * unless it is explicitly mocked.
     */
    @Mock
    private ValueMap metadataValueMap;

    private AssetExpiryScheduler schedulerService;

    @BeforeEach
    void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);

        schedulerService = new AssetExpiryScheduler();

        setField("scheduler", scheduler);
        setField("resourceResolverFactory", resourceResolverFactory);
        setField("assetExpiryQueryService", assetExpiryQueryService);
        setField(
                "assetExpiryNotificationService",
                assetExpiryNotificationService);
        setField(
                "assetExpiryReportService",
                assetExpiryReportService);
        setField(
                "damAdminRecipientService",
                damAdminRecipientService);
        setField(
                "expiryNotificationAuditService",
                expiryNotificationAuditService);

        setField("enabled", true);
        setField("notificationWindowDays", 7);
        setField("cronExpression", "0 0 9 * * ?");
        setField("damRootPath", TEST_DAM_ROOT_PATH);
        setField("deduplicationEnabled", true);

        /*
         * Fix for the NPE:
         *
         * metadata.getValueMap() must return a mocked ValueMap.
         */
        when(metadata.getValueMap())
                .thenReturn(metadataValueMap);
    }

    // =========================================================
    // execute() - no assets
    // =========================================================

    @Test
    void shouldDoNothingWhenNoAssetsAreFound() {

        when(assetExpiryQueryService.findExpiringAssets(
                anyString(),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(Collections.<String>emptyList());

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(assetExpiryQueryService)
                .findExpiringAssets(
                        eq(TEST_DAM_ROOT_PATH),
                        any(Instant.class),
                        any(Instant.class));

        verify(
                damAdminRecipientService,
                never())
                .getRecipientEmails();

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());
    }

    // =========================================================
    // execute() - query failure
    // =========================================================

    @Test
    void shouldHandleQueryServiceException() {

        when(assetExpiryQueryService.findExpiringAssets(
                anyString(),
                any(Instant.class),
                any(Instant.class)))
                .thenThrow(
                        new RuntimeException("Query failure"));

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(assetExpiryQueryService)
                .findExpiringAssets(
                        eq(TEST_DAM_ROOT_PATH),
                        any(Instant.class),
                        any(Instant.class));

        verify(
                damAdminRecipientService,
                never())
                .getRecipientEmails();

        verifyNoInteractions(assetExpiryReportService);
    }

    // =========================================================
    // execute() - recipient service failure
    // =========================================================

    @Test
    void shouldStopWhenRecipientServiceFails() {

        mockExpiringAssets(ASSET_1);

        when(damAdminRecipientService
                .getRecipientEmails())
                .thenThrow(
                        new RuntimeException(
                                "Recipient service failure"));

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(damAdminRecipientService)
                .getRecipientEmails();

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());

        verify(
                expiryNotificationAuditService,
                never())
                .markNotificationSent(
                        any(Resource.class),
                        any(LocalDate.class));
    }

    // =========================================================
    // execute() - no recipients
    // =========================================================

    @Test
    void shouldStopWhenNoRecipientsExist() {

        mockExpiringAssets(ASSET_1);

        when(damAdminRecipientService
                .getRecipientEmails())
                .thenReturn(
                        Collections.<String>emptyList());

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(damAdminRecipientService)
                .getRecipientEmails();

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());

        verify(
                expiryNotificationAuditService,
                never())
                .markNotificationSent(
                        any(Resource.class),
                        any(LocalDate.class));
    }

    // =========================================================
    // execute() - asset cannot be resolved
    // =========================================================

    @Test
    void shouldSkipAssetWhenResourceCannotBeResolved()
            throws Exception {

        mockExpiringAssets(ASSET_1);
        mockRecipients();
        mockResolver();

        when(resourceResolver
                .getResource(ASSET_1))
                .thenReturn(null);

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(resourceResolver)
                .getResource(ASSET_1);

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());

        verify(
                expiryNotificationAuditService,
                never())
                .wasNotificationSent(
                        any(Resource.class),
                        any(LocalDate.class));
    }

    // =========================================================
    // execute() - metadata missing
    // =========================================================

    @Test
    void shouldSkipAssetWhenMetadataIsMissing()
            throws Exception {

        mockExpiringAssets(ASSET_1);
        mockRecipients();
        mockResolver();

        when(resourceResolver
                .getResource(ASSET_1))
                .thenReturn(asset1);

        when(asset1.getChild(METADATA_NODE))
                .thenReturn(null);

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(asset1)
                .getChild(METADATA_NODE);

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());
    }

    // =========================================================
    // execute() - multiple assets
    // =========================================================

    @Test
    void shouldProcessMultipleAssetPaths()
            throws Exception {

        mockExpiringAssets(
                ASSET_1,
                ASSET_2,
                ASSET_3);

        mockRecipients();
        mockResolver();

        when(resourceResolver
                .getResource(ASSET_1))
                .thenReturn(asset1);

        when(resourceResolver
                .getResource(ASSET_2))
                .thenReturn(asset2);

        when(resourceResolver
                .getResource(ASSET_3))
                .thenReturn(asset3);

        when(asset1.getChild(METADATA_NODE))
                .thenReturn(null);

        when(asset2.getChild(METADATA_NODE))
                .thenReturn(null);

        when(asset3.getChild(METADATA_NODE))
                .thenReturn(null);

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(resourceResolver)
                .getResource(ASSET_1);

        verify(resourceResolver)
                .getResource(ASSET_2);

        verify(resourceResolver)
                .getResource(ASSET_3);

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());
    }

    // =========================================================
    // execute() - expiration date missing
    // =========================================================

    @Test
    void shouldSkipAssetWhenExpirationDateIsMissing()
            throws Exception {

        mockExpiringAssets(ASSET_1);
        mockRecipients();
        mockResolver();

        when(resourceResolver
                .getResource(ASSET_1))
                .thenReturn(asset1);

        when(asset1.getChild(METADATA_NODE))
                .thenReturn(metadata);

        /*
         * Use the mocked ValueMap instead of:
         *
         * metadata.getValueMap().get(...)
         *
         * because getValueMap() otherwise returns null.
         */
        when(metadataValueMap.get(
                EXPIRATION_PROPERTY,
                Calendar.class))
                .thenReturn(null);

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(asset1)
                .getChild(METADATA_NODE);

        verify(metadata)
                .getValueMap();

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());
    }

    // =========================================================
    // execute() - already reported asset
    // =========================================================

    @Test
    void shouldSkipAlreadyReportedAsset()
            throws Exception {

        mockExpiringAssets(ASSET_1);
        mockRecipients();
        mockResolver();

        when(resourceResolver
                .getResource(ASSET_1))
                .thenReturn(asset1);

        when(asset1.getChild(METADATA_NODE))
                .thenReturn(metadata);

        Calendar calendar =
                Calendar.getInstance();

        LocalDate expirationDate =
                LocalDate.now().plusDays(3);

        calendar.set(
                expirationDate.getYear(),
                expirationDate.getMonthValue() - 1,
                expirationDate.getDayOfMonth(),
                0,
                0,
                0);

        when(metadataValueMap.get(
                EXPIRATION_PROPERTY,
                Calendar.class))
                .thenReturn(calendar);

        when(expiryNotificationAuditService
                .wasNotificationSent(
                        eq(asset1),
                        any(LocalDate.class)))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(expiryNotificationAuditService)
                .wasNotificationSent(
                        eq(asset1),
                        eq(expirationDate));

        verifyNoInteractions(assetExpiryReportService);

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());
    }

    // =========================================================
    // execute() - report generation returns null
    // =========================================================

    @Test
    void shouldNotSendEmailWhenReportGenerationReturnsNull()
            throws Exception {

        mockEligibleAsset();

        when(assetExpiryReportService.generateReport(
                anyList(),
                anyString(),
                anyString()))
                .thenReturn(null);

        assertDoesNotThrow(
                () -> schedulerService.execute());

        verify(assetExpiryReportService)
                .generateReport(
                        anyList(),
                        anyString(),
                        anyString());

        verify(
                assetExpiryNotificationService,
                never())
                .sendExpiryReport(
                        any(File.class),
                        any(String[].class),
                        anyInt());

        verify(
                expiryNotificationAuditService,
                never())
                .markNotificationSent(
                        any(Resource.class),
                        any(LocalDate.class));
    }

    // =========================================================
    // execute() - report email fails
    // =========================================================

    @Test
    void shouldNotMarkAssetsWhenReportEmailFails()
            throws Exception {

        mockEligibleAsset();

        File reportFile =
                File.createTempFile(
                        "workspace-test-report",
                        ".csv");

        try {

            when(assetExpiryReportService.generateReport(
                    anyList(),
                    anyString(),
                    anyString()))
                    .thenReturn(reportFile);

            when(assetExpiryNotificationService
                    .sendExpiryReport(
                            any(File.class),
                            any(String[].class),
                            anyInt()))
                    .thenReturn(false);

            assertDoesNotThrow(
                    () -> schedulerService.execute());

            verify(assetExpiryReportService)
                    .generateReport(
                            anyList(),
                            anyString(),
                            anyString());

            verify(assetExpiryNotificationService)
                    .sendExpiryReport(
                            eq(reportFile),
                            any(String[].class),
                            eq(1));

            verify(
                    expiryNotificationAuditService,
                    never())
                    .markNotificationSent(
                            any(Resource.class),
                            any(LocalDate.class));

        } finally {

            if (reportFile.exists()) {
                reportFile.delete();
            }
        }
    }

    // =========================================================
    // execute() - report sent successfully
    // =========================================================

    @Test
    void shouldGenerateAndSendReportSuccessfully()
            throws Exception {

        mockEligibleAsset();

        File reportFile =
                File.createTempFile(
                        "workspace-test-report",
                        ".csv");

        try {

            when(assetExpiryReportService.generateReport(
                    anyList(),
                    anyString(),
                    anyString()))
                    .thenReturn(reportFile);

            when(assetExpiryNotificationService
                    .sendExpiryReport(
                            any(File.class),
                            any(String[].class),
                            anyInt()))
                    .thenReturn(true);

            when(expiryNotificationAuditService
                    .markNotificationSent(
                            any(Resource.class),
                            any(LocalDate.class)))
                    .thenReturn(true);

            assertDoesNotThrow(
                    () -> schedulerService.execute());

            verify(assetExpiryReportService)
                    .generateReport(
                            anyList(),
                            eq("author"),
                            eq("http://localhost:4502"));

            verify(assetExpiryNotificationService)
                    .sendExpiryReport(
                            eq(reportFile),
                            any(String[].class),
                            eq(1));

            verify(expiryNotificationAuditService)
                    .markNotificationSent(
                            eq(asset1),
                            any(LocalDate.class));

        } finally {

            if (reportFile.exists()) {
                reportFile.delete();
            }
        }
    }

    // =========================================================
    // execute() - tracker update fails
    // =========================================================

    @Test
    void shouldContinueWhenTrackerUpdateFails()
            throws Exception {

        mockEligibleAsset();

        File reportFile =
                File.createTempFile(
                        "workspace-test-report",
                        ".csv");

        try {

            when(assetExpiryReportService.generateReport(
                    anyList(),
                    anyString(),
                    anyString()))
                    .thenReturn(reportFile);

            when(assetExpiryNotificationService
                    .sendExpiryReport(
                            any(File.class),
                            any(String[].class),
                            anyInt()))
                    .thenReturn(true);

            when(expiryNotificationAuditService
                    .markNotificationSent(
                            any(Resource.class),
                            any(LocalDate.class)))
                    .thenReturn(false);

            assertDoesNotThrow(
                    () -> schedulerService.execute());

            verify(assetExpiryNotificationService)
                    .sendExpiryReport(
                            eq(reportFile),
                            any(String[].class),
                            eq(1));

            verify(expiryNotificationAuditService)
                    .markNotificationSent(
                            eq(asset1),
                            any(LocalDate.class));

        } finally {

            if (reportFile.exists()) {
                reportFile.delete();
            }
        }
    }

    // =========================================================
    // execute() - tracker update throws exception
    // =========================================================

    @Test
    void shouldHandleTrackerUpdateException()
            throws Exception {

        mockEligibleAsset();

        File reportFile =
                File.createTempFile(
                        "workspace-test-report",
                        ".csv");

        try {

            when(assetExpiryReportService.generateReport(
                    anyList(),
                    anyString(),
                    anyString()))
                    .thenReturn(reportFile);

            when(assetExpiryNotificationService
                    .sendExpiryReport(
                            any(File.class),
                            any(String[].class),
                            anyInt()))
                    .thenReturn(true);

            doThrow(
                    new RuntimeException("Tracker failure"))
                    .when(expiryNotificationAuditService)
                    .markNotificationSent(
                            any(Resource.class),
                            any(LocalDate.class));

            assertDoesNotThrow(
                    () -> schedulerService.execute());

            verify(assetExpiryNotificationService)
                    .sendExpiryReport(
                            eq(reportFile),
                            any(String[].class),
                            eq(1));

            verify(expiryNotificationAuditService)
                    .markNotificationSent(
                            eq(asset1),
                            any(LocalDate.class));

        } finally {

            if (reportFile.exists()) {
                reportFile.delete();
            }
        }
    }

    // =========================================================
    // execute() - deduplication disabled
    // =========================================================

    @Test
    void shouldNotCheckTrackerWhenDeduplicationIsDisabled()
            throws Exception {

        setField(
                "deduplicationEnabled",
                false);

        mockEligibleAsset();

        File reportFile =
                File.createTempFile(
                        "workspace-test-report",
                        ".csv");

        try {

            when(assetExpiryReportService.generateReport(
                    anyList(),
                    anyString(),
                    anyString()))
                    .thenReturn(reportFile);

            when(assetExpiryNotificationService
                    .sendExpiryReport(
                            any(File.class),
                            any(String[].class),
                            anyInt()))
                    .thenReturn(true);

            assertDoesNotThrow(
                    () -> schedulerService.execute());

            verify(
                    expiryNotificationAuditService,
                    never())
                    .wasNotificationSent(
                            any(Resource.class),
                            any(LocalDate.class));

            verify(
                    expiryNotificationAuditService,
                    never())
                    .markNotificationSent(
                            any(Resource.class),
                            any(LocalDate.class));

            verify(assetExpiryReportService)
                    .generateReport(
                            anyList(),
                            anyString(),
                            anyString());

            verify(assetExpiryNotificationService)
                    .sendExpiryReport(
                            eq(reportFile),
                            any(String[].class),
                            eq(1));

        } finally {

            if (reportFile.exists()) {
                reportFile.delete();
            }
        }
    }

    // =========================================================
    // run()
    // =========================================================

    @Test
    void shouldCallExecuteWhenRunIsInvoked() {

        when(assetExpiryQueryService.findExpiringAssets(
                anyString(),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(
                        Collections.<String>emptyList());

        assertDoesNotThrow(
                () -> schedulerService.run());

        verify(assetExpiryQueryService)
                .findExpiringAssets(
                        eq(TEST_DAM_ROOT_PATH),
                        any(Instant.class),
                        any(Instant.class));
    }

    // =========================================================
    // deactivate()
    // =========================================================

    @Test
    void shouldUnscheduleJobOnDeactivate() {

        assertDoesNotThrow(
                () -> schedulerService.deactivate());

        verify(scheduler)
                .unschedule(JOB_NAME);
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void mockExpiringAssets(
            String... assetPaths) {

        List<String> paths =
                Arrays.asList(assetPaths);

        when(assetExpiryQueryService.findExpiringAssets(
                anyString(),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(paths);
    }

    private void mockRecipients() {

        when(damAdminRecipientService
                .getRecipientEmails())
                .thenReturn(
                        Arrays.asList(
                                "admin@example.com"));
    }

    private void mockResolver()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(any()))
                .thenReturn(resourceResolver);
    }

    /**
     * Creates an eligible asset whose expiration date is
     * three days from today.
     */
    private void mockEligibleAsset()
            throws Exception {

        mockExpiringAssets(ASSET_1);

        mockRecipients();

        mockResolver();

        when(resourceResolver
                .getResource(ASSET_1))
                .thenReturn(asset1);

        when(asset1.getName())
                .thenReturn("asset1.jpg");

        when(asset1.getPath())
                .thenReturn(ASSET_1);

        when(asset1.getChild(METADATA_NODE))
                .thenReturn(metadata);

        /*
         * IMPORTANT:
         * Use metadataValueMap instead of chaining
         * metadata.getValueMap().get(...)
         */
        when(metadata.getValueMap())
                .thenReturn(metadataValueMap);

        Calendar calendar =
                Calendar.getInstance();

        LocalDate expirationDate =
                LocalDate.now().plusDays(3);

        calendar.set(
                expirationDate.getYear(),
                expirationDate.getMonthValue() - 1,
                expirationDate.getDayOfMonth(),
                0,
                0,
                0);

        when(metadataValueMap.get(
                EXPIRATION_PROPERTY,
                Calendar.class))
                .thenReturn(calendar);

        when(expiryNotificationAuditService
                .wasNotificationSent(
                        any(Resource.class),
                        any(LocalDate.class)))
                .thenReturn(false);
    }

    private void setField(
            String fieldName,
            Object value)
            throws Exception {

        Field field =
                AssetExpiryScheduler.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(
                schedulerService,
                value);
    }
}