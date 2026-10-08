package com.workspace.core.services;

public interface SolrConfigurationService {

    String getAemBaseUrl();

    String getSolrBaseUrl();

    String getIndexingTarget();

    int getExternalCrawlMaxPages();

    int getExternalCrawlConnectionTimeout();

    boolean isAemIndexingTarget();

    boolean isExternalIndexingTarget();
}
