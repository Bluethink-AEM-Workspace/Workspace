package com.workspace.core.servlets;

import com.workspace.core.services.SolrService;
import com.workspace.core.services.WebsiteCrawlerService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;
import com.workspace.core.services.WebsiteCrawlerService.WebsiteCrawlerException;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

import java.io.IOException;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet used to manually trigger external website crawling
 * and Solr indexing.
 *
 * Endpoint:
 * POST /bin/workspace/solr/external-index
 */
@Component(service = Servlet.class)
@SlingServletPaths("/bin/workspace/solr/external-index")
public class ExternalSolrIndexServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(ExternalSolrIndexServlet.class);

    @Reference
    private WebsiteCrawlerService websiteCrawlerService;

    @Reference
    private SolrService solrService;

    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        LOG.info("External Solr indexing request received");

        /*
         * The servlet should only be used when the configured
         * indexing target is an external website.
         */
        if (!solrService.isExternalIndexingTarget()) {

            LOG.info(
                    "External Solr indexing request skipped. "
                            + "Configured target is not external: {}",
                    solrService.getIndexingTarget());

            response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST);

            response.getWriter().write(
                    "{"
                            + "\"status\":\"error\","
                            + "\"message\":\"Configured indexing target is not an external website\","
                            + "\"indexingTarget\":\""
                            + escapeJson(solrService.getIndexingTarget())
                            + "\""
                            + "}");

            return;
        }

        try {

            /*
             * Crawl the configured external website.
             */
            LOG.info(
                    "Starting external website crawl for target: {}",
                    solrService.getIndexingTarget());

            List<CrawledPage> crawledPages = websiteCrawlerService.crawl();

            LOG.info(
                    "External website crawl completed. Pages discovered: {}",
                    crawledPages.size());

            int indexedCount = 0;
            int failedCount = 0;

            /*
             * Send every crawled page to the existing Solr service.
             */
            for (CrawledPage page : crawledPages) {

                try {

                    solrService.indexExternalPage(page);

                    indexedCount++;

                    LOG.info(
                            "Successfully indexed external page: {}",
                            page.getUrl());

                } catch (SolrIndexException e) {

                    failedCount++;

                    LOG.error(
                            "Failed to index external page: {}",
                            page.getUrl(),
                            e);
                }
            }

            /*
             * Return a summary of the indexing operation.
             */
            response.setStatus(
                    HttpServletResponse.SC_OK);

            response.getWriter().write(
                    "{"
                            + "\"status\":\"success\","
                            + "\"indexingTarget\":\""
                            + escapeJson(solrService.getIndexingTarget())
                            + "\","
                            + "\"crawledPages\":"
                            + crawledPages.size()
                            + ","
                            + "\"indexedPages\":"
                            + indexedCount
                            + ","
                            + "\"failedPages\":"
                            + failedCount
                            + "}");

            LOG.info(
                    "External Solr indexing completed. "
                            + "Crawled: {}, Indexed: {}, Failed: {}",
                    crawledPages.size(),
                    indexedCount,
                    failedCount);

        } catch (WebsiteCrawlerException e) {

            LOG.error(
                    "External website crawling failed for target: {}",
                    solrService.getIndexingTarget(),
                    e);

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            response.getWriter().write(
                    "{"
                            + "\"status\":\"error\","
                            + "\"message\":\"External website crawling failed\","
                            + "\"details\":\""
                            + escapeJson(e.getMessage())
                            + "\""
                            + "}");

        } catch (Exception e) {

            LOG.error(
                    "Unexpected error during external Solr indexing",
                    e);

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            response.getWriter().write(
                    "{"
                            + "\"status\":\"error\","
                            + "\"message\":\"Unexpected error during external Solr indexing\","
                            + "\"details\":\""
                            + escapeJson(e.getMessage())
                            + "\""
                            + "}");
        }
    }

    /**
     * Escapes values before placing them inside a JSON response.
     */
    private String escapeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}