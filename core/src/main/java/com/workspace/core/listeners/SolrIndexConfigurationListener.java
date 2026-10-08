package com.workspace.core.listeners;

import java.util.List;

import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workspace.core.services.SolrConfigurationService;
import com.workspace.core.services.SolrService;
import com.workspace.core.services.WebsiteCrawlerService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;
import com.workspace.core.services.WebsiteCrawlerService.WebsiteCrawlerException;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

@Component(service = ConfigurationListener.class, immediate = true)
public class SolrIndexConfigurationListener
        implements ConfigurationListener {

    private static final Logger LOG = LoggerFactory.getLogger(
            SolrIndexConfigurationListener.class);

    private static final String SOLR_CONFIGURATION_PID = "com.workspace.core.services.impl.SolrConfigurationServiceImpl";

    @Reference
    private SolrConfigurationService solrConfigurationService;

    @Reference
    private WebsiteCrawlerService websiteCrawlerService;

    @Reference
    private SolrService solrService;

    @Reference
    private Scheduler scheduler;

    private volatile boolean active;

    private volatile String lastObservedIndexingTarget;

    /*
     * Prevents multiple external crawls from running at the
     * same time.
     */
    private boolean crawlRunning;

    /*
     * If the indexing target changes while a crawl is already
     * running, this flag ensures the latest configuration is
     * processed after the current crawl finishes.
     */
    private boolean crawlPending;

    @Activate
    protected void activate() {

        active = true;

        lastObservedIndexingTarget = solrConfigurationService.getIndexingTarget();

        LOG.info(
                "==================================================");

        LOG.info(
                "SolrIndexConfigurationListener ACTIVATED");

        LOG.info(
                "Current indexing target: {}",
                lastObservedIndexingTarget);

        LOG.info(
                "Current indexing mode - AEM: {}, External: {}",
                solrService.isAemIndexingTarget(),
                solrService.isExternalIndexingTarget());

        LOG.info(
                "==================================================");

        /*
         * Automatically start an external crawl when the listener
         * becomes active and an external target is already configured.
         *
         * This covers:
         * - AEM deployment
         * - Bundle restart
         * - AEM restart
         */
        if (solrService.isExternalIndexingTarget()) {

            requestExternalCrawl(
                    "Initial listener activation");
        }
    }

    @Deactivate
    protected void deactivate() {

        active = false;

        synchronized (this) {
            crawlPending = false;
        }

        LOG.info(
                "SolrIndexConfigurationListener DEACTIVATED");
    }

    @Override
    public void configurationEvent(
            ConfigurationEvent event) {

        /*
         * This must be the first diagnostic log.
         *
         * If this message appears after changing the OSGi
         * configuration, we know ConfigurationAdmin is delivering
         * the event to this listener.
         */
        LOG.info(
                "Received OSGi configuration event. PID: {}, Type: {}",
                event != null
                        ? event.getPid()
                        : "null",
                event != null
                        ? event.getType()
                        : "null");

        if (!active) {

            LOG.warn(
                    "Ignoring configuration event because "
                            + "SolrIndexConfigurationListener is inactive");

            return;
        }

        if (event == null) {
            return;
        }

        /*
         * Ignore configuration events belonging to other
         * components.
         */
        if (!SOLR_CONFIGURATION_PID.equals(
                event.getPid())) {

            LOG.debug(
                    "Ignoring unrelated configuration event. PID: {}",
                    event.getPid());

            return;
        }

        /*
         * We only need to react when the Solr configuration
         * is actually updated.
         */
        if (event.getType() != ConfigurationEvent.CM_UPDATED) {

            LOG.debug(
                    "Ignoring configuration event type: {}",
                    event.getType());

            return;
        }

        String currentTarget = solrConfigurationService.getIndexingTarget();

        String previousTarget = lastObservedIndexingTarget;

        LOG.info(
                "Solr configuration updated. "
                        + "Previous indexing target: {}, "
                        + "Current indexing target: {}",
                previousTarget,
                currentTarget);

        /*
         * Always store the latest target.
         *
         * This is important because changes to other OSGi
         * properties may generate CM_UPDATED events too.
         */
        lastObservedIndexingTarget = currentTarget;

        /*
         * Only an actual target change should trigger the
         * automatic external crawl.
         */
        if (sameValue(
                previousTarget,
                currentTarget)) {

            LOG.info(
                    "Indexing target did not change. "
                            + "Automatic external crawl will not start.");

            return;
        }

        LOG.info(
                "==================================================");

        LOG.info(
                "SOLR INDEXING TARGET CHANGED");

        LOG.info(
                "Previous target: {}",
                previousTarget);

        LOG.info(
                "New target: {}",
                currentTarget);

        LOG.info(
                "==================================================");

        /*
         * If the new target is an AEM repository path, the
         * AEM ResourceChangeListener handles page indexing.
         */
        if (!solrService.isExternalIndexingTarget()) {

            LOG.info(
                    "New indexing target is not an external URL. "
                            + "External crawler will not start.");

            return;
        }

        requestExternalCrawl(
                "Indexing target changed");
    }

    private void requestExternalCrawl(
            String reason) {

        synchronized (this) {

            if (!active) {

                LOG.warn(
                        "Cannot start external crawl because "
                                + "the listener is inactive. Reason: {}",
                        reason);

                return;
            }

            /*
             * Never start two crawls simultaneously.
             */
            if (crawlRunning) {

                crawlPending = true;

                LOG.info(
                        "External crawl is already running. "
                                + "A follow-up crawl has been queued. Reason: {}",
                        reason);

                return;
            }

            crawlRunning = true;
        }

        LOG.info(
                "Requesting automatic external crawl. Reason: {}",
                reason);

        scheduleExternalCrawl();
    }

    private void scheduleExternalCrawl() {

        try {

            ScheduleOptions options = scheduler
                    .NOW()
                    .canRunConcurrently(false);

            boolean scheduled = scheduler.schedule(
                    new Runnable() {

                        @Override
                        public void run() {

                            runExternalCrawl();
                        }
                    },
                    options);

            if (!scheduled) {

                synchronized (this) {
                    crawlRunning = false;
                }

                LOG.error(
                        "Sling Scheduler rejected the automatic "
                                + "external crawl");

                return;
            }

            LOG.info(
                    "Automatic external crawl successfully scheduled");

        } catch (Exception e) {

            synchronized (this) {
                crawlRunning = false;
            }

            LOG.error(
                    "Failed to schedule automatic external crawl",
                    e);
        }
    }

    private void runExternalCrawl() {

        int crawledCount = 0;

        int indexedCount = 0;

        int failedCount = 0;

        String target = solrConfigurationService.getIndexingTarget();

        LOG.info(
                "==================================================");

        LOG.info(
                "STARTING AUTOMATIC EXTERNAL CRAWL");

        LOG.info(
                "Target: {}",
                target);

        LOG.info(
                "==================================================");

        try {

            /*
             * Configuration may have changed between scheduling
             * and actual execution.
             *
             * Never crawl an AEM target as an external website.
             */
            if (!solrService.isExternalIndexingTarget()) {

                LOG.info(
                        "Automatic external crawl skipped because "
                                + "the current indexing target is no longer external: {}",
                        target);

                return;
            }

            List<CrawledPage> crawledPages = websiteCrawlerService.crawl();

            crawledCount = crawledPages.size();

            LOG.info(
                    "Automatic crawler returned {} pages",
                    crawledCount);

            for (CrawledPage page : crawledPages) {

                if (page == null) {

                    failedCount++;

                    LOG.warn(
                            "Crawler returned a null page. "
                                    + "Skipping indexing.");

                    continue;
                }

                String pageUrl = page.getUrl();

                try {

                    LOG.info(
                            "Indexing crawled external page: {}",
                            pageUrl);

                    solrService.indexExternalPage(
                            page);

                    indexedCount++;

                } catch (SolrIndexException e) {

                    failedCount++;

                    LOG.error(
                            "Failed to index crawled external page: {}",
                            pageUrl,
                            e);
                }
            }

            LOG.info(
                    "==================================================");

            LOG.info(
                    "AUTOMATIC EXTERNAL INDEXING COMPLETED");

            LOG.info(
                    "Target: {}",
                    target);

            LOG.info(
                    "Crawled pages: {}",
                    crawledCount);

            LOG.info(
                    "Indexed pages: {}",
                    indexedCount);

            LOG.info(
                    "Failed pages: {}",
                    failedCount);

            LOG.info(
                    "==================================================");

        } catch (WebsiteCrawlerException e) {

            LOG.error(
                    "Automatic external website crawl failed: {}",
                    target,
                    e);

        } catch (Exception e) {

            LOG.error(
                    "Unexpected error during automatic external indexing: {}",
                    target,
                    e);

        } finally {

            schedulePendingCrawlIfRequired();
        }
    }

    private void schedulePendingCrawlIfRequired() {

        boolean scheduleAgain;

        synchronized (this) {

            if (active
                    && crawlPending
                    && solrService.isExternalIndexingTarget()) {

                /*
                 * Clear the pending flag and keep crawlRunning=true
                 * while the next crawl is scheduled.
                 */
                crawlPending = false;

                scheduleAgain = true;

            } else {

                crawlPending = false;

                crawlRunning = false;

                scheduleAgain = false;
            }
        }

        if (scheduleAgain) {

            LOG.info(
                    "A new indexing target/configuration change "
                            + "occurred during the previous crawl. "
                            + "Scheduling the latest external crawl.");

            scheduleExternalCrawl();
        }
    }

    private boolean sameValue(
            String first,
            String second) {

        if (first == null) {
            return second == null;
        }

        return first.equals(second);
    }
}