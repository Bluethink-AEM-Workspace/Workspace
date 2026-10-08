package com.workspace.core.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.service.cm.ConfigurationEvent;

import com.workspace.core.services.SolrConfigurationService;
import com.workspace.core.services.SolrService;
import com.workspace.core.services.WebsiteCrawlerService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;
import com.workspace.core.services.WebsiteCrawlerService.WebsiteCrawlerException;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolrIndexConfigurationListenerTest {

    private static final String SOLR_CONFIGURATION_PID = "com.workspace.core.services.impl.SolrConfigurationServiceImpl";

    private static final String AEM_TARGET = "/content/workspace/language-masters/en";

    private static final String EXTERNAL_TARGET = "https://www.bluethinkinc.com";

    private static final String SECOND_EXTERNAL_TARGET = "https://en.wikipedia.org/wiki/Transformer";

    @Mock
    private SolrConfigurationService solrConfigurationService;

    @Mock
    private WebsiteCrawlerService websiteCrawlerService;

    @Mock
    private SolrService solrService;

    @Mock
    private Scheduler scheduler;

    @Mock
    private ScheduleOptions scheduleOptions;

    @Mock
    private ConfigurationEvent configurationEvent;

    @Mock
    private CrawledPage firstPage;

    @Mock
    private CrawledPage secondPage;

    @InjectMocks
    private SolrIndexConfigurationListener listener;

    @Test
    void shouldStartInitialExternalCrawlOnActivation()
            throws Exception {

        configureScheduler();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(EXTERNAL_TARGET);

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(websiteCrawlerService.crawl())
                .thenReturn(
                        Arrays.asList(
                                firstPage,
                                secondPage));

        when(firstPage.getUrl())
                .thenReturn(EXTERNAL_TARGET + "/");

        when(secondPage.getUrl())
                .thenReturn(EXTERNAL_TARGET + "/about");

        listener.activate();

        Runnable scheduledRunnable = captureScheduledRunnable();

        scheduledRunnable.run();

        verify(websiteCrawlerService)
                .crawl();

        verify(solrService)
                .indexExternalPage(firstPage);

        verify(solrService)
                .indexExternalPage(secondPage);
    }

    @Test
    void shouldStartCrawlWhenIndexingTargetChanges()
            throws Exception {

        configureScheduler();

        /*
         * Initial state is AEM.
         */
        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(
                        AEM_TARGET,
                        SECOND_EXTERNAL_TARGET);

        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);

        listener.activate();

        /*
         * Activation must not schedule an external crawl because
         * the initial target is an AEM path.
         */
        verify(scheduler, never())
                .schedule(
                        any(Runnable.class),
                        any(ScheduleOptions.class));

        /*
         * Configuration event represents the target changing from
         * AEM to an external website.
         */
        when(configurationEvent.getPid())
                .thenReturn(
                        SOLR_CONFIGURATION_PID);

        when(configurationEvent.getType())
                .thenReturn(
                        ConfigurationEvent.CM_UPDATED);

        /*
         * The new target is external.
         */
        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        listener.configurationEvent(
                configurationEvent);

        /*
         * A crawl must now be scheduled.
         */
        Runnable scheduledRunnable = captureScheduledRunnable();

        verify(scheduler)
                .schedule(
                        any(Runnable.class),
                        eq(scheduleOptions));

        /*
         * We intentionally don't execute the Runnable here.
         * This test verifies only that the target change schedules
         * a crawl.
         */
        if (scheduledRunnable == null) {
            throw new AssertionError(
                    "Expected scheduled crawl Runnable");
        }
    }

    @Test
    void shouldNotStartCrawlWhenOnlyOtherConfigurationValuesChange()
            throws Exception {

        configureNoInitialExternalCrawl();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(AEM_TARGET);

        listener.activate();

        when(configurationEvent.getPid())
                .thenReturn(
                        SOLR_CONFIGURATION_PID);

        when(configurationEvent.getType())
                .thenReturn(
                        ConfigurationEvent.CM_UPDATED);

        /*
         * Target remains unchanged.
         */
        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(AEM_TARGET);

        listener.configurationEvent(
                configurationEvent);

        verify(scheduler, never())
                .schedule(
                        any(Runnable.class),
                        any(ScheduleOptions.class));

        verify(websiteCrawlerService, never())
                .crawl();
    }

    @Test
    void shouldNotStartCrawlWhenTargetChangesToAemPath()
            throws Exception {

        configureNoInitialExternalCrawl();

        String secondAemTarget = "/content/workspace/language-masters/en/products";

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(
                        AEM_TARGET,
                        secondAemTarget);

        listener.activate();

        when(configurationEvent.getPid())
                .thenReturn(
                        SOLR_CONFIGURATION_PID);

        when(configurationEvent.getType())
                .thenReturn(
                        ConfigurationEvent.CM_UPDATED);

        /*
         * New target is an AEM path, not an external website.
         */
        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);

        listener.configurationEvent(
                configurationEvent);

        verify(scheduler, never())
                .schedule(
                        any(Runnable.class),
                        any(ScheduleOptions.class));

        verify(websiteCrawlerService, never())
                .crawl();
    }

    @Test
    void shouldIgnoreConfigurationEventForDifferentPid()
            throws Exception {

        configureNoInitialExternalCrawl();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(AEM_TARGET);

        listener.activate();

        when(configurationEvent.getPid())
                .thenReturn(
                        "some.other.configuration.pid");

        /*
         * The listener must stop immediately because the PID
         * does not belong to the Solr configuration.
         */
        listener.configurationEvent(
                configurationEvent);

        verify(scheduler, never())
                .schedule(
                        any(Runnable.class),
                        any(ScheduleOptions.class));

        verify(websiteCrawlerService, never())
                .crawl();
    }

    @Test
    void shouldIgnoreNonUpdateConfigurationEvent()
            throws Exception {

        configureNoInitialExternalCrawl();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(AEM_TARGET);

        listener.activate();

        when(configurationEvent.getPid())
                .thenReturn(
                        SOLR_CONFIGURATION_PID);

        when(configurationEvent.getType())
                .thenReturn(
                        ConfigurationEvent.CM_DELETED);

        listener.configurationEvent(
                configurationEvent);

        verify(scheduler, never())
                .schedule(
                        any(Runnable.class),
                        any(ScheduleOptions.class));

        verify(websiteCrawlerService, never())
                .crawl();
    }

    @Test
    void shouldNotStartExternalCrawlWhenNewTargetIsNotExternal()
            throws Exception {

        configureNoInitialExternalCrawl();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(
                        EXTERNAL_TARGET,
                        AEM_TARGET);

        listener.activate();

        when(configurationEvent.getPid())
                .thenReturn(
                        SOLR_CONFIGURATION_PID);

        when(configurationEvent.getType())
                .thenReturn(
                        ConfigurationEvent.CM_UPDATED);

        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);

        listener.configurationEvent(
                configurationEvent);

        verify(scheduler, never())
                .schedule(
                        any(Runnable.class),
                        any(ScheduleOptions.class));

        verify(websiteCrawlerService, never())
                .crawl();
    }

    @Test
    void shouldIndexEveryPageReturnedByCrawler()
            throws Exception {

        configureScheduler();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(EXTERNAL_TARGET);

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(websiteCrawlerService.crawl())
                .thenReturn(
                        Arrays.asList(
                                firstPage,
                                secondPage));

        when(firstPage.getUrl())
                .thenReturn(
                        EXTERNAL_TARGET + "/first");

        when(secondPage.getUrl())
                .thenReturn(
                        EXTERNAL_TARGET + "/second");

        listener.activate();

        Runnable scheduledRunnable = captureScheduledRunnable();

        scheduledRunnable.run();

        verify(solrService)
                .indexExternalPage(firstPage);

        verify(solrService)
                .indexExternalPage(secondPage);
    }

    @Test
    void shouldContinueIndexingWhenOnePageFails()
            throws Exception {

        configureScheduler();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(EXTERNAL_TARGET);

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(websiteCrawlerService.crawl())
                .thenReturn(
                        Arrays.asList(
                                firstPage,
                                secondPage));

        when(firstPage.getUrl())
                .thenReturn(
                        EXTERNAL_TARGET + "/first");

        when(secondPage.getUrl())
                .thenReturn(
                        EXTERNAL_TARGET + "/second");

        doThrow(
                new SolrIndexException(
                        "Failed to index first page"))
                .when(solrService)
                .indexExternalPage(firstPage);

        listener.activate();

        Runnable scheduledRunnable = captureScheduledRunnable();

        scheduledRunnable.run();

        verify(solrService)
                .indexExternalPage(firstPage);

        verify(solrService)
                .indexExternalPage(secondPage);
    }

    @Test
    void shouldNotIndexWhenCrawlerFails()
            throws Exception {

        configureScheduler();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(EXTERNAL_TARGET);

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(websiteCrawlerService.crawl())
                .thenThrow(
                        new WebsiteCrawlerException(
                                "Crawler failed"));

        listener.activate();

        Runnable scheduledRunnable = captureScheduledRunnable();

        scheduledRunnable.run();

        verify(websiteCrawlerService)
                .crawl();

        verify(solrService, never())
                .indexExternalPage(
                        any(CrawledPage.class));
    }

    @Test
    void shouldSkipCrawlIfTargetIsNoLongerExternalWhenScheduledJobRuns()
            throws Exception {

        configureScheduler();

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(EXTERNAL_TARGET);

        /*
         * The target is external when activation happens, so a
         * scheduled crawl must be created.
         */
        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        listener.activate();

        Runnable scheduledRunnable = captureScheduledRunnable();

        /*
         * The configuration changes before the scheduled Runnable
         * actually executes. It is now an AEM target.
         */
        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);

        scheduledRunnable.run();

        verify(websiteCrawlerService, never())
                .crawl();

        verify(solrService, never())
                .indexExternalPage(
                        any(CrawledPage.class));
    }

    private void configureScheduler() {

        when(scheduler.NOW())
                .thenReturn(scheduleOptions);

        when(scheduleOptions.canRunConcurrently(false))
                .thenReturn(scheduleOptions);
    }

    private void configureNoInitialExternalCrawl() {

        /*
         * Lenient test class configuration allows individual tests
         * to override this value when necessary without triggering
         * strict-stubbing failures.
         */
        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);
    }

    private Runnable captureScheduledRunnable() {

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        verify(scheduler)
                .schedule(
                        runnableCaptor.capture(),
                        eq(scheduleOptions));

        return runnableCaptor.getValue();
    }
}