package com.workspace.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;

class AssetExpiryQueryServiceTest {

    private static final String TEST_DAM_ROOT_PATH =
            "/content/dam/workspace";

    private static final Instant WINDOW_START =
            Instant.parse("2026-08-27T00:00:00Z");

    private static final Instant WINDOW_END =
            Instant.parse("2026-09-03T00:00:00Z");

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Session session;

    @Mock
    private QueryBuilder queryBuilder;

    @Mock
    private Query query;

    @Mock
    private SearchResult searchResult;

    @Mock
    private Hit hit1;

    @Mock
    private Hit hit2;

    @Mock
    private Hit hit3;

    private AssetExpiryQueryService service;

    @BeforeEach
    void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);

        service = new AssetExpiryQueryService();

        setField(
                "resourceResolverFactory",
                resourceResolverFactory);

        setField(
                "queryBuilder",
                queryBuilder);
    }

    // =========================================================
    // Null / invalid window
    // =========================================================

    @Test
    void shouldReturnEmptyListWhenWindowStartIsNull()
            throws Exception {

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        null,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(
                resourceResolverFactory,
                never())
                .getServiceResourceResolver(
                        anyMap());
    }

    @Test
    void shouldReturnEmptyListWhenWindowEndIsNull()
            throws Exception {

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        null);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(
                resourceResolverFactory,
                never())
                .getServiceResourceResolver(
                        anyMap());
    }

    @Test
    void shouldReturnEmptyListWhenBothWindowDatesAreNull()
            throws Exception {

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        null,
                        null);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(
                resourceResolverFactory,
                never())
                .getServiceResourceResolver(
                        anyMap());
    }

    @Test
    void shouldReturnEmptyListWhenWindowEndIsBeforeWindowStart()
            throws Exception {

        Instant windowStart =
                Instant.parse("2026-09-03T00:00:00Z");

        Instant windowEnd =
                Instant.parse("2026-08-27T00:00:00Z");

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        windowStart,
                        windowEnd);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(
                resourceResolverFactory,
                never())
                .getServiceResourceResolver(
                        anyMap());
    }

    // =========================================================
    // Successful QueryBuilder execution
    // =========================================================

    @Test
    void shouldReturnAssetPathsFromQueryBuilder()
            throws Exception {

        mockSuccessfulQuery();

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);

        assertEquals(
                2,
                results.size());

        assertTrue(
                results.contains(
                        "/content/dam/workspace/asset1.jpg"));

        assertTrue(
                results.contains(
                        "/content/dam/workspace/asset2.jpg"));

        verify(resourceResolverFactory)
                .getServiceResourceResolver(
                        anyMap());

        verify(resourceResolver)
                .adaptTo(Session.class);

        verify(queryBuilder)
                .createQuery(
                        any(PredicateGroup.class),
                        any(Session.class));

        verify(query)
                .getResult();
    }

    // =========================================================
    // No query results
    // =========================================================

    @Test
    void shouldReturnEmptyListWhenNoHitsAreReturned()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        when(searchResult.getHits())
                .thenReturn(
                        Collections.<Hit>emptyList());

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(query)
                .getResult();
    }

    // =========================================================
    // Null Hit
    // =========================================================

    @Test
    void shouldSkipNullHit()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        List<Hit> hits =
                Arrays.asList(
                        null,
                        hit1);

        when(searchResult.getHits())
                .thenReturn(hits);

        when(hit1.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset1.jpg");

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);

        assertEquals(
                1,
                results.size());

        assertEquals(
                "/content/dam/workspace/asset1.jpg",
                results.get(0));
    }

    // =========================================================
    // Null / empty asset path
    // =========================================================

    @Test
    void shouldSkipHitWhenAssetPathIsNull()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        when(searchResult.getHits())
                .thenReturn(
                        Collections.singletonList(hit1));

        when(hit1.getPath())
                .thenReturn(null);

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldSkipHitWhenAssetPathIsEmpty()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        when(searchResult.getHits())
                .thenReturn(
                        Collections.singletonList(hit1));

        when(hit1.getPath())
                .thenReturn("");

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // =========================================================
    // One bad Hit must not stop other Hits
    // =========================================================

    @Test
    void shouldContinueWhenOneHitThrowsException()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        when(searchResult.getHits())
                .thenReturn(
                        Arrays.asList(
                                hit1,
                                hit2));

        when(hit1.getPath())
                .thenThrow(
                        new RuntimeException(
                                "Unable to read hit"));

        when(hit2.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset2.jpg");

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);

        assertEquals(
                1,
                results.size());

        assertTrue(
                results.contains(
                        "/content/dam/workspace/asset2.jpg"));
    }

    // =========================================================
    // ResourceResolver cannot adapt to Session
    // =========================================================

    @Test
    void shouldReturnEmptyListWhenSessionIsNull()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(
                        anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(Session.class))
                .thenReturn(null);

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(resourceResolver)
                .adaptTo(Session.class);

        verify(
                queryBuilder,
                never())
                .createQuery(
                        any(PredicateGroup.class),
                        any(Session.class));
    }

    // =========================================================
    // ResourceResolverFactory failure
    // =========================================================

    @Test
    void shouldReturnEmptyListWhenResolverCreationFails()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(
                        anyMap()))
                .thenThrow(
                        new RuntimeException(
                                "Unable to create resolver"));

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(resourceResolverFactory)
                .getServiceResourceResolver(
                        anyMap());

        verify(
                queryBuilder,
                never())
                .createQuery(
                        any(PredicateGroup.class),
                        any(Session.class));
    }

    // =========================================================
    // QueryBuilder failure
    // =========================================================

    @Test
    void shouldReturnEmptyListWhenQueryBuilderFails()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenThrow(
                        new RuntimeException(
                                "QueryBuilder failure"));

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(queryBuilder)
                .createQuery(
                        any(PredicateGroup.class),
                        any(Session.class));
    }

    // =========================================================
    // Query execution failure
    // =========================================================

    @Test
    void shouldReturnEmptyListWhenQueryExecutionFails()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenThrow(
                        new RuntimeException(
                                "Query execution failure"));

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);
        assertTrue(results.isEmpty());

        verify(query)
                .getResult();
    }

    // =========================================================
    // Same start and end
    // =========================================================

    @Test
    void shouldAcceptSameStartAndEnd()
            throws Exception {

        Instant window =
                Instant.parse(
                        "2026-08-27T00:00:00Z");

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        when(searchResult.getHits())
                .thenReturn(
                        Collections.singletonList(hit1));

        when(hit1.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset1.jpg");

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        window,
                        window);

        assertNotNull(results);

        assertEquals(
                1,
                results.size());

        assertTrue(
                results.contains(
                        "/content/dam/workspace/asset1.jpg"));
    }

    // =========================================================
    // Multiple results
    // =========================================================

    @Test
    void shouldReturnAllValidAssetPaths()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        when(searchResult.getHits())
                .thenReturn(
                        Arrays.asList(
                                hit1,
                                hit2,
                                hit3));

        when(hit1.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset1.jpg");

        when(hit2.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset2.jpg");

        when(hit3.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset3.jpg");

        List<String> results =
                service.findExpiringAssets(
                        TEST_DAM_ROOT_PATH,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);

        assertEquals(
                3,
                results.size());

        assertTrue(
                results.contains(
                        "/content/dam/workspace/asset1.jpg"));

        assertTrue(
                results.contains(
                        "/content/dam/workspace/asset2.jpg"));

        assertTrue(
                results.contains(
                        "/content/dam/workspace/asset3.jpg"));
    }

    // =========================================================
    // Null DAM root should use default
    // =========================================================

    @Test
    void shouldUseDefaultDamRootWhenDamRootIsNull()
            throws Exception {

        mockSuccessfulQuery();

        List<String> results =
                service.findExpiringAssets(
                        null,
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);

        assertEquals(
                2,
                results.size());

        verify(queryBuilder)
                .createQuery(
                        any(PredicateGroup.class),
                        any(Session.class));
    }

    // =========================================================
    // Empty DAM root should use default
    // =========================================================

    @Test
    void shouldUseDefaultDamRootWhenDamRootIsEmpty()
            throws Exception {

        mockSuccessfulQuery();

        List<String> results =
                service.findExpiringAssets(
                        "   ",
                        WINDOW_START,
                        WINDOW_END);

        assertNotNull(results);

        assertEquals(
                2,
                results.size());
    }

    // =========================================================
    // Helpers
    // =========================================================

    private void mockResolverAndSession()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(
                        anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(Session.class))
                .thenReturn(session);
    }

    private void mockSuccessfulQuery()
            throws Exception {

        mockResolverAndSession();

        when(queryBuilder.createQuery(
                any(PredicateGroup.class),
                any(Session.class)))
                .thenReturn(query);

        when(query.getResult())
                .thenReturn(searchResult);

        when(searchResult.getHits())
                .thenReturn(
                        Arrays.asList(
                                hit1,
                                hit2));

        when(hit1.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset1.jpg");

        when(hit2.getPath())
                .thenReturn(
                        "/content/dam/workspace/asset2.jpg");
    }

    private void setField(
            String fieldName,
            Object value)
            throws Exception {

        Field field =
                AssetExpiryQueryService.class
                        .getDeclaredField(fieldName);

        field.setAccessible(true);

        field.set(
                service,
                value);
    }
}