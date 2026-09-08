/*

DamAdminRecipientService is responsible for resolving the email addresses of users belonging to the configured DAM Admin group in AEM. It uses the asset-expiry-service service user to access the repository, locates the configured group, iterates through its direct members, and retrieves valid profile/email values while filtering out invalid or duplicate addresses. The service is configurable through OSGi and provides the scheduler with the final list of recipients for asset expiry report notifications, with appropriate handling and logging for missing groups, invalid members, missing email properties, and repository errors.

*/


package com.workspace.core.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DamAdminRecipientService.class)
@Designate(ocd = DamAdminRecipientService.Config.class)
public class DamAdminRecipientService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DamAdminRecipientService.class);

    private static final String SUBSERVICE =
            "asset-expiry-service";

    @ObjectClassDefinition(
            name = "Workspace DAM Admin Recipient Configuration",
            description =
                    "Configuration used to resolve DAM Admin email recipients")
    public @interface Config {

        @AttributeDefinition(
                name = "DAM Admin Group",
                description =
                        "AEM group whose members receive asset expiry notifications")
        String adminGroup() default "dam-admins";
    }

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    private volatile String adminGroup =
            "dam-admins";

    @Activate
    @Modified
    protected void activate(Config config) {

        adminGroup = config.adminGroup();

        LOG.info(
                "DAM Admin recipient service configured. Group: {}",
                adminGroup);
    }

    /**
     * Resolves valid email addresses from the configured
     * DAM Admin group.
     *
     * @return list of valid email addresses
     */
    public List<String> getRecipientEmails() {

        if (adminGroup == null
                || adminGroup.trim().isEmpty()) {

            LOG.error(
                    "DAM Admin group is not configured");

            return Collections.emptyList();
        }

        List<String> recipients =
                new ArrayList<String>();

        try (ResourceResolver resolver =
                     getServiceResourceResolver()) {

            LOG.info(
                    "Service ResourceResolver successfully obtained for DAM Admin recipient lookup");

            UserManager userManager =
                    resolver.adaptTo(UserManager.class);

            if (userManager == null) {

                LOG.error(
                        "Unable to adapt ResourceResolver to UserManager");

                return Collections.emptyList();
            }

            LOG.info(
                    "Successfully adapted ResourceResolver to UserManager");

            LOG.info(
                    "Looking for DAM Admin group: {}",
                    adminGroup);

            Authorizable authorizable =
                    userManager.getAuthorizable(adminGroup);

            if (authorizable == null) {

                LOG.error(
                        "DAM Admin group was NOT found by service user. Group ID: {}",
                        adminGroup);

                return Collections.emptyList();
            }

            LOG.info(
                    "DAM Admin authorizable found. ID: {}, IsGroup: {}",
                    authorizable.getID(),
                    authorizable.isGroup());

            if (!authorizable.isGroup()) {

                LOG.error(
                        "Configured DAM Admin principal is not a group: {}",
                        adminGroup);

                return Collections.emptyList();
            }

            Group group =
                    (Group) authorizable;

            Iterator<Authorizable> members =
                    group.getMembers();

            int memberCount = 0;

            while (members.hasNext()) {

                Authorizable member =
                        members.next();

                memberCount++;

                try {

                    LOG.info(
                            "Found DAM Admin group member. ID: {}, IsGroup: {}",
                            member.getID(),
                            member.isGroup());

                    String email =
                            getEmail(member);

                    LOG.info(
                            "Resolved email for member {}: {}",
                            member.getID(),
                            email);

                    if (email != null
                            && !email.trim().isEmpty()
                            && !recipients.contains(email)) {

                        recipients.add(email);

                        LOG.info(
                                "Added email recipient: {}",
                                email);
                    }

                } catch (Exception e) {

                    LOG.error(
                            "Unable to resolve email for DAM Admin member: {}",
                            getAuthorizableIdSafely(member),
                            e);
                }
            }

            LOG.info(
                    "DAM Admin group {} contains {} direct member(s)",
                    adminGroup,
                    memberCount);

        } catch (Exception e) {

            LOG.error(
                    "Unable to resolve DAM Admin recipients for group: {}",
                    adminGroup,
                    e);
        }

        if (recipients.isEmpty()) {

            LOG.error(
                    "No valid DAM Admin email addresses found for group: {}",
                    adminGroup);

        } else {

            LOG.info(
                    "Resolved {} DAM Admin email recipient(s)",
                    recipients.size());
        }

        return recipients;
    }

    /**
     * Resolves the email address from an authorizable.
     *
     * @param authorizable user or group
     * @return email address or null
     * @throws RepositoryException if repository access fails
     */
    private String getEmail(
            Authorizable authorizable)
            throws RepositoryException {

        if (authorizable == null
                || authorizable.isGroup()) {

            return null;
        }

        String authorizableId =
                authorizable.getID();

        if (!authorizable.hasProperty("profile/email")) {

            LOG.warn(
                    "User {} does not have profile/email property",
                    authorizableId);

            return null;
        }

        javax.jcr.Value[] values =
                authorizable.getProperty(
                        "profile/email");

        if (values == null
                || values.length == 0
                || values[0] == null) {

            LOG.warn(
                    "User {} has an empty profile/email property",
                    authorizableId);

            return null;
        }

        String email =
                values[0].getString();

        if (email == null
                || email.trim().isEmpty()) {

            LOG.warn(
                    "User {} has a blank email address",
                    authorizableId);

            return null;
        }

        return email.trim();
    }

    /**
     * Safely gets the authorizable ID for logging.
     *
     * @param authorizable authorizable
     * @return ID or unknown
     */
    private String getAuthorizableIdSafely(
            Authorizable authorizable) {

        if (authorizable == null) {
            return "null";
        }

        try {
            return authorizable.getID();
        } catch (RepositoryException e) {
            return "unknown";
        }
    }

    /**
     * Gets a service ResourceResolver using the
     * asset-expiry-service subservice.
     *
     * @return service ResourceResolver
     * @throws Exception if resolver creation fails
     */
    private ResourceResolver getServiceResourceResolver()
            throws Exception {

        Map<String, Object> authInfo =
                new HashMap<String, Object>();

        authInfo.put(
                ResourceResolverFactory.SUBSERVICE,
                SUBSERVICE);

        LOG.debug(
                "Requesting service ResourceResolver using subservice: {}",
                SUBSERVICE);

        return resourceResolverFactory
                .getServiceResourceResolver(authInfo);
    }
}