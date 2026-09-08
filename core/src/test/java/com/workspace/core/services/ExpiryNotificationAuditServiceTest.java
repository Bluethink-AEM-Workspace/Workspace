package com.workspace.core.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.Property;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ExpiryNotificationAuditServiceTest {

    private ExpiryNotificationAuditService service;

    @Mock
    private Resource asset;

    @Mock
    private Resource trackerRoot;

    @Mock
    private Resource trackerResource;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private Node assetNode;

    @Mock
    private Node trackerNode;

    @Mock
    private Property uuidProperty;

    @Mock
    private ValueMap valueMap;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ExpiryNotificationAuditService();
    }

    // ---------------------------------------------------------
    // No audit marker
    // ---------------------------------------------------------

    @Test
    void shouldReturnFalseWhenNotificationWasNeverSent()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(null);

        assertFalse(
                service.wasNotificationSent(
                        asset,
                        expirationDate));
    }

    // ---------------------------------------------------------
    // Marker matches expiration date
    // ---------------------------------------------------------

    @Test
    void shouldReturnTrueWhenNotificationWasAlreadySent()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        Calendar sentCalendar =
                createCalendar(expirationDate);

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(trackerResource);

        when(trackerResource.getValueMap())
                .thenReturn(valueMap);

        when(valueMap.get(
                "expiryDate",
                Calendar.class))
                .thenReturn(sentCalendar);

        assertTrue(
                service.wasNotificationSent(
                        asset,
                        expirationDate));
    }

    // ---------------------------------------------------------
    // Expiration date changed
    // ---------------------------------------------------------

    @Test
    void shouldReturnFalseWhenExpirationDateChanged()
            throws Exception {

        LocalDate oldExpirationDate =
                LocalDate.of(2026, 9, 1);

        LocalDate newExpirationDate =
                LocalDate.of(2026, 9, 5);

        String assetUuid = "abc-123";

        Calendar sentCalendar =
                createCalendar(oldExpirationDate);

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(trackerResource);

        when(trackerResource.getValueMap())
                .thenReturn(valueMap);

        when(valueMap.get(
                "expiryDate",
                Calendar.class))
                .thenReturn(sentCalendar);

        assertFalse(
                service.wasNotificationSent(
                        asset,
                        newExpirationDate));
    }

    // ---------------------------------------------------------
    // Same expiration date should not duplicate
    // ---------------------------------------------------------

    @Test
    void shouldPreventDuplicateNotificationForSameDate()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        Calendar sentCalendar =
                createCalendar(expirationDate);

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(trackerResource);

        when(trackerResource.getValueMap())
                .thenReturn(valueMap);

        when(valueMap.get(
                "expiryDate",
                Calendar.class))
                .thenReturn(sentCalendar);

        assertTrue(
                service.wasNotificationSent(
                        asset,
                        expirationDate));

        assertTrue(
                service.wasNotificationSent(
                        asset,
                        expirationDate));

        verify(
                resourceResolver,
                times(2))
                .getResource(
                        "/var/workspace/expiry-notifications/"
                                + assetUuid);
    }

    // ---------------------------------------------------------
    // Null asset
    // ---------------------------------------------------------

    @Test
    void shouldReturnFalseWhenAssetIsNull() {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        assertFalse(
                service.wasNotificationSent(
                        null,
                        expirationDate));
    }

    // ---------------------------------------------------------
    // Null expiration date
    // ---------------------------------------------------------

    @Test
    void shouldReturnFalseWhenExpirationDateIsNull() {

        assertFalse(
                service.wasNotificationSent(
                        asset,
                        null));
    }

    // ---------------------------------------------------------
    // Asset UUID unavailable
    // ---------------------------------------------------------

    @Test
    void shouldReturnFalseWhenAssetUuidIsUnavailable()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(false);

        assertFalse(
                service.wasNotificationSent(
                        asset,
                        expirationDate));

        verify(
                asset,
                never())
                .getResourceResolver();
    }

    // ---------------------------------------------------------
    // Asset Node unavailable
    // ---------------------------------------------------------

    @Test
    void shouldReturnFalseWhenAssetNodeIsUnavailable() {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        when(asset.adaptTo(Node.class))
                .thenReturn(null);

        assertFalse(
                service.wasNotificationSent(
                        asset,
                        expirationDate));
    }

    // ---------------------------------------------------------
    // Tracker resource exists but expiryDate is missing
    // ---------------------------------------------------------

    @Test
    void shouldReturnFalseWhenTrackerExpiryDateIsMissing()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(trackerResource);

        when(trackerResource.getValueMap())
                .thenReturn(valueMap);

        when(valueMap.get(
                "expiryDate",
                Calendar.class))
                .thenReturn(null);

        assertFalse(
                service.wasNotificationSent(
                        asset,
                        expirationDate));
    }

    // ---------------------------------------------------------
    // Marker persistence - new tracker node
    // ---------------------------------------------------------

    @Test
    void shouldPersistNotificationMarker()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        /*
         * Tracker root is expected to already exist.
         * Repoinit creates it.
         */
        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications"))
                .thenReturn(trackerRoot);

        /*
         * Individual UUID tracker node does not exist yet.
         */
        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(null);

        when(resourceResolver.create(
                eq(trackerRoot),
                eq(assetUuid),
                anyMap()))
                .thenReturn(trackerResource);

        when(trackerResource.adaptTo(Node.class))
                .thenReturn(trackerNode);

        boolean result =
                service.markNotificationSent(
                        asset,
                        expirationDate);

        assertTrue(result);

        ArgumentCaptor<Calendar> calendarCaptor =
                ArgumentCaptor.forClass(Calendar.class);

        verify(trackerNode).setProperty(
                eq("expiryDate"),
                calendarCaptor.capture());

        Calendar savedCalendar =
                calendarCaptor.getValue();

        assertTrue(
                expirationDate.equals(
                        toLocalDate(savedCalendar)));

        verify(resourceResolver)
                .commit();
    }

    // ---------------------------------------------------------
    // Existing tracker node should be updated
    // ---------------------------------------------------------

    @Test
    void shouldUpdateExistingTrackerNode()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 5);

        String assetUuid = "abc-123";

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        /*
         * Tracker root already exists.
         */
        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications"))
                .thenReturn(trackerRoot);

        /*
         * Tracker node already exists.
         */
        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(trackerResource);

        when(trackerResource.adaptTo(Node.class))
                .thenReturn(trackerNode);

        boolean result =
                service.markNotificationSent(
                        asset,
                        expirationDate);

        assertTrue(result);

        ArgumentCaptor<Calendar> calendarCaptor =
                ArgumentCaptor.forClass(Calendar.class);

        verify(trackerNode).setProperty(
                eq("expiryDate"),
                calendarCaptor.capture());

        assertTrue(
                expirationDate.equals(
                        toLocalDate(
                                calendarCaptor.getValue())));

        verify(resourceResolver)
                .commit();

        /*
         * Existing tracker node should not be recreated.
         */
        verify(
                resourceResolver,
                never())
                .create(
                        eq(trackerRoot),
                        eq(assetUuid),
                        anyMap());
    }

    // ---------------------------------------------------------
    // Tracker root unavailable
    // ---------------------------------------------------------

    @Test
    void shouldNotPersistWhenTrackerRootIsUnavailable()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        /*
         * Repoinit-created tracker root is missing.
         */
        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications"))
                .thenReturn(null);

        boolean result =
                service.markNotificationSent(
                        asset,
                        expirationDate);

        assertFalse(result);

        /*
         * Java must NOT attempt to create the tracker root.
         */
        verify(
                resourceResolver,
                never())
                .create(
                        eq(trackerRoot),
                        eq("expiry-notifications"),
                        anyMap());

        verify(
                resourceResolver,
                never())
                .commit();
    }

    // ---------------------------------------------------------
    // Tracker node unavailable after creation
    // ---------------------------------------------------------

    @Test
    void shouldNotPersistWhenTrackerNodeIsUnavailable()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        /*
         * Tracker root exists.
         */
        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications"))
                .thenReturn(trackerRoot);

        /*
         * Existing tracker node exists.
         */
        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(trackerResource);

        /*
         * But it cannot be adapted to a JCR Node.
         */
        when(trackerResource.adaptTo(Node.class))
                .thenReturn(null);

        boolean result =
                service.markNotificationSent(
                        asset,
                        expirationDate);

        assertFalse(result);

        verify(
                resourceResolver,
                never())
                .commit();
    }

    // ---------------------------------------------------------
    // Tracker node creation fails
    // ---------------------------------------------------------

    @Test
    void shouldNotPersistWhenTrackerNodeCreationFails()
            throws Exception {

        LocalDate expirationDate =
                LocalDate.of(2026, 9, 1);

        String assetUuid = "abc-123";

        when(asset.adaptTo(Node.class))
                .thenReturn(assetNode);

        when(assetNode.hasProperty("jcr:uuid"))
                .thenReturn(true);

        when(assetNode.getProperty("jcr:uuid"))
                .thenReturn(uuidProperty);

        when(uuidProperty.getString())
                .thenReturn(assetUuid);

        when(asset.getResourceResolver())
                .thenReturn(resourceResolver);

        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications"))
                .thenReturn(trackerRoot);

        when(resourceResolver.getResource(
                "/var/workspace/expiry-notifications/"
                        + assetUuid))
                .thenReturn(null);

        when(resourceResolver.create(
                eq(trackerRoot),
                eq(assetUuid),
                anyMap()))
                .thenReturn(null);

        boolean result =
                service.markNotificationSent(
                        asset,
                        expirationDate);

        assertFalse(result);

        verify(
                resourceResolver,
                never())
                .commit();
    }

    // ---------------------------------------------------------
    // Helper - create Calendar
    // ---------------------------------------------------------

    private Calendar createCalendar(
            LocalDate date) {

        Calendar calendar =
                Calendar.getInstance();

        calendar.set(
                date.getYear(),
                date.getMonthValue() - 1,
                date.getDayOfMonth(),
                0,
                0,
                0);

        calendar.set(
                Calendar.MILLISECOND,
                0);

        return calendar;
    }

    // ---------------------------------------------------------
    // Helper - convert Calendar to LocalDate
    // ---------------------------------------------------------

    private LocalDate toLocalDate(
            Calendar calendar) {

        return LocalDate.of(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }
}

