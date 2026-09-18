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

        assertEquals(
                "Talk to an expert",
                model.getTitle());
    }

    @Test
    void shouldReadMultipleCards() {
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

        assertEquals(
                "Card One",
                model.getCards().get(0).getTitle());

        assertEquals(
                "Description One",
                model.getCards().get(0).getDescription());

        assertEquals(
                "Card Two",
                model.getCards().get(1).getTitle());

        assertEquals(
                "Description Two",
                model.getCards().get(1).getDescription());

        assertEquals(
                "Card Three",
                model.getCards().get(2).getTitle());

        assertEquals(
                "Description Three",
                model.getCards().get(2).getDescription());
    }

    @Test
    void shouldPreserveAuthoredCardOrder() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "First Card",
                null,
                null,
                null,
                null,
                null);

        createCard(
                "item1",
                "Second Card",
                null,
                null,
                null,
                null,
                null);

        createCard(
                "item2",
                "Third Card",
                null,
                null,
                null,
                null,
                null);

        ActionCardsModel model = getModel();

        assertNotNull(model);
        assertEquals(3, model.getCards().size());

        assertEquals(
                "First Card",
                model.getCards().get(0).getTitle());

        assertEquals(
                "Second Card",
                model.getCards().get(1).getTitle());

        assertEquals(
                "Third Card",
                model.getCards().get(2).getTitle());
    }

    @Test
    void shouldReadCardTitle() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                null,
                null,
                null,
                null);

        ActionCardsModel model = getModel();

        assertEquals(
                "Card Title",
                model.getCards().get(0).getTitle());
    }

    @Test
    void shouldReadCardDescription() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                "Card Description",
                null,
                null,
                null,
                null);

        ActionCardsModel model = getModel();

        assertEquals(
                "Card Description",
                model.getCards().get(0).getDescription());
    }

    @Test
    void shouldAllowMissingCardDescription() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                null,
                null,
                null,
                null);

        ActionCardsModel model = getModel();

        assertNotNull(model);
        assertEquals(1, model.getCards().size());

        assertEquals(
                "Card Title",
                model.getCards().get(0).getTitle());

        assertNull(
                model.getCards().get(0).getDescription());
    }

    @Test
    void shouldAllowMissingCardTitle() {
        createActionCardsResource(null);

        createCard(
                "item0",
                null,
                "Description without title",
                null,
                null,
                null,
                null);

        ActionCardsModel model = getModel();

        assertNotNull(model);
        assertEquals(1, model.getCards().size());

        assertNull(
                model.getCards().get(0).getTitle());

        assertEquals(
                "Description without title",
                model.getCards().get(0).getDescription());
    }

    @Test
    void shouldReadFirstCta() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one",
                null,
                null);

        ActionCardsModel model = getModel();

        ActionCardsModel.Card card = model.getCards().get(0);

        assertEquals(
                "Learn more",
                card.getLinkLabel1());

        assertEquals(
                "/content/workspace/page-one",
                card.getLink1());
    }

    @Test
    void shouldReadSecondCta() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                null,
                null,
                "Contact us",
                "/content/workspace/contact");

        ActionCardsModel model = getModel();

        ActionCardsModel.Card card = model.getCards().get(0);

        assertEquals(
                "Contact us",
                card.getLinkLabel2());

        assertEquals(
                "/content/workspace/contact",
                card.getLink2());
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

        ActionCardsModel model = getModel();

        assertTrue(
                model.getCards().get(0).isCta1Valid());
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

        ActionCardsModel model = getModel();

        assertFalse(
                model.getCards().get(0).isCta1Valid());
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

        ActionCardsModel model = getModel();

        assertFalse(
                model.getCards().get(0).isCta1Valid());
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

        ActionCardsModel model = getModel();

        assertTrue(
                model.getCards().get(0).isCta2Valid());
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

        ActionCardsModel model = getModel();

        assertFalse(
                model.getCards().get(0).isCta2Valid());
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

        ActionCardsModel model = getModel();

        assertFalse(
                model.getCards().get(0).isCta2Valid());
    }

    @Test
    void shouldInvalidateCtaWhenValuesAreBlank() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "   ",
                "   ",
                null,
                null);

        ActionCardsModel model = getModel();

        assertFalse(
                model.getCards().get(0).isCta1Valid());
    }

    @Test
    void shouldSupportTwoCtasOnSameCard() {
        createActionCardsResource(null);

        createCard(
                "item0",
                "Card Title",
                null,
                "Learn more",
                "/content/workspace/page-one",
                "Contact us",
                "/content/workspace/contact");

        ActionCardsModel model = getModel();

        ActionCardsModel.Card card = model.getCards().get(0);

        assertTrue(card.isCta1Valid());
        assertTrue(card.isCta2Valid());

        assertEquals(
                "Learn more",
                card.getLinkLabel1());

        assertEquals(
                "/content/workspace/page-one",
                card.getLink1());

        assertEquals(
                "Contact us",
                card.getLinkLabel2());

        assertEquals(
                "/content/workspace/contact",
                card.getLink2());
    }

    @Test
    void shouldReturnEmptyCardsWhenNoCardsAreConfigured() {
        createActionCardsResource("Action Cards");

        ActionCardsModel model = getModel();

        assertNotNull(model);
        assertEquals(
                "Action Cards",
                model.getTitle());

        assertNotNull(model.getCards());
        assertTrue(model.getCards().isEmpty());
    }

    @Test
    void shouldReturnEmptyCardsWhenCardsResourceIsMissing() {
        context.create().resource(
                "/content/action-cards");

        ActionCardsModel model = getModel();

        assertNotNull(model);
        assertNotNull(model.getCards());
        assertTrue(model.getCards().isEmpty());
    }

    private void createActionCardsResource(String title) {
        if (title != null) {
            context.create().resource(
                    "/content/action-cards",
                    "title", title);
        } else {
            context.create().resource(
                    "/content/action-cards");
        }

        context.create().resource(
                "/content/action-cards/cards");
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