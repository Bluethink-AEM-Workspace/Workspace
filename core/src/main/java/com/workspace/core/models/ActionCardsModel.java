package com.workspace.core.models;

import java.util.List;

public interface ActionCardsModel {

    String getSectionTitle();

    List<ActionCardItem> getCards();

    boolean isHasContent();

    interface ActionCardItem {
        String getTitle();
        String getDescription();
        String getLinkLabel1();
        String getLink1();
        String getTarget1();
        String getLinkLabel2();
        String getLink2();
        String getTarget2();
        boolean isValidCta1();
        boolean isValidCta2();
        boolean isHasCtas();
    }
}