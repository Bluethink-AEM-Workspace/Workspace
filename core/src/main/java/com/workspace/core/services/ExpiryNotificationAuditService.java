/*

`ExpiryNotificationAuditService` is responsible for tracking whether an asset expiry notification has already been sent for a specific expiration date. It stores audit records under `/var/workspace/expiry-notifications`, using the asset UUID as the tracker node identifier and the expiration date as the stored marker. Before adding an asset to a new report, the service checks this tracker to prevent duplicate notifications; after successful email delivery, it updates the marker with the current expiration date. This also allows a new notification to be sent if the asset's expiration date changes, while handling repository, resolver, and persistence errors safely.


*/

package com.workspace.core.services;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ExpiryNotificationAuditService.class)
public class ExpiryNotificationAuditService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ExpiryNotificationAuditService.class);

    /**
     * Root location where expiry notification audit records are stored.
     *
     * This path must be created by Repoinit during deployment.
     */
    private static final String TRACKER_ROOT =
            "/var/workspace/expiry-notifications";

    private static final String EXPIRY_DATE_PROPERTY =
            "expiryDate";

    private static final String NODE_TYPE =
            "nt:unstructured";

    /**
     * Checks whether an expiry notification has already been
     * successfully sent for the supplied expiration date.
     *
     * The audit record is stored separately under /var/workspace
     * and is identified using the asset UUID.
     *
     * @param asset asset resource
     * @param expirationDate expiration date
     * @return true if notification was already sent for the
     *         supplied expiration date
     */
    public boolean wasNotificationSent(
            Resource asset,
            LocalDate expirationDate) {

        if (asset == null || expirationDate == null) {
            return false;
        }

        try {
            String assetUuid = getAssetUuid(asset);

            if (assetUuid == null || assetUuid.isEmpty()) {
                LOG.warn(
                        "Unable to determine UUID for asset: {}",
                        asset.getPath());
                return false;
            }

            ResourceResolver resolver =
                    asset.getResourceResolver();

            Resource trackerResource =
                    resolver.getResource(
                            TRACKER_ROOT + "/" + assetUuid);

            if (trackerResource == null) {
                return false;
            }

            Calendar sentDate =
                    trackerResource
                            .getValueMap()
                            .get(
                                    EXPIRY_DATE_PROPERTY,
                                    Calendar.class);

            if (sentDate == null) {
                return false;
            }

            LocalDate sentLocalDate =
                    toLocalDate(sentDate);

            return expirationDate.equals(sentLocalDate);

        } catch (Exception e) {
            LOG.error(
                    "Unable to check expiry notification audit for asset: {}",
                    asset.getPath(),
                    e);

            return false;
        }
    }

    /**
     * Marks an asset as notified for the supplied expiration date.
     *
     * This method should only be called after successful email delivery.
     *
     * The audit record is stored under:
     *
     * /var/workspace/expiry-notifications/<asset-uuid>
     *
     * Only the expiration date is stored on the tracker node.
     *
     * @param asset asset resource
     * @param expirationDate expiration date
     * @return true if the marker was successfully persisted
     */
    public boolean markNotificationSent(
            Resource asset,
            LocalDate expirationDate) {

        if (asset == null || expirationDate == null) {
            return false;
        }

        try {
            String assetUuid =
                    getAssetUuid(asset);

            if (assetUuid == null || assetUuid.isEmpty()) {
                LOG.warn(
                        "Unable to determine UUID for asset: {}",
                        asset.getPath());
                return false;
            }

            ResourceResolver resolver =
                    asset.getResourceResolver();

            /*
             * The tracker root must already exist.
             *
             * It is intentionally NOT created here.
             * Repoinit is responsible for creating:
             *
             * /var/workspace
             * /var/workspace/expiry-notifications
             */
            Resource trackerRoot =
                    resolver.getResource(TRACKER_ROOT);

            if (trackerRoot == null) {
                LOG.error(
                        "Expiry notification tracker root does not exist: {}. " +
                        "Ensure Repoinit creates this path.",
                        TRACKER_ROOT);
                return false;
            }

            String trackerPath =
                    TRACKER_ROOT + "/" + assetUuid;

            Resource trackerResource =
                    resolver.getResource(trackerPath);

            /*
             * Create a tracker node only when one does not already exist.
             */
            if (trackerResource == null) {

                trackerResource =
                        resolver.create(
                                trackerRoot,
                                assetUuid,
                                createNodeProperties());

                LOG.debug(
                        "Created expiry notification tracker node: {}",
                        trackerPath);
            }

            if (trackerResource == null) {
                LOG.error(
                        "Unable to create expiry notification tracker node: {}",
                        trackerPath);
                return false;
            }

            Node trackerNode =
                    trackerResource.adaptTo(Node.class);

            if (trackerNode == null) {
                LOG.error(
                        "Unable to adapt tracker resource to JCR Node: {}",
                        trackerPath);
                return false;
            }

            /*
             * Store only the expiration date.
             *
             * This means that if the asset's expiration date changes,
             * the marker changes as well and a new notification can
             * be sent for the new expiration date.
             */
            Calendar calendar =
                    Calendar.getInstance();

            calendar.set(
                    expirationDate.getYear(),
                    expirationDate.getMonthValue() - 1,
                    expirationDate.getDayOfMonth(),
                    0,
                    0,
                    0);

            calendar.set(
                    Calendar.MILLISECOND,
                    0);

            trackerNode.setProperty(
                    EXPIRY_DATE_PROPERTY,
                    calendar);

            resolver.commit();

            LOG.info(
                    "Expiry notification audit saved. " +
                    "Asset UUID: {}, Expiration: {}, Tracker: {}",
                    assetUuid,
                    expirationDate,
                    trackerPath);

            return true;

        } catch (RepositoryException e) {

            LOG.error(
                    "Unable to persist expiry notification audit for asset: {}",
                    asset.getPath(),
                    e);

            return false;

        } catch (Exception e) {

            LOG.error(
                    "Unexpected error while persisting expiry notification audit for asset: {}",
                    asset.getPath(),
                    e);

            return false;
        }
    }

    /**
     * Gets the UUID of the asset.
     *
     * @param asset asset resource
     * @return asset UUID or null
     * @throws RepositoryException if the JCR node cannot be accessed
     */
    private String getAssetUuid(Resource asset)
            throws RepositoryException {

        Node assetNode =
                asset.adaptTo(Node.class);

        if (assetNode == null) {
            return null;
        }

        if (!assetNode.hasProperty("jcr:uuid")) {
            return null;
        }

        return assetNode
                .getProperty("jcr:uuid")
                .getString();
    }

    /**
     * Creates the properties used when creating individual
     * audit tracker nodes.
     *
     * @return node creation properties
     */
    private Map<String, Object> createNodeProperties() {

        Map<String, Object> properties =
                new HashMap<String, Object>();

        properties.put(
                "jcr:primaryType",
                NODE_TYPE);

        return properties;
    }

    /**
     * Converts JCR Calendar to LocalDate.
     *
     * @param calendar JCR calendar
     * @return LocalDate
     */
    private LocalDate toLocalDate(
            Calendar calendar) {

        return LocalDate.of(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }
}

