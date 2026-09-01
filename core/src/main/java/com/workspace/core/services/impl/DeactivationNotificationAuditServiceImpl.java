package com.workspace.core.services.impl;

import com.workspace.core.services.DeactivationNotificationAuditService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = DeactivationNotificationAuditService.class
)
public class DeactivationNotificationAuditServiceImpl
        implements DeactivationNotificationAuditService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DeactivationNotificationAuditServiceImpl.class
            );

    private static final String SUB_SERVICE =
            "damNotificationService";

    private static final String AUDIT_ROOT =
            "/var/workspace/dam-deactivation-audit";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Override
    public boolean isAlreadySent(String eventId) {

        if (eventId == null || eventId.trim().isEmpty()) {
            return false;
        }

        try (ResourceResolver resolver = getResolver()) {

            String auditPath =
                    AUDIT_ROOT + "/" + safeName(eventId);

            Resource resource =
                    resolver.getResource(auditPath);

            if (resource == null) {
                return false;
            }

            String status =
                    resource.getValueMap().get(
                            "status",
                            String.class
                    );

            boolean sent =
                    "SENT".equalsIgnoreCase(status);

            LOG.info(
                    "Audit status checked. eventId=[{}], status=[{}]",
                    eventId,
                    status
            );

            return sent;

        } catch (Exception e) {

            LOG.error(
                    "Unable to check audit status. eventId=[{}]",
                    eventId,
                    e
            );

            return false;
        }
    }

    @Override
    public void markSent(
            String eventId,
            String assetPath,
            long deactivationTime) {

        saveAudit(
                eventId,
                assetPath,
                "SENT",
                "Notification sent successfully",
                deactivationTime
        );
    }

    @Override
    public void markFailed(
            String eventId,
            String assetPath,
            String reason) {

        saveAudit(
                eventId,
                assetPath,
                "FAILED",
                reason,
                System.currentTimeMillis()
        );
    }

    private void saveAudit(
            String eventId,
            String assetPath,
            String status,
            String reason,
            long time) {

        if (eventId == null || eventId.trim().isEmpty()) {

            LOG.warn(
                    "Cannot save audit because eventId is empty."
            );

            return;
        }

        try (ResourceResolver resolver = getResolver()) {

            Resource auditRoot =
                    resolver.getResource(AUDIT_ROOT);

            if (auditRoot == null) {

                LOG.error(
                        "Audit root does not exist or is not readable. "
                                + "path=[{}]",
                        AUDIT_ROOT
                );

                return;
            }

            String auditPath =
                    AUDIT_ROOT + "/" + safeName(eventId);

            Resource auditResource =
                    resolver.getResource(auditPath);

            if (auditResource == null) {

                auditResource =
                        resolver.create(
                                auditRoot,
                                safeName(eventId),
                                Collections.singletonMap(
                                        "jcr:primaryType",
                                        "nt:unstructured"
                                )
                        );
            }

            ModifiableValueMap map =
                    auditResource.adaptTo(
                            ModifiableValueMap.class
                    );

            if (map == null) {
                throw new IllegalStateException(
                        "Unable to obtain ModifiableValueMap"
                );
            }

            Map<String, Object> properties =
                    new HashMap<>();

            properties.put("eventId", eventId);
            properties.put("assetPath", assetPath);
            properties.put("status", status);
            properties.put("reason", reason);
            properties.put("timestamp", time);

            map.putAll(properties);

            resolver.commit();

            LOG.info(
                    "Notification audit saved. "
                            + "eventId=[{}], status=[{}]",
                    eventId,
                    status
            );

        } catch (Exception e) {

            LOG.error(
                    "Unable to save notification audit. "
                            + "eventId=[{}], status=[{}]",
                    eventId,
                    status,
                    e
            );
        }
    }

    private ResourceResolver getResolver()
            throws LoginException {

        return resourceResolverFactory.getServiceResourceResolver(
                Collections.singletonMap(
                        ResourceResolverFactory.SUBSERVICE,
                        SUB_SERVICE
                )
        );
    }

    private String safeName(String value) {

        return value.replaceAll(
                "[^a-zA-Z0-9._-]",
                "_"
        );
    }
}