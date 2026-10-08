package com.workspace.core.listeners;

import com.workspace.core.services.SolrService;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.observation.ResourceChangeListener;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ResourceChangeListener.class, property = {
                ResourceChangeListener.PATHS
                                + "=/content",
                ResourceChangeListener.CHANGES
                                + "=ADDED",
                ResourceChangeListener.CHANGES
                                + "=CHANGED",
                ResourceChangeListener.CHANGES
                                + "=REMOVED"
})
public class SolrIndexChangeListener
                implements ResourceChangeListener {

        private static final Logger LOG = LoggerFactory.getLogger(
                        SolrIndexChangeListener.class);

        private static final String PAGE_PRIMARY_TYPE = "cq:Page";

        private static final String JCR_PRIMARY_TYPE = "jcr:primaryType";

        private static final String JCR_CONTENT = "jcr:content";

        private static final String SUBSERVICE = "solr-index-service";

        @Reference
        private SolrService solrService;

        @Reference
        private ResourceResolverFactory resourceResolverFactory;

        private Map<String, Object> serviceUserParams;

        @Activate
        protected void activate() {

                serviceUserParams = new HashMap<>();

                serviceUserParams.put(
                                ResourceResolverFactory.SUBSERVICE,
                                SUBSERVICE);

                LOG.info(
                                "Solr index change listener activated");

                LOG.info(
                                "Configured Solr indexing target: {}",
                                solrService.getIndexingTarget());
        }

        @Override
        public void onChange(
                        List<ResourceChange> changes) {

                /*
                 * AEM resource changes should only trigger automatic
                 * indexing when the configured target is an AEM path.
                 *
                 * If the configured target is an external website,
                 * the WebsiteCrawlerService is responsible for indexing
                 * that website instead.
                 */
                if (!solrService.isAemIndexingTarget()) {

                        LOG.debug(
                                        "Ignoring AEM resource changes because "
                                                        + "Solr indexing target is external: {}",
                                        solrService.getIndexingTarget());

                        return;
                }

                try (ResourceResolver resourceResolver = resourceResolverFactory
                                .getServiceResourceResolver(
                                                serviceUserParams)) {

                        for (ResourceChange change : changes) {

                                processChange(
                                                change,
                                                resourceResolver);
                        }

                } catch (LoginException e) {

                        LOG.error(
                                        "Failed to obtain service resource resolver "
                                                        + "for Solr indexing",
                                        e);
                }
        }

        private void processChange(
                        ResourceChange change,
                        ResourceResolver resourceResolver) {

                String changedPath = change.getPath();

                ChangeType changeType = change.getType();

                if (!isWithinContentRoot(
                                changedPath)) {

                        LOG.debug(
                                        "Ignoring resource change outside "
                                                        + "configured Solr indexing target: {}",
                                        changedPath);

                        return;
                }

                if (changeType == ChangeType.REMOVED) {

                        handleRemovedResource(
                                        changedPath,
                                        resourceResolver);

                        return;
                }

                Resource changedResource = resourceResolver.getResource(
                                changedPath);

                if (changedResource == null) {

                        LOG.debug(
                                        "Changed resource could not be resolved: {}",
                                        changedPath);

                        return;
                }

                Resource pageResource = findContainingPage(
                                changedResource);

                if (pageResource == null) {

                        LOG.debug(
                                        "No containing cq:Page found "
                                                        + "for changed resource: {}",
                                        changedPath);

                        return;
                }

                indexPage(pageResource);
        }

        /**
         * Checks whether the changed path belongs to the
         * configured Solr indexing target.
         *
         * <p>
         * The configured root itself is considered valid,
         * as are all resources below it.
         * </p>
         */
        private boolean isWithinContentRoot(
                        String path) {

                String indexingTarget = solrService.getIndexingTarget();

                if (indexingTarget == null
                                || indexingTarget.trim().isEmpty()) {

                        LOG.warn(
                                        "Solr indexing target is not configured");

                        return false;
                }

                String normalizedRoot = removeTrailingSlash(
                                indexingTarget.trim());

                return path.equals(
                                normalizedRoot)
                                || path.startsWith(
                                                normalizedRoot + "/");
        }

        /**
         * Handles a removed resource.
         *
         * <p>
         * If the removed resource was the page itself,
         * the corresponding Solr document is deleted.
         * </p>
         *
         * <p>
         * If a child resource was removed, the containing
         * page is re-indexed because its content may have changed.
         * </p>
         */
        private void handleRemovedResource(
                        String removedPath,
                        ResourceResolver resourceResolver) {

                if (isPagePath(removedPath)) {

                        deleteFromSolr(
                                        removedPath);

                        return;
                }

                String pagePath = findPagePathFromRemovedResource(
                                removedPath,
                                resourceResolver);

                if (pagePath == null) {

                        LOG.debug(
                                        "Could not determine containing page "
                                                        + "for removed resource: {}",
                                        removedPath);

                        return;
                }

                Resource pageResource = resourceResolver.getResource(
                                pagePath);

                if (pageResource != null) {

                        indexPage(pageResource);

                } else {

                        deleteFromSolr(
                                        pagePath);
                }
        }

        /**
         * Walks upward through the resource tree until
         * an AEM cq:Page is found.
         */
        private Resource findContainingPage(
                        Resource resource) {

                Resource current = resource;

                while (current != null) {

                        String primaryType = current.getValueMap().get(
                                        JCR_PRIMARY_TYPE,
                                        String.class);

                        if (PAGE_PRIMARY_TYPE.equals(
                                        primaryType)) {

                                return current;
                        }

                        current = current.getParent();
                }

                return null;
        }

        /**
         * Finds the containing page path for a resource
         * that has already been removed.
         *
         * <p>
         * Since the removed resource no longer exists,
         * the method walks upward through the path and
         * checks the remaining parent resources.
         * </p>
         */
        private String findPagePathFromRemovedResource(
                        String removedPath,
                        ResourceResolver resourceResolver) {

                String currentPath = removedPath;

                while (currentPath != null
                                && isWithinContentRoot(
                                                currentPath)) {

                        int lastSlash = currentPath.lastIndexOf('/');

                        if (lastSlash <= 0) {
                                break;
                        }

                        currentPath = currentPath.substring(
                                        0,
                                        lastSlash);

                        Resource resource = resourceResolver.getResource(
                                        currentPath);

                        if (resource == null) {
                                continue;
                        }

                        String primaryType = resource.getValueMap().get(
                                        JCR_PRIMARY_TYPE,
                                        String.class);

                        if (PAGE_PRIMARY_TYPE.equals(
                                        primaryType)) {

                                return currentPath;
                        }
                }

                return null;
        }

        /**
         * Determines whether a removed path represents
         * an AEM page.
         *
         * <p>
         * A page path itself does not contain jcr:content.
         * </p>
         */
        private boolean isPagePath(
                        String path) {

                return isWithinContentRoot(path)
                                && !path.contains(
                                                "/" + JCR_CONTENT + "/")
                                && !path.endsWith(
                                                "/" + JCR_CONTENT);
        }

        /**
         * Indexes an AEM page in Solr.
         */
        private void indexPage(
                        Resource pageResource) {

                try {

                        LOG.info(
                                        "Automatically indexing AEM page in Solr: {}",
                                        pageResource.getPath());

                        solrService.indexPage(
                                        pageResource);

                } catch (SolrIndexException e) {

                        LOG.error(
                                        "Failed to automatically index AEM page in Solr: {}",
                                        pageResource.getPath(),
                                        e);
                }
        }

        /**
         * Deletes an AEM page from Solr.
         */
        private void deleteFromSolr(
                        String pagePath) {

                try {

                        LOG.info(
                                        "Automatically deleting AEM page from Solr: {}",
                                        pagePath);

                        solrService.deleteDocument(
                                        pagePath);

                } catch (SolrIndexException e) {

                        LOG.error(
                                        "Failed to delete AEM page from Solr: {}",
                                        pagePath,
                                        e);
                }
        }

        /**
         * Removes a trailing slash from a repository path.
         */
        private String removeTrailingSlash(
                        String path) {

                if (path == null) {
                        return "";
                }

                if (path.length() > 1
                                && path.endsWith("/")) {

                        return path.substring(
                                        0,
                                        path.length() - 1);
                }

                return path;
        }
}