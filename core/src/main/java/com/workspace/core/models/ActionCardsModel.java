package com.workspace.core.models;
import java.util.List;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * Sling Model Interface for the Action Cards component.
 */
@ConsumerType
public interface ActionCardsModel {

    /**
     * @return Global section title authored by the content author.
     */
    String getSectionTitle();

    /**
     * @return List of authored card items.
     */
    List<ActionCardItem> getCards();

    /**
     * @return true if section title or at least one valid card exists.
     */
    boolean isHasContent();

    /**
     * Child Interface representing an individual Action Card item.
     */
    interface ActionCardItem {

        /**
         * @return Title of the card.
         */
        String getTitle();

        /**
         * @return Description text of the card.
         */
        String getDescription();

        /**
         * @return Label text for the primary CTA link.
         */
        String getLinkLabel1();

        /**
         * @return Destination path for the primary CTA link.
         */
        String getLink1();

        /**
         * @return Label text for the secondary CTA link.
         */
        String getLinkLabel2();

        /**
         * @return Destination path for the secondary CTA link.
         */
        String getLink2();

        /**
         * @return true if CTA 1 has both a non-empty label and path.
         */
        boolean isValidCta1();

        /**
         * @return true if CTA 2 has both a non-empty label and path.
         */
        boolean isValidCta2();

        /**
         * @return true if at least one valid CTA pair exists.
         */
        boolean isHasCtas();
    }
}