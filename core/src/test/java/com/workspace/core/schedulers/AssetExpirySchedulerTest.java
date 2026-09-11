package com.workspace.core.schedulers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.workspace.core.services.AssetExpiryNotificationService;
import com.workspace.core.services.AssetExpiryQueryService;
import com.workspace.core.services.AssetExpiryReportService;
import com.workspace.core.services.DamAdminRecipientService;
import com.workspace.core.services.ExpiryNotificationAuditService;

@ExtendWith(MockitoExtension.class)
class AssetExpirySchedulerTest {

        private static final String TEST_DAM_ROOT_PATH = "/content/dam/workspace";

        private static final String ASSET_1 = "/content/dam/workspace/asset1.jpg";

        private static final String ASSET_2 = "/content/dam/workspace/asset2.jpg";

        private static final String ASSET_3 = "/content/dam/workspace/asset3.jpg";

        private static final String METADATA_PATH = "jcr:content/metadata";

        private static final String EXPIRATION_PROPERTY = "prism:expirationDate";

        private static final String JOB_NAME = "workspace-asset-expiry-scheduler";

        private static final LocalDate EXPIRATION_DATE = LocalDate.now().plusDays(1);

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
        private Resource assetResource;

        @Mock
        private Resource metadataResource;

        @Mock
        private ValueMap metadataProperties;

        private AssetExpiryScheduler schedulerUnderTest;

        @BeforeEach
        void setUp() throws Exception {

                schedulerUnderTest = new AssetExpiryScheduler();

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

                // Default Author URL used by the scheduler configuration.
                setField("authorUrl", "http://localhost:4502");

                setField("deduplicationEnabled", true);
        }

        @Test
        void shouldNotProcessWhenNoAssetsAreFound() {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(Collections.emptyList());

                schedulerUnderTest.run();

                verify(assetExpiryQueryService)
                                .findExpiringAssets(
                                                eq(TEST_DAM_ROOT_PATH),
                                                any(Instant.class),
                                                any(Instant.class));

                verifyNoInteractions(
                                damAdminRecipientService,
                                assetExpiryNotificationService,
                                assetExpiryReportService);
        }

        @Test
        void shouldHandleQueryServiceException() {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenThrow(new RuntimeException("Query failed"));

                assertDoesNotThrow(
                                () -> schedulerUnderTest.run());

                verify(assetExpiryQueryService)
                                .findExpiringAssets(
                                                eq(TEST_DAM_ROOT_PATH),
                                                any(Instant.class),
                                                any(Instant.class));

                verifyNoInteractions(
                                damAdminRecipientService,
                                assetExpiryNotificationService,
                                assetExpiryReportService);
        }

        @Test
        void shouldHandleRecipientServiceException() {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenThrow(
                                                new RuntimeException("Recipient failure"));

                schedulerUnderTest.run();

                verify(damAdminRecipientService)
                                .getRecipientEmails();

                verifyNoInteractions(
                                assetExpiryNotificationService,
                                assetExpiryReportService);
        }

        @Test
        void shouldNotProcessWhenNoRecipientsAreConfigured() {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(Collections.emptyList());

                schedulerUnderTest.run();

                verify(damAdminRecipientService)
                                .getRecipientEmails();

                verifyNoInteractions(
                                assetExpiryNotificationService,
                                assetExpiryReportService);
        }

        @Test
        void shouldSkipWhenAssetResourceCannotBeResolved()
                        throws Exception {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(
                                                Collections.singletonList(
                                                                "admin@example.com"));

                when(resourceResolverFactory.getServiceResourceResolver(
                                any()))
                                .thenReturn(resourceResolver);

                when(resourceResolver.getResource(ASSET_1))
                                .thenReturn(null);

                schedulerUnderTest.run();

                verify(resourceResolver)
                                .getResource(ASSET_1);

                verifyNoInteractions(
                                assetExpiryReportService,
                                assetExpiryNotificationService);
        }

        @Test
        void shouldSkipWhenMetadataIsMissing()
                        throws Exception {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(
                                                Collections.singletonList(
                                                                "admin@example.com"));

                when(resourceResolverFactory.getServiceResourceResolver(
                                any()))
                                .thenReturn(resourceResolver);

                when(resourceResolver.getResource(ASSET_1))
                                .thenReturn(assetResource);

                when(assetResource.getChild(METADATA_PATH))
                                .thenReturn(null);

                schedulerUnderTest.run();

                verify(assetResource)
                                .getChild(METADATA_PATH);

                verifyNoInteractions(
                                assetExpiryReportService,
                                assetExpiryNotificationService);
        }

        @Test
        void shouldProcessMultipleAssets()
                        throws Exception {

                List<String> assetPaths = Arrays.asList(
                                ASSET_1,
                                ASSET_2,
                                ASSET_3);

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(assetPaths);

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(
                                                Collections.singletonList(
                                                                "admin@example.com"));

                when(resourceResolverFactory.getServiceResourceResolver(
                                any()))
                                .thenReturn(resourceResolver);

                when(resourceResolver.getResource(anyString()))
                                .thenReturn(assetResource);

                when(assetResource.getChild(METADATA_PATH))
                                .thenReturn(metadataResource);

                when(metadataResource.getValueMap())
                                .thenReturn(metadataProperties);

                when(metadataProperties.get(
                                EXPIRATION_PROPERTY,
                                Calendar.class))
                                .thenReturn(toCalendar(EXPIRATION_DATE));

                when(expiryNotificationAuditService.wasNotificationSent(
                                any(Resource.class),
                                eq(EXPIRATION_DATE)))
                                .thenReturn(false);

                when(assetExpiryReportService.generateReport(
                                anyList(),
                                anyString(),
                                anyString()))
                                .thenReturn(new File("report.csv"));

                when(assetExpiryNotificationService.sendExpiryReport(
                                any(File.class),
                                any(String[].class),
                                anyInt()))
                                .thenReturn(true);

                schedulerUnderTest.run();

                verify(resourceResolver, times(3))
                                .getResource(anyString());

                verify(assetExpiryReportService)
                                .generateReport(
                                                anyList(),
                                                eq("author"),
                                                eq("http://localhost:4502"));
        }

        @Test
        void shouldSkipAssetWhenExpirationDateIsMissing()
                        throws Exception {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(
                                                Collections.singletonList(
                                                                "admin@example.com"));

                when(resourceResolverFactory.getServiceResourceResolver(
                                any()))
                                .thenReturn(resourceResolver);

                when(resourceResolver.getResource(ASSET_1))
                                .thenReturn(assetResource);

                when(assetResource.getChild(METADATA_PATH))
                                .thenReturn(metadataResource);

                when(metadataResource.getValueMap())
                                .thenReturn(metadataProperties);

                when(metadataProperties.get(
                                EXPIRATION_PROPERTY,
                                Calendar.class))
                                .thenReturn(null);

                schedulerUnderTest.run();

                verifyNoInteractions(
                                assetExpiryReportService,
                                assetExpiryNotificationService);
        }

        @Test
        void shouldSkipAlreadyReportedAsset()
                        throws Exception {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(
                                                Collections.singletonList(
                                                                "admin@example.com"));

                when(resourceResolverFactory.getServiceResourceResolver(
                                any()))
                                .thenReturn(resourceResolver);

                when(resourceResolver.getResource(ASSET_1))
                                .thenReturn(assetResource);

                when(assetResource.getChild(METADATA_PATH))
                                .thenReturn(metadataResource);

                when(metadataResource.getValueMap())
                                .thenReturn(metadataProperties);

                when(metadataProperties.get(
                                EXPIRATION_PROPERTY,
                                Calendar.class))
                                .thenReturn(toCalendar(EXPIRATION_DATE));

                when(expiryNotificationAuditService.wasNotificationSent(
                                assetResource,
                                EXPIRATION_DATE))
                                .thenReturn(true);

                schedulerUnderTest.run();

                verify(expiryNotificationAuditService)
                                .wasNotificationSent(
                                                assetResource,
                                                EXPIRATION_DATE);

                verifyNoInteractions(
                                assetExpiryReportService,
                                assetExpiryNotificationService);
        }

        @Test
        void shouldHandleReportGenerationReturningNull()
                        throws Exception {

                mockEligibleAsset();

                when(assetExpiryReportService.generateReport(
                                anyList(),
                                anyString(),
                                anyString()))
                                .thenReturn(null);

                schedulerUnderTest.run();

                verify(assetExpiryReportService)
                                .generateReport(
                                                anyList(),
                                                eq("author"),
                                                eq("http://localhost:4502"));

                verifyNoInteractions(
                                assetExpiryNotificationService);
        }

        @Test
        void shouldNotMarkTrackerWhenEmailFails()
                        throws Exception {

                mockEligibleAsset();

                File reportFile = new File("report.csv");

                when(assetExpiryReportService.generateReport(
                                anyList(),
                                anyString(),
                                anyString()))
                                .thenReturn(reportFile);

                when(assetExpiryNotificationService.sendExpiryReport(
                                any(File.class),
                                any(String[].class),
                                anyInt()))
                                .thenReturn(false);

                schedulerUnderTest.run();

                verify(assetExpiryNotificationService)
                                .sendExpiryReport(
                                                eq(reportFile),
                                                any(String[].class),
                                                anyInt());

                verify(expiryNotificationAuditService,
                                never())
                                .markNotificationSent(
                                                any(Resource.class),
                                                any(LocalDate.class));
        }

        @Test
        void shouldSendReportSuccessfully()
                        throws Exception {

                mockEligibleAsset();

                File reportFile = new File("report.csv");

                when(assetExpiryReportService.generateReport(
                                anyList(),
                                anyString(),
                                anyString()))
                                .thenReturn(reportFile);

                when(assetExpiryNotificationService.sendExpiryReport(
                                any(File.class),
                                any(String[].class),
                                anyInt()))
                                .thenReturn(true);

                schedulerUnderTest.run();

                verify(assetExpiryReportService)
                                .generateReport(
                                                anyList(),
                                                eq("author"),
                                                eq("http://localhost:4502"));

                verify(assetExpiryNotificationService)
                                .sendExpiryReport(
                                                eq(reportFile),
                                                any(String[].class),
                                                anyInt());

                verify(expiryNotificationAuditService)
                                .markNotificationSent(
                                                any(Resource.class),
                                                eq(EXPIRATION_DATE));
        }

        @Test
        void shouldHandleTrackerUpdateFailure()
                        throws Exception {

                mockEligibleAsset();

                File reportFile = new File("report.csv");

                when(assetExpiryReportService.generateReport(
                                anyList(),
                                anyString(),
                                anyString()))
                                .thenReturn(reportFile);

                when(assetExpiryNotificationService.sendExpiryReport(
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
                                () -> schedulerUnderTest.run());

                verify(assetExpiryNotificationService)
                                .sendExpiryReport(
                                                eq(reportFile),
                                                any(String[].class),
                                                anyInt());

                verify(expiryNotificationAuditService)
                                .markNotificationSent(
                                                any(Resource.class),
                                                eq(EXPIRATION_DATE));
        }

        @Test
        void shouldHandleTrackerUpdateException()
                        throws Exception {

                mockEligibleAsset();

                File reportFile = new File("report.csv");

                when(assetExpiryReportService.generateReport(
                                anyList(),
                                anyString(),
                                anyString()))
                                .thenReturn(reportFile);

                when(assetExpiryNotificationService.sendExpiryReport(
                                any(File.class),
                                any(String[].class),
                                anyInt()))
                                .thenReturn(true);

                doThrow(
                                new RuntimeException("Unexpected tracker error"))
                                .when(expiryNotificationAuditService)
                                .markNotificationSent(
                                                any(Resource.class),
                                                any(LocalDate.class));

                assertDoesNotThrow(
                                () -> schedulerUnderTest.run());
        }

        @Test
        void shouldProcessWithoutDeduplicationWhenDisabled()
                        throws Exception {

                setField(
                                "deduplicationEnabled",
                                false);

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(
                                                Collections.singletonList(
                                                                "admin@example.com"));

                when(resourceResolverFactory.getServiceResourceResolver(
                                any()))
                                .thenReturn(resourceResolver);

                when(resourceResolver.getResource(ASSET_1))
                                .thenReturn(assetResource);

                when(assetResource.getChild(METADATA_PATH))
                                .thenReturn(metadataResource);

                when(metadataResource.getValueMap())
                                .thenReturn(metadataProperties);

                when(metadataProperties.get(
                                EXPIRATION_PROPERTY,
                                Calendar.class))
                                .thenReturn(toCalendar(EXPIRATION_DATE));

                File reportFile = new File("report.csv");

                when(assetExpiryReportService.generateReport(
                                anyList(),
                                anyString(),
                                anyString()))
                                .thenReturn(reportFile);

                when(assetExpiryNotificationService.sendExpiryReport(
                                any(File.class),
                                any(String[].class),
                                anyInt()))
                                .thenReturn(true);

                schedulerUnderTest.run();

                verify(expiryNotificationAuditService,
                                never())
                                .wasNotificationSent(
                                                any(Resource.class),
                                                any(LocalDate.class));

                verify(assetExpiryReportService)
                                .generateReport(
                                                anyList(),
                                                eq("author"),
                                                eq("http://localhost:4502"));
        }

        @Test
        void shouldRunScheduler() {

                assertDoesNotThrow(
                                () -> schedulerUnderTest.run());
        }

        @Test
        void shouldDeactivateScheduler() {

                assertDoesNotThrow(
                                () -> schedulerUnderTest.deactivate());
        }

        private void mockEligibleAsset()
                        throws Exception {

                when(assetExpiryQueryService.findExpiringAssets(
                                eq(TEST_DAM_ROOT_PATH),
                                any(Instant.class),
                                any(Instant.class)))
                                .thenReturn(
                                                Collections.singletonList(ASSET_1));

                when(damAdminRecipientService.getRecipientEmails())
                                .thenReturn(
                                                Collections.singletonList(
                                                                "admin@example.com"));

                when(resourceResolverFactory.getServiceResourceResolver(
                                any()))
                                .thenReturn(resourceResolver);

                when(resourceResolver.getResource(ASSET_1))
                                .thenReturn(assetResource);

                when(assetResource.getChild(METADATA_PATH))
                                .thenReturn(metadataResource);

                when(metadataResource.getValueMap())
                                .thenReturn(metadataProperties);

                when(metadataProperties.get(
                                EXPIRATION_PROPERTY,
                                Calendar.class))
                                .thenReturn(toCalendar(EXPIRATION_DATE));

                when(expiryNotificationAuditService.wasNotificationSent(
                                assetResource,
                                EXPIRATION_DATE))
                                .thenReturn(false);
        }

        private Calendar toCalendar(LocalDate date) {

                Calendar calendar = Calendar.getInstance();

                calendar.set(
                                date.getYear(),
                                date.getMonthValue() - 1,
                                date.getDayOfMonth(),
                                0,
                                0,
                                0);

                calendar.set(Calendar.MILLISECOND, 0);

                return calendar;
        }

        private void setField(
                        String fieldName,
                        Object value)
                        throws Exception {

                Field field = AssetExpiryScheduler.class
                                .getDeclaredField(fieldName);

                field.setAccessible(true);

                field.set(
                                schedulerUnderTest,
                                value);
        }
}