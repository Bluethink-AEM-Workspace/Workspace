package com.workspace.core.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.workspace.core.config.SolrIndexConfiguration;

@ExtendWith(MockitoExtension.class)
class SolrConfigurationServiceImplTest {

    private static final String AEM_BASE_URL = "http://localhost:4502";

    private static final String SOLR_BASE_URL = "http://localhost:8983/solr/workspace";

    private static final String AEM_TARGET = "/content/workspace/language-masters/en";

    private static final String EXTERNAL_TARGET = "https://www.bluethinkinc.com";

    @Mock
    private SolrIndexConfiguration configuration;

    @Test
    void shouldLoadConfigurationOnActivation() {

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                AEM_TARGET,
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                AEM_BASE_URL,
                service.getAemBaseUrl());

        assertEquals(
                SOLR_BASE_URL,
                service.getSolrBaseUrl());

        assertEquals(
                AEM_TARGET,
                service.getIndexingTarget());

        assertEquals(
                50,
                service.getExternalCrawlMaxPages());

        assertEquals(
                10000,
                service.getExternalCrawlConnectionTimeout());
    }

    @Test
    void shouldRemoveTrailingSlashesFromBaseUrls() {

        whenConfigurationIs(
                AEM_BASE_URL + "///",
                SOLR_BASE_URL + "///",
                AEM_TARGET,
                25,
                5000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                AEM_BASE_URL,
                service.getAemBaseUrl());

        assertEquals(
                SOLR_BASE_URL,
                service.getSolrBaseUrl());
    }

    @Test
    void shouldNormalizeAemIndexingTarget() {

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                "  " + AEM_TARGET + "///  ",
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                AEM_TARGET,
                service.getIndexingTarget());

        assertTrue(
                service.isAemIndexingTarget());

        assertFalse(
                service.isExternalIndexingTarget());
    }

    @Test
    void shouldAcceptContentRootAsAemTarget() {

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                "/content/",
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                "/content",
                service.getIndexingTarget());

        assertTrue(
                service.isAemIndexingTarget());

        assertFalse(
                service.isExternalIndexingTarget());
    }

    @Test
    void shouldAcceptExternalHttpsTarget() {

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                EXTERNAL_TARGET,
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                EXTERNAL_TARGET,
                service.getIndexingTarget());

        assertTrue(
                service.isExternalIndexingTarget());

        assertFalse(
                service.isAemIndexingTarget());
    }

    @Test
    void shouldAcceptExternalHttpTarget() {

        String externalTarget = "http://www.bluethinkinc.com";

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                externalTarget,
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                externalTarget,
                service.getIndexingTarget());

        assertTrue(
                service.isExternalIndexingTarget());
    }

    @Test
    void shouldAcceptUppercaseHttpScheme() {

        String externalTarget = "HTTPS://www.bluethinkinc.com";

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                externalTarget,
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertTrue(
                service.isExternalIndexingTarget());
    }

    @Test
    void shouldTrimExternalIndexingTarget() {

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                "  " + EXTERNAL_TARGET + "  ",
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                EXTERNAL_TARGET,
                service.getIndexingTarget());

        assertTrue(
                service.isExternalIndexingTarget());
    }

    @Test
    void shouldRejectInvalidScheme() {

        assertInvalidTarget(
                "ftp://www.bluethinkinc.com");
    }

    @Test
    void shouldRejectExternalTargetWithoutHost() {

        assertInvalidTarget(
                "https:///some/path");
    }

    @Test
    void shouldRejectMalformedExternalTarget() {

        assertInvalidTarget(
                "https://[invalid-url");
    }

    @Test
    void shouldRejectBlankIndexingTarget() {

        assertInvalidTarget(
                "   ");
    }

    @Test
    void shouldRejectNullIndexingTarget() {

        assertInvalidTarget(null);
    }

    @Test
    void shouldRejectUnsupportedAemLikePath() {

        assertInvalidTarget(
                "/contentfoo");
    }

    @Test
    void shouldUpdateConfigurationWhenModified()
            throws Exception {

        whenConfigurationIs(
                AEM_BASE_URL,
                SOLR_BASE_URL,
                AEM_TARGET,
                50,
                10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        String updatedAemUrl = "http://localhost:4502";

        String updatedSolrUrl = "http://localhost:8983/solr/workspace";

        String updatedTarget = "https://en.wikipedia.org";

        when(configuration.aemBaseUrl())
                .thenReturn(updatedAemUrl);

        when(configuration.solrBaseUrl())
                .thenReturn(updatedSolrUrl);

        when(configuration.indexingTarget())
                .thenReturn(updatedTarget);

        when(configuration.externalCrawlMaxPages())
                .thenReturn(100);

        when(configuration.externalCrawlConnectionTimeout())
                .thenReturn(20000);

        service.modified(configuration);

        assertEquals(
                updatedAemUrl,
                service.getAemBaseUrl());

        assertEquals(
                updatedSolrUrl,
                service.getSolrBaseUrl());

        assertEquals(
                updatedTarget,
                service.getIndexingTarget());

        assertEquals(
                100,
                service.getExternalCrawlMaxPages());

        assertEquals(
                20000,
                service.getExternalCrawlConnectionTimeout());

        assertTrue(
                service.isExternalIndexingTarget());

        assertFalse(
                service.isAemIndexingTarget());
    }

    @Test
    void shouldConvertNullBaseUrlsToEmptyStrings() {

        when(configuration.aemBaseUrl())
                .thenReturn(null);

        when(configuration.solrBaseUrl())
                .thenReturn(null);

        when(configuration.indexingTarget())
                .thenReturn(AEM_TARGET);

        when(configuration.externalCrawlMaxPages())
                .thenReturn(50);

        when(configuration.externalCrawlConnectionTimeout())
                .thenReturn(10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        service.activate(configuration);

        assertEquals(
                "",
                service.getAemBaseUrl());

        assertEquals(
                "",
                service.getSolrBaseUrl());
    }

    private void whenConfigurationIs(
            String aemBaseUrl,
            String solrBaseUrl,
            String indexingTarget,
            int maxPages,
            int connectionTimeout) {

        when(configuration.aemBaseUrl())
                .thenReturn(aemBaseUrl);

        when(configuration.solrBaseUrl())
                .thenReturn(solrBaseUrl);

        when(configuration.indexingTarget())
                .thenReturn(indexingTarget);

        when(configuration.externalCrawlMaxPages())
                .thenReturn(maxPages);

        when(configuration.externalCrawlConnectionTimeout())
                .thenReturn(connectionTimeout);
    }

    private void assertInvalidTarget(
            String indexingTarget) {

        when(configuration.aemBaseUrl())
                .thenReturn(AEM_BASE_URL);

        when(configuration.solrBaseUrl())
                .thenReturn(SOLR_BASE_URL);

        when(configuration.indexingTarget())
                .thenReturn(indexingTarget);

        when(configuration.externalCrawlMaxPages())
                .thenReturn(50);

        when(configuration.externalCrawlConnectionTimeout())
                .thenReturn(10000);

        SolrConfigurationServiceImpl service = new SolrConfigurationServiceImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.activate(configuration));
    }
}