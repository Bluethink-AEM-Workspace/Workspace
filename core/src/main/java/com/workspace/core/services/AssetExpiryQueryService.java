/*

AssetExpiryQueryService is responsible for querying the AEM DAM for assets whose prism:expirationDate falls within the configured notification window. It uses AEM QueryBuilder with a service ResourceResolver and JCR Session to efficiently search for dam:Asset resources under the configured DAM root path, applying an inclusive expiration-date range. The service returns only asset paths to avoid using resources tied to a closed resolver/session and includes validation, default path handling, resource cleanup, and exception handling to ensure that individual query-result failures do not interrupt processing of the remaining assets.

*/


package com.workspace.core.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;

@Component(service = AssetExpiryQueryService.class)
public class AssetExpiryQueryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(AssetExpiryQueryService.class);

    private static final String DEFAULT_DAM_ROOT =
            "/content/dam/workspace";

    private static final String EXPIRATION_PROPERTY =
            "jcr:content/metadata/prism:expirationDate";

    private static final String SUBSERVICE =
            "asset-expiry-service";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private QueryBuilder queryBuilder;

    /**
     * Finds DAM asset paths whose expiration date is between
     * windowStart and windowEnd, inclusive.
     *
     * Only asset paths are returned instead of Resource objects.
     * This prevents resources backed by a closed ResourceResolver/JCR
     * session from being used later by the scheduler.
     *
     * @param damRootPath DAM root path under which assets are searched
     * @param windowStart start of notification window
     * @param windowEnd end of notification window
     * @return list of matching DAM asset paths
     */
    public List<String> findExpiringAssets(
            String damRootPath,
            Instant windowStart,
            Instant windowEnd) {

        if (windowStart == null || windowEnd == null) {
            LOG.warn(
                    "Cannot query expiring assets: date window is null");

            return Collections.emptyList();
        }

        if (windowEnd.isBefore(windowStart)) {
            LOG.warn(
                    "Cannot query expiring assets: windowEnd {} is before windowStart {}",
                    windowEnd,
                    windowStart);

            return Collections.emptyList();
        }

        String queryRootPath = damRootPath;

        if (queryRootPath == null
                || queryRootPath.trim().isEmpty()) {

            LOG.warn(
                    "DAM root path is empty. Using default {}",
                    DEFAULT_DAM_ROOT);

            queryRootPath = DEFAULT_DAM_ROOT;
        }

        queryRootPath = queryRootPath.trim();

        List<String> expiringAssetPaths =
                new ArrayList<String>();

        try (ResourceResolver resourceResolver =
                     getServiceResourceResolver()) {

            Session session =
                    resourceResolver.adaptTo(Session.class);

            if (session == null) {
                LOG.error(
                        "Unable to adapt ResourceResolver to JCR Session");

                return Collections.emptyList();
            }

            Map<String, String> predicates =
                    buildQueryPredicates(
                            queryRootPath,
                            windowStart,
                            windowEnd);

            Query query =
                    queryBuilder.createQuery(
                            PredicateGroup.create(predicates),
                            session);

            SearchResult searchResult =
                    query.getResult();

            LOG.info(
                    "Asset expiry query returned {} matching assets under {}",
                    searchResult.getHits().size(),
                    queryRootPath);

            for (Hit hit : searchResult.getHits()) {

                try {
                    if (hit == null) {
                        LOG.warn(
                                "Skipping null query result");

                        continue;
                    }

                    String assetPath =
                            hit.getPath();

                    if (assetPath == null
                            || assetPath.trim().isEmpty()) {

                        LOG.warn(
                                "Skipping query result because asset path is null or empty");

                        continue;
                    }

                    expiringAssetPaths.add(
                            assetPath);

                    LOG.debug(
                            "Found expiring asset path: {}",
                            assetPath);

                } catch (Exception e) {

                    /**
                     * One bad query result must not prevent the
                     * remaining assets from being processed.
                     */
                    LOG.error(
                            "Failed to process expiry query result",
                            e);
                }
            }

        } catch (Exception e) {

            LOG.error(
                    "Unable to query expiring DAM assets under {}",
                    queryRootPath,
                    e);
        }

        return expiringAssetPaths;
    }

    /**
     * Builds a bounded QueryBuilder date range.
     *
     * TDD requirement:
     *
     *     windowStart <= expirationDate <= windowEnd
     *
     * @param damRootPath DAM root path
     * @param windowStart start of notification window
     * @param windowEnd end of notification window
     * @return QueryBuilder predicates
     */
    private Map<String, String> buildQueryPredicates(
            String damRootPath,
            Instant windowStart,
            Instant windowEnd) {

        Map<String, String> predicates =
                new HashMap<String, String>();

        predicates.put(
                "path",
                damRootPath);

        predicates.put(
                "type",
                "dam:Asset");

        predicates.put(
                "1_daterange.property",
                EXPIRATION_PROPERTY);

        predicates.put(
                "1_daterange.lowerBound",
                windowStart.toString());

        predicates.put(
                "1_daterange.lowerOperation",
                ">=");

        predicates.put(
                "1_daterange.upperBound",
                windowEnd.toString());

        predicates.put(
                "1_daterange.upperOperation",
                "<=");

        predicates.put(
                "p.limit",
                "-1");

        return predicates;
    }

    /**
     * Gets a service ResourceResolver using the
     * asset-expiry-service subservice.
     *
     * @return service ResourceResolver
     * @throws Exception if resolver creation fails
     */
    private ResourceResolver getServiceResourceResolver()
            throws Exception {

        Map<String, Object> authInfo =
                new HashMap<String, Object>();

        authInfo.put(
                ResourceResolverFactory.SUBSERVICE,
                SUBSERVICE);

        return resourceResolverFactory
                .getServiceResourceResolver(authInfo);
    }
}

