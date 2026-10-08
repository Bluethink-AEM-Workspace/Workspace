package com.workspace.core.models;

public class SolrSearchResult {

    private final String id;
    private final String pagePath;
    private final String pageUrl;
    private final String pageTitle;
    private final String pageContent;
    private final String source;

    public SolrSearchResult(
            String id,
            String pagePath,
            String pageUrl,
            String pageTitle,
            String pageContent,
            String source) {

        this.id = id;
        this.pagePath = pagePath;
        this.pageUrl = pageUrl;
        this.pageTitle = pageTitle;
        this.pageContent = pageContent;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getPagePath() {
        return pagePath;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public String getPageContent() {
        return pageContent;
    }

    public String getSource() {
        return source;
    }
}