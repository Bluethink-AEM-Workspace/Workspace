package com.workspace.core.models;

import java.util.List;

public class SolrSearchResponse {

    private final String query;
    private final long totalResults;
    private final List<SolrSearchResult> results;

    public SolrSearchResponse(
            String query,
            long totalResults,
            List<SolrSearchResult> results) {

        this.query = query;
        this.totalResults = totalResults;
        this.results = results;
    }

    public String getQuery() {
        return query;
    }

    public long getTotalResults() {
        return totalResults;
    }

    public List<SolrSearchResult> getResults() {
        return results;
    }
}