package com.workspace.core.models;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class ActionCardsModel {

    @ValueMapValue
    private String title;

    @ChildResource(name = "cards")
    private List<Resource> cards;

    private List<Card> cardList;

    @PostConstruct
    protected void init() {
        if (cards == null) {
            cardList = Collections.emptyList();
            return;
        }

        cardList = cards.stream()
                .map(Card::new)
                .collect(Collectors.toList());
    }

    public String getTitle() {
        return title;
    }

    public List<Card> getCards() {
        return cardList;
    }

    public static class Card {

        private final Resource resource;

        public Card(Resource resource) {
            this.resource = resource;
        }

        public String getTitle() {
            return resource.getValueMap().get("title", String.class);
        }

        public String getDescription() {
            return resource.getValueMap().get("description", String.class);
        }

        public String getLinkLabel1() {
            return resource.getValueMap().get("linkLabel1", String.class);
        }

        public String getLink1() {
            return resource.getValueMap().get("link1", String.class);
        }

        public String getLinkLabel2() {
            return resource.getValueMap().get("linkLabel2", String.class);
        }

        public String getLink2() {
            return resource.getValueMap().get("link2", String.class);
        }

        public boolean isCta1Valid() {
            return isNotBlank(getLinkLabel1()) && isNotBlank(getLink1());
        }

        public boolean isCta2Valid() {
            return isNotBlank(getLinkLabel2()) && isNotBlank(getLink2());
        }

        private boolean isNotBlank(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }
}