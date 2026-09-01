package com.workspace.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.jcr.Value;

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DamAdminRecipientServiceTest {

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @Mock
    private ResourceResolver resourceResolver;

    @Mock
    private UserManager userManager;

    @Mock
    private Group damAdminGroup;

    @Mock
    private Authorizable user1;

    @Mock
    private Authorizable user2;

    @Mock
    private Authorizable nestedGroup;

    @Mock
    private Value emailValue1;

    @Mock
    private Value emailValue2;

    private DamAdminRecipientService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        service = new DamAdminRecipientService();

        Field field =
                DamAdminRecipientService.class
                        .getDeclaredField("resourceResolverFactory");

        field.setAccessible(true);

        field.set(
                service,
                resourceResolverFactory);
    }

    // ---------------------------------------------------------
    // Successful recipient resolution
    // ---------------------------------------------------------

    @Test
    void shouldReturnDamAdminEmailAddresses()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        // -----------------------------------------------------
        // User 1
        // -----------------------------------------------------

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenReturn(true);

        when(user1.getProperty("profile/email"))
                .thenReturn(new Value[] {
                        emailValue1
                });

        when(emailValue1.getString())
                .thenReturn("admin1@example.com");

        // -----------------------------------------------------
        // User 2
        // -----------------------------------------------------

        when(user2.isGroup())
                .thenReturn(false);

        when(user2.hasProperty("profile/email"))
                .thenReturn(true);

        when(user2.getProperty("profile/email"))
                .thenReturn(new Value[] {
                        emailValue2
                });

        when(emailValue2.getString())
                .thenReturn("admin2@example.com");

        // -----------------------------------------------------
        // DAM Admin group members
        // -----------------------------------------------------

        Iterator<Authorizable> members =
                Arrays.<Authorizable>asList(
                        user1,
                        user2)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        // -----------------------------------------------------
        // Execute
        // -----------------------------------------------------

        List<String> recipients =
                service.getRecipientEmails();

        // -----------------------------------------------------
        // Verify
        // -----------------------------------------------------

        assertEquals(
                2,
                recipients.size());

        assertTrue(
                recipients.contains(
                        "admin1@example.com"));

        assertTrue(
                recipients.contains(
                        "admin2@example.com"));
    }

    // ---------------------------------------------------------
    // Nested groups should be ignored
    // ---------------------------------------------------------

    @Test
    void shouldIgnoreNestedGroups()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        when(nestedGroup.isGroup())
                .thenReturn(true);

        Iterator<Authorizable> members =
                Collections.<Authorizable>singletonList(
                        nestedGroup)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // User without email
    // ---------------------------------------------------------

    @Test
    void shouldIgnoreUserWithoutEmail()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenReturn(false);

        Iterator<Authorizable> members =
                Collections.<Authorizable>singletonList(
                        user1)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Blank email
    // ---------------------------------------------------------

    @Test
    void shouldIgnoreBlankEmail()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenReturn(true);

        when(user1.getProperty("profile/email"))
                .thenReturn(new Value[] {
                        emailValue1
                });

        when(emailValue1.getString())
                .thenReturn("   ");

        Iterator<Authorizable> members =
                Collections.<Authorizable>singletonList(
                        user1)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Empty group
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenGroupHasNoMembers()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        Iterator<Authorizable> members =
                Collections.<Authorizable>emptyList()
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Group does not exist
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenGroupDoesNotExist()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(null);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Configured authorizable is not a group
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenConfiguredAuthorizableIsNotGroup()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(user1);

        when(user1.isGroup())
                .thenReturn(false);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // ResourceResolver cannot adapt to UserManager
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenUserManagerIsUnavailable()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(null);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Null configured group
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenAdminGroupIsNull()
            throws Exception {

        setAdminGroup(null);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());

        verify(
                resourceResolverFactory,
                never())
                .getServiceResourceResolver(anyMap());
    }

    // ---------------------------------------------------------
    // Blank configured group
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenAdminGroupIsBlank()
            throws Exception {

        setAdminGroup("   ");

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());

        verify(
                resourceResolverFactory,
                never())
                .getServiceResourceResolver(anyMap());
    }

    // ---------------------------------------------------------
    // Duplicate email addresses
    // ---------------------------------------------------------

    @Test
    void shouldRemoveDuplicateEmailAddresses()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenReturn(true);

        when(user1.getProperty("profile/email"))
                .thenReturn(new Value[] {
                        emailValue1
                });

        when(emailValue1.getString())
                .thenReturn("admin@example.com");

        when(user2.isGroup())
                .thenReturn(false);

        when(user2.hasProperty("profile/email"))
                .thenReturn(true);

        when(user2.getProperty("profile/email"))
                .thenReturn(new Value[] {
                        emailValue2
                });

        when(emailValue2.getString())
                .thenReturn("admin@example.com");

        Iterator<Authorizable> members =
                Arrays.<Authorizable>asList(
                        user1,
                        user2)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertEquals(
                1,
                recipients.size());

        assertTrue(
                recipients.contains(
                        "admin@example.com"));
    }

    // ---------------------------------------------------------
    // Null email property values
    // ---------------------------------------------------------

    @Test
    void shouldIgnoreNullEmailValues()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenReturn(true);

        when(user1.getProperty("profile/email"))
                .thenReturn(null);

        Iterator<Authorizable> members =
                Collections.<Authorizable>singletonList(
                        user1)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Empty email property array
    // ---------------------------------------------------------

    @Test
    void shouldIgnoreEmptyEmailPropertyArray()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenReturn(true);

        when(user1.getProperty("profile/email"))
                .thenReturn(new Value[0]);

        Iterator<Authorizable> members =
                Collections.<Authorizable>singletonList(
                        user1)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Null first Value
    // ---------------------------------------------------------

    @Test
    void shouldIgnoreNullFirstEmailValue()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenReturn(true);

        when(user1.getProperty("profile/email"))
                .thenReturn(new Value[] {
                        null
                });

        Iterator<Authorizable> members =
                Collections.<Authorizable>singletonList(
                        user1)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Service ResourceResolver failure
    // ---------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenServiceResolverFails()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenThrow(
                        new RuntimeException(
                                "Unable to get service resolver"));

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());
    }

    // ---------------------------------------------------------
    // Exception while resolving one member
    // ---------------------------------------------------------

    @Test
    void shouldContinueWhenOneMemberThrowsException()
            throws Exception {

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable("dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        // User 1 throws an exception

        when(user1.isGroup())
                .thenReturn(false);

        when(user1.hasProperty("profile/email"))
                .thenThrow(
                        new RuntimeException(
                                "Member error"));

        // User 2 is valid

        when(user2.isGroup())
                .thenReturn(false);

        when(user2.hasProperty("profile/email"))
                .thenReturn(true);

        when(user2.getProperty("profile/email"))
                .thenReturn(new Value[] {
                        emailValue2
                });

        when(emailValue2.getString())
                .thenReturn("admin2@example.com");

        Iterator<Authorizable> members =
                Arrays.<Authorizable>asList(
                        user1,
                        user2)
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertEquals(
                1,
                recipients.size());

        assertTrue(
                recipients.contains(
                        "admin2@example.com"));
    }

    // ---------------------------------------------------------
    // Activation / configuration
    // ---------------------------------------------------------

    @Test
    void shouldLoadConfiguredAdminGroup()
            throws Exception {

        DamAdminRecipientService.Config config =
                org.mockito.Mockito.mock(
                        DamAdminRecipientService.Config.class);

        when(config.adminGroup())
                .thenReturn("custom-dam-admins");

        service.activate(config);

        when(resourceResolverFactory
                .getServiceResourceResolver(anyMap()))
                .thenReturn(resourceResolver);

        when(resourceResolver
                .adaptTo(UserManager.class))
                .thenReturn(userManager);

        when(userManager.getAuthorizable(
                "custom-dam-admins"))
                .thenReturn(damAdminGroup);

        when(damAdminGroup.isGroup())
                .thenReturn(true);

        Iterator<Authorizable> members =
                Collections.<Authorizable>emptyList()
                        .iterator();

        when(damAdminGroup.getMembers())
                .thenReturn(members);

        List<String> recipients =
                service.getRecipientEmails();

        assertTrue(
                recipients.isEmpty());

        verify(userManager)
                .getAuthorizable(
                        "custom-dam-admins");
    }

    // ---------------------------------------------------------
    // Helper
    // ---------------------------------------------------------

    private void setAdminGroup(
            String groupName)
            throws Exception {

        Field field =
                DamAdminRecipientService.class
                        .getDeclaredField("adminGroup");

        field.setAccessible(true);

        field.set(
                service,
                groupName);
    }
}