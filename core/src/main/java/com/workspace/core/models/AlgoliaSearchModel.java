package com.workspace.core.models;

import com.workspace.core.services.impl.AlgoliaIndexingServiceImpl;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;

import javax.annotation.PostConstruct;

@Model(adaptables = SlingHttpServletRequest.class)
public class AlgoliaSearchModel {

    @OSGiService
    private AlgoliaIndexingServiceImpl algoliaIndexingService;

    private String appId;
    private String searchApiKey;
    private String indexName;

    @PostConstruct
    protected void init() {
        if (algoliaIndexingService != null) {
            this.appId = algoliaIndexingService.getAppId();
            this.searchApiKey = algoliaIndexingService.getSearchApiKey();
            this.indexName = algoliaIndexingService.getIndexName();
        }
    }

    public String getAppId() { return appId; }
    public String getSearchApiKey() { return searchApiKey; }
    public String getIndexName() { return indexName; }
}