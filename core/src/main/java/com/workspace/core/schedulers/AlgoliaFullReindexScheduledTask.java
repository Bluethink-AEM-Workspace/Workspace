package com.workspace.core.schedulers;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.workspace.core.services.impl.AlgoliaIndexingServiceImpl;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;

@Component(immediate = true, service = Runnable.class, property = {
    "scheduler.expression=0 0 0 1/1 * ? *" // Cron expression: Runs daily or trigger manually
})
public class AlgoliaFullReindexScheduledTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(AlgoliaFullReindexScheduledTask.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private AlgoliaIndexingServiceImpl algoliaIndexingService;

    @Override
    public void run() {
        LOG.info("Starting Full Reindexing Job for Algolia...");
        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(
                Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "algoliaServiceUser"))) {

            PageManager pageManager = resolver.adaptTo(PageManager.class);
            if (pageManager != null) {
                Page rootPage = pageManager.getPage("/content/workspace");
                if (rootPage != null) {
                    crawlAndIndex(rootPage);
                }
            }
        } catch (Exception e) {
            LOG.error("Error during Algolia Full Reindexing", e);
        }
    }

    private void crawlAndIndex(Page page) {
        try {
            // Indexing service method call karein
            algoliaIndexingService.indexPage(page.getPath());
        } catch (Exception e) {
            LOG.error("Failed to index page during full reindex: {}", page.getPath(), e);
        }
        
        // Recursive crawling for child pages
        Iterator<Page> children = page.listChildren();
        while (children.hasNext()) {
            crawlAndIndex(children.next());
        }
    }
}