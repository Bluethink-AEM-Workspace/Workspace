package com.workspace.core.services;

import com.workspace.core.models.SolrSearchResponse;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;

import org.apache.sling.api.resource.Resource;

public interface SolrService {

        /**
         * Indexes an AEM page/resource in Solr.
         *
         * @param resource AEM page resource
         * @throws SolrIndexException if the resource cannot be indexed
         */
        void indexPage(Resource resource)
                        throws SolrIndexException;

        /**
         * Indexes an externally crawled website page in Solr.
         *
         * @param page crawled external website page
         * @throws SolrIndexException if the page cannot be indexed
         */
        void indexExternalPage(CrawledPage page)
                        throws SolrIndexException;

        /**
         * Deletes a document from Solr using its document ID.
         *
         * @param documentId Solr document ID
         * @throws SolrIndexException if the document cannot be deleted
         */
        void deleteDocument(String documentId)
                        throws SolrIndexException;

        /**
         * Returns the configured indexing target.
         *
         * <p>
         * The target can be either an AEM content path such as
         * {@code /content/workspace} or an external website URL such as
         * {@code https://www.bluethinkinc.com}.
         * </p>
         *
         * @return configured AEM content path or external website URL
         */
        String getIndexingTarget();

        /**
         * Determines whether the configured indexing target
         * is an AEM content path.
         *
         * @return true when the target is an AEM content path
         */
        boolean isAemIndexingTarget();

        /**
         * Determines whether the configured indexing target
         * is a valid external HTTP/HTTPS URL.
         *
         * @return true when the target is a valid external URL
         */
        boolean isExternalIndexingTarget();

        /**
         * Searches indexed AEM pages in Solr.
         *
         * @param query search query entered by the user
         * @return search response containing matching pages
         * @throws SolrIndexException if the search request fails
         */
        SolrSearchResponse searchPages(String query)
                        throws SolrIndexException;
}