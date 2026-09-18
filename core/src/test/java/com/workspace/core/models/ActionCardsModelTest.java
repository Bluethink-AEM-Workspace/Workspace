package com.workspace.core.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

@ExtendWith(AemContextExtension.class)
class ActionCardsModelTest {

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    private ModelFactory modelFactory;

    @BeforeEach
    void setUp() {
        modelFactory = context.getService(ModelFactory.class);
    }

    @Test
    void shouldReadSectionTitle() {
        createActionCardsResource("Talk to an expert");

        ActionCardsModel model = getModel();

        assertEquals("Talk to an expert", model.getTitle());
    }

    @Test
    void shouldReadMultipleCardsAndPreserveOrder() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card One",
                "Description One",
                null,
                null,
                null,
                null);

        createCard(
                "item1",
                "Card Two",
                "Description Two",
                null,
                null,
                null,
                null);

        createCard(
                "item2",
                "Card Three",
                "Description Three",
                null,
                null,
                null,
                null);

        ActionCardsModel model = getModel();

        assertNotNull(model);
        assertNotNull(model.getCards());
        assertEquals(3, model.getCards().size());

        assertEquals("Card One", model.getCards().get(0).getTitle());
        assertEquals("Card Two", model.getCards().get(1).getTitle());
        assertEquals("Card Three", model.getCards().get(2).getTitle());

        assertEquals(
                "Description One",
                model.getCards().get(0).getDescription());

        assertEquals(
                "Description Two",
                model.getCards().get(1).getDescription());

        assertEquals(
                "Description Three",
                model.getCards().get(2).getDescription());
    }

    @Test
    void shouldReadCardProperties() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                "Card Description",
                "Learn more",
                "/content/workspace/page-one",
                "Contact us",
                "/content/workspace/contact");

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals("Card Title", card.getTitle());
        assertEquals("Card Description", card.getDescription());
        assertEquals("Learn more", card.getLinkLabel1());
        assertEquals(
                "/content/workspace/page-one.html",
                card.getLink1());
        assertEquals("Contact us", card.getLinkLabel2());
        assertEquals(
                "/content/workspace/contact.html",
                card.getLink2());
    }

    @Test
    void shouldAllowMissingCardProperties() {
        createActionCardsResource(null);

        createCard(
                "item0",
                null,
                null,
                null,
                null,
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertNull(card.getTitle());
        assertNull(card.getDescription());
        assertNull(card.getLinkLabel1());
        assertNull(card.getLink1());
        assertNull(card.getLinkLabel2());
        assertNull(card.getLink2());
    }

    @Test
    void shouldAddHtmlToWorkspaceLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/page-one.html",
                card.getLink1());
    }

    @Test
    void shouldHandleWorkspaceLinkWithTrailingSlash() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/us/en/",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/us/en.html",
                card.getLink1());
    }

    @Test
    void shouldHandleMultipleTrailingSlashes() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/us/en///",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/us/en.html",
                card.getLink1());
    }

    @Test
    void shouldNotModifyExistingHtmlWorkspaceLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one.html",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/page-one.html",
                card.getLink1());
    }

    @Test
    void shouldPreserveQueryParametersOnWorkspaceLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one?mode=edit",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/page-one.html?mode=edit",
                card.getLink1());
    }

    @Test
    void shouldPreserveFragmentOnWorkspaceLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one#section",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/page-one.html#section",
                card.getLink1());
    }

    @Test
    void shouldPreserveQueryAndFragmentOnWorkspaceLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one?mode=view#section",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/page-one.html?mode=view#section",
                card.getLink1());
    }

    @Test
    void shouldPreserveExistingHtmlWithQueryAndFragment() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one.html?mode=view#section",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "/content/workspace/page-one.html?mode=view#section",
                card.getLink1());
    }

    @Test
    void shouldNotModifyExternalHttpLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Visit website",
                "https://example.com/page",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "https://example.com/page",
                card.getLink1());
    }

    @Test
    void shouldNotModifyExternalHttpsLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Visit website",
                "https://www.example.com/page",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "https://www.example.com/page",
                card.getLink1());
    }

    @Test
    void shouldNotModifyMailtoLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Email us",
                "mailto:test@example.com",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "mailto:test@example.com",
                card.getLink1());
    }

    @Test
    void shouldNotModifyTelLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Call us",
                "tel:+1234567890",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals(
                "tel:+1234567890",
                card.getLink1());
    }

    @Test
    void shouldReturnNullForMissingLink() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                null,
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertNull(card.getLink1());
    }

    @Test
    void shouldReturnBlankLinkUnchanged() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "   ",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertEquals("   ", card.getLink1());
    }

    @Test
    void shouldValidateFirstCtaWhenLabelAndLinkArePresent() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertTrue(card.isCta1Valid());
    }

    @Test
    void shouldInvalidateFirstCtaWhenLabelIsMissing() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                null,
                "/content/workspace/page-one",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertFalse(card.isCta1Valid());
    }

    @Test
    void shouldInvalidateFirstCtaWhenLinkIsMissing() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                null,
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertFalse(card.isCta1Valid());
    }

    @Test
    void shouldValidateSecondCtaWhenLabelAndLinkArePresent() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                null,
                null,
                "Contact us",
                "/content/workspace/contact");

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertTrue(card.isCta2Valid());
    }

    @Test
    void shouldInvalidateSecondCtaWhenLabelIsMissing() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                null,
                null,
                null,
                "/content/workspace/contact");

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertFalse(card.isCta2Valid());
    }

    @Test
    void shouldInvalidateSecondCtaWhenLinkIsMissing() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                null,
                null,
                "Contact us",
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertFalse(card.isCta2Valid());
    }

    @Test
    void shouldInvalidateCtaWhenLabelAndLinkAreBlank() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                " ",
                " ",
                null,
                null);

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertFalse(card.isCta1Valid());
    }

    @Test
    void shouldSupportTwoValidCtasOnSameCard() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one",
                "Contact us",
                "/content/workspace/contact");

        ActionCardsModel.Card card = getModel().getCards().get(0);

        assertTrue(card.isCta1Valid());
        assertTrue(card.isCta2Valid());

        assertEquals("Learn more", card.getLinkLabel1());
        assertEquals(
                "/content/workspace/page-one.html",
                card.getLink1());

        assertEquals("Contact us", card.getLinkLabel2());
        assertEquals(
                "/content/workspace/contact.html",
                card.getLink2());
    }

    @Test
    void shouldReturnEmptyCardsWhenCardsResourceIsMissing() {
        context.create().resource(
                "/content/action-cards",
                "title",
                "Action Cards");

        ActionCardsModel model = getModel();

        assertNotNull(model);
        assertEquals("Action Cards", model.getTitle());
        assertNotNull(model.getCards());
        assertTrue(model.getCards().isEmpty());
    }

    private void createActionCardsResource(String title) {
        if (title != null) {
            context.create().resource(
                    "/content/action-cards",
                    "title",
                    title);
        } else {
            context.create().resource("/content/action-cards");
        }

        context.create().resource("/content/action-cards/cards");
    }

    private void createCard(
            String itemName,
            String title,
            String description,
            String linkLabel1,
            String link1,
            String linkLabel2,
            String link2) {

        Map<String, Object> properties = new HashMap<>();

        if (title != null) {
            properties.put("title", title);
        }

        if (description != null) {
            properties.put("description", description);
        }

        if (linkLabel1 != null) {
            properties.put("linkLabel1", linkLabel1);
        }

        if (link1 != null) {
            properties.put("link1", link1);
        }

        if (linkLabel2 != null) {
            properties.put("linkLabel2", linkLabel2);
        }

        if (link2 != null) {
            properties.put("link2", link2);
        }

        context.create().resource(
                "/content/action-cards/cards/" + itemName,
                properties);
    }

    private ActionCardsModel getModel() {
        Resource resource = context.resourceResolver()
                .getResource("/content/action-cards");

        assertNotNull(resource);

        return modelFactory.createModel(
                resource,
                ActionCardsModel.class);
    }
}