package com.workspace.core.services.impl;

import java.net.URI;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workspace.core.config.SolrIndexConfiguration;
import com.workspace.core.services.SolrConfigurationService;

@Component(service = SolrConfigurationService.class, immediate = true)
@Designate(ocd = SolrIndexConfiguration.class)
public class SolrConfigurationServiceImpl
        implements SolrConfigurationService {

    private static final Logger LOG = LoggerFactory.getLogger(
            SolrConfigurationServiceImpl.class);

    private volatile String aemBaseUrl;

    private volatile String solrBaseUrl;

    private volatile String indexingTarget;

    private volatile int externalCrawlMaxPages;

    private volatile int externalCrawlConnectionTimeout;

    @Activate
    protected void activate(
            SolrIndexConfiguration configuration) {

        updateConfiguration(configuration);

        LOG.info(
                "Solr configuration service activated");
    }

    @Modified
    protected void modified(
            SolrIndexConfiguration configuration) {

        updateConfiguration(configuration);

        LOG.info(
                "Solr configuration updated at runtime");
    }

    private void updateConfiguration(
            SolrIndexConfiguration configuration) {

        aemBaseUrl = removeTrailingSlash(
                configuration.aemBaseUrl());

        solrBaseUrl = removeTrailingSlash(
                configuration.solrBaseUrl());

        indexingTarget = normalizeIndexingTarget(
                configuration.indexingTarget());

        externalCrawlMaxPages = configuration.externalCrawlMaxPages();

        externalCrawlConnectionTimeout = configuration.externalCrawlConnectionTimeout();

        validateIndexingTarget();

        LOG.info(
                "AEM base URL: {}",
                aemBaseUrl);

        LOG.info(
                "Solr base URL: {}",
                solrBaseUrl);

        LOG.info(
                "Indexing target: {}",
                indexingTarget);

        LOG.info(
                "External crawl maximum pages: {}",
                externalCrawlMaxPages);

        LOG.info(
                "External crawl connection timeout: {} ms",
                externalCrawlConnectionTimeout);
    }

    @Override
    public String getAemBaseUrl() {
        return aemBaseUrl;
    }

    @Override
    public String getSolrBaseUrl() {
        return solrBaseUrl;
    }

    @Override
    public String getIndexingTarget() {
        return indexingTarget;
    }

    @Override
    public int getExternalCrawlMaxPages() {
        return externalCrawlMaxPages;
    }

    @Override
    public int getExternalCrawlConnectionTimeout() {
        return externalCrawlConnectionTimeout;
    }

    @Override
    public boolean isAemIndexingTarget() {

        return indexingTarget != null
                && ("/content".equals(indexingTarget)
                        || indexingTarget.startsWith("/content/"));
    }

    @Override
    public boolean isExternalIndexingTarget() {

        return isValidExternalTarget(
                indexingTarget);
    }

    private void validateIndexingTarget() {

        if (isAemIndexingTarget()) {

            LOG.info(
                    "Solr indexing mode: AEM");

            return;
        }

        if (isExternalIndexingTarget()) {

            LOG.info(
                    "Solr indexing mode: EXTERNAL");

            return;
        }

        throw new IllegalArgumentException(
                "Invalid Solr indexing target: "
                        + indexingTarget);
    }

    private boolean isValidExternalTarget(
            String target) {

        if (target == null
                || target.trim().isEmpty()) {

            return false;
        }

        try {

            URI uri = new URI(
                    target.trim());

            String scheme = uri.getScheme();

            String host = uri.getHost();

            return ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))
                    && host != null
                    && !host.trim().isEmpty();

        } catch (Exception e) {

            LOG.warn(
                    "Invalid external Solr indexing target: {}",
                    target);

            return false;
        }
    }

    private String normalizeIndexingTarget(
            String target) {

        if (target == null) {
            return "";
        }

        String normalized = target.trim();

        if (normalized.startsWith("/")) {

            return removeTrailingSlash(
                    normalized);
        }

        return normalized;
    }

    private String removeTrailingSlash(
            String value) {

        if (value == null) {
            return "";
        }

        String result = value.trim();

        while (result.length() > 1
                && result.endsWith("/")) {

            result = result.substring(
                    0,
                    result.length() - 1);
        }

        return result;
    }
}