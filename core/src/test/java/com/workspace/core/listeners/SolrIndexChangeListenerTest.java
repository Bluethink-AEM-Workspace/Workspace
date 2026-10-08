package com.workspace.core.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.workspace.core.services.SolrService;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SolrIndexChangeListenerTest {

    private static final String INDEXING_ROOT = "/content/workspace/language-masters/en";

    private static final String PAGE_PATH = INDEXING_ROOT + "/products";

    private static final String CHILD_PATH = PAGE_PATH + "/jcr:content/root/text";

    @Mock
    private SolrService solrService;

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Resource changedResource;

    @Mock
    private Resource pageResource;

    @Mock
    private ValueMap changedValueMap;

    @Mock
    private ValueMap pageValueMap;

    @InjectMocks
    private SolrIndexChangeListener listener;

    @Test
    void shouldIndexAddedResourceWhenItBelongsToConfiguredAemPath()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.ADDED);

        prepareChangedResourceWithContainingPage();

        listener.onChange(List.of(change));

        verify(solrService)
                .indexPage(pageResource);

        verify(solrService, never())
                .deleteDocument(any());

        verifyServiceUser();
    }

    @Test
    void shouldIndexChangedResourceWhenItBelongsToConfiguredAemPath()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.CHANGED);

        prepareChangedResourceWithContainingPage();

        listener.onChange(List.of(change));

        verify(solrService)
                .indexPage(pageResource);
    }

    @Test
    void shouldDeletePageWhenPageItselfIsRemoved()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        ResourceChange change = mockChange(
                PAGE_PATH,
                ChangeType.REMOVED);

        prepareServiceResolver();

        listener.onChange(List.of(change));

        verify(solrService)
                .deleteDocument(PAGE_PATH);

        verify(solrService, never())
                .indexPage(any());
    }

    @Test
    void shouldReindexContainingPageWhenChildResourceIsRemoved()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.REMOVED);

        prepareServiceResolver();

        /*
         * The removed child itself no longer exists.
         *
         * The production implementation walks upward and resolves
         * the containing page. The current implementation resolves
         * PAGE_PATH twice while processing the removed resource.
         */
        when(resourceResolver.getResource(anyString()))
                .thenAnswer(invocation -> {

                    String requestedPath = invocation.getArgument(0);

                    if (PAGE_PATH.equals(requestedPath)) {
                        return pageResource;
                    }

                    return null;
                });

        when(pageResource.getValueMap())
                .thenReturn(pageValueMap);

        when(pageValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("cq:Page");

        listener.onChange(List.of(change));

        verify(resourceResolver, times(2))
                .getResource(PAGE_PATH);

        verify(solrService)
                .indexPage(pageResource);

        verify(solrService, never())
                .deleteDocument(any());
    }

    @Test
    void shouldIgnoreChangesOutsideConfiguredAemPath()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        String outsidePath = "/content/another-site/page";

        ResourceChange change = mockChange(
                outsidePath,
                ChangeType.CHANGED);

        prepareServiceResolver();

        listener.onChange(List.of(change));

        verify(solrService, never())
                .indexPage(any());

        verify(solrService, never())
                .deleteDocument(any());

        verify(resourceResolver, never())
                .getResource(outsidePath);
    }

    @Test
    void shouldIgnoreChangesWhenExternalIndexingTargetIsConfigured()
            throws Exception {

        when(solrService.isAemIndexingTarget())
                .thenReturn(false);

        listener.activate();

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.CHANGED);

        listener.onChange(List.of(change));

        verify(resourceResolverFactory, never())
                .getServiceResourceResolver(anyMap());

        verify(solrService, never())
                .indexPage(any());

        verify(solrService, never())
                .deleteDocument(any());
    }

    @Test
    void shouldIgnoreChangesWhenIndexingTargetIsNotConfigured()
            throws Exception {

        when(solrService.isAemIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn("");

        listener.activate();

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.CHANGED);

        prepareServiceResolver();

        listener.onChange(List.of(change));

        verify(solrService, never())
                .indexPage(any());

        verify(solrService, never())
                .deleteDocument(any());

        verify(resourceResolver, never())
                .getResource(CHILD_PATH);
    }

    @Test
    void shouldDoNothingWhenChangedResourceCannotBeResolved()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.ADDED);

        prepareServiceResolver();

        when(resourceResolver.getResource(CHILD_PATH))
                .thenReturn(null);

        listener.onChange(List.of(change));

        verify(solrService, never())
                .indexPage(any());

        verify(solrService, never())
                .deleteDocument(any());
    }

    @Test
    void shouldDoNothingWhenContainingPageCannotBeFound()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.ADDED);

        prepareServiceResolver();

        when(resourceResolver.getResource(CHILD_PATH))
                .thenReturn(changedResource);

        when(changedResource.getValueMap())
                .thenReturn(changedValueMap);

        when(changedValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("nt:unstructured");

        when(changedResource.getParent())
                .thenReturn(null);

        listener.onChange(List.of(change));

        verify(solrService, never())
                .indexPage(any());

        verify(solrService, never())
                .deleteDocument(any());
    }

    @Test
    void shouldHandleLoginExceptionWithoutCallingSolr()
            throws Exception {

        activateForAem();

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.CHANGED);

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenThrow(
                        new LoginException(
                                "Service user login failed"));

        listener.onChange(List.of(change));

        verify(solrService, never())
                .indexPage(any());

        verify(solrService, never())
                .deleteDocument(any());
    }

    @Test
    void shouldContinueProcessingWhenOneChangeFails()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        String secondResourcePath = INDEXING_ROOT + "/services/jcr:content/root/text";

        ResourceChange firstChange = mockChange(
                CHILD_PATH,
                ChangeType.CHANGED);

        ResourceChange secondChange = mockChange(
                secondResourcePath,
                ChangeType.CHANGED);

        Resource secondResource = org.mockito.Mockito.mock(Resource.class);

        ValueMap secondValueMap = org.mockito.Mockito.mock(ValueMap.class);

        Resource secondPage = org.mockito.Mockito.mock(Resource.class);

        ValueMap secondPageValueMap = org.mockito.Mockito.mock(ValueMap.class);

        prepareServiceResolver();

        /*
         * First change.
         */
        when(resourceResolver.getResource(CHILD_PATH))
                .thenReturn(changedResource);

        when(changedResource.getValueMap())
                .thenReturn(changedValueMap);

        when(changedValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("nt:unstructured");

        when(changedResource.getParent())
                .thenReturn(pageResource);

        when(pageResource.getValueMap())
                .thenReturn(pageValueMap);

        when(pageValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("cq:Page");

        /*
         * Second change.
         */
        when(resourceResolver.getResource(secondResourcePath))
                .thenReturn(secondResource);

        when(secondResource.getValueMap())
                .thenReturn(secondValueMap);

        when(secondValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("nt:unstructured");

        when(secondResource.getParent())
                .thenReturn(secondPage);

        when(secondPage.getValueMap())
                .thenReturn(secondPageValueMap);

        when(secondPageValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("cq:Page");

        /*
         * First indexing operation fails.
         */
        doThrow(
                new SolrIndexException(
                        "Solr indexing failed"))
                .when(solrService)
                .indexPage(pageResource);

        listener.onChange(
                Arrays.asList(
                        firstChange,
                        secondChange));

        /*
         * The first operation failed, but the second must still
         * be processed.
         */
        verify(solrService)
                .indexPage(pageResource);

        verify(solrService)
                .indexPage(secondPage);
    }

    @Test
    void shouldUseSolrIndexServiceSubservice()
            throws Exception {

        activateForAem();

        when(solrService.getIndexingTarget())
                .thenReturn(INDEXING_ROOT);

        ResourceChange change = mockChange(
                CHILD_PATH,
                ChangeType.CHANGED);

        prepareChangedResourceWithContainingPage();

        listener.onChange(List.of(change));

        verifyServiceUser();
    }

    private void activateForAem() {

        when(solrService.isAemIndexingTarget())
                .thenReturn(true);

        listener.activate();
    }

    private void prepareServiceResolver()
            throws LoginException {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);
    }

    private void prepareChangedResourceWithContainingPage()
            throws LoginException {

        prepareServiceResolver();

        when(resourceResolver.getResource(CHILD_PATH))
                .thenReturn(changedResource);

        when(changedResource.getValueMap())
                .thenReturn(changedValueMap);

        when(changedValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("nt:unstructured");

        when(changedResource.getParent())
                .thenReturn(pageResource);

        when(pageResource.getValueMap())
                .thenReturn(pageValueMap);

        when(pageValueMap.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("cq:Page");
    }

    private ResourceChange mockChange(
            String path,
            ChangeType changeType) {

        ResourceChange change = org.mockito.Mockito.mock(
                ResourceChange.class);

        when(change.getPath())
                .thenReturn(path);

        when(change.getType())
                .thenReturn(changeType);

        return change;
    }

    private void verifyServiceUser()
            throws LoginException {

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);

        verify(resourceResolverFactory)
                .getServiceResourceResolver(
                        captor.capture());

        Map<String, Object> params = captor.getValue();

        assertEquals(
                "solr-index-service",
                params.get(
                        ResourceResolverFactory.SUBSERVICE));
    }
}