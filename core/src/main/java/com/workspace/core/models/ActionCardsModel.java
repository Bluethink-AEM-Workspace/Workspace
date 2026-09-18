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

        private static final String WORKSPACE_CONTENT_PATH = "/content/workspace/";

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
            return processLink(resource.getValueMap().get("link1", String.class));
        }

        public String getLinkLabel2() {
            return resource.getValueMap().get("linkLabel2", String.class);
        }

        public String getLink2() {
            return processLink(resource.getValueMap().get("link2", String.class));
        }

        public boolean isCta1Valid() {
            return isNotBlank(getLinkLabel1()) && isNotBlank(getLink1());
        }

        public boolean isCta2Valid() {
            return isNotBlank(getLinkLabel2()) && isNotBlank(getLink2());
        }

        private String processLink(String link) {
            if (!isNotBlank(link)) {
                return link;
            }

            /*
             * Only process internal Workspace content links.
             * External links and other URI types remain unchanged.
             */
            if (!link.startsWith(WORKSPACE_CONTENT_PATH)) {
                return link;
            }

            /*
             * Separate query parameters and fragments from the path.
             */
            int queryIndex = link.indexOf('?');
            int fragmentIndex = link.indexOf('#');

            int suffixIndex = link.length();

            if (queryIndex >= 0) {
                suffixIndex = Math.min(suffixIndex, queryIndex);
            }

            if (fragmentIndex >= 0) {
                suffixIndex = Math.min(suffixIndex, fragmentIndex);
            }

            String path = link.substring(0, suffixIndex);
            String suffix = link.substring(suffixIndex);

            /*
             * Remove trailing slash from an internal content path
             * before adding .html.
             */
            while (path.length() > WORKSPACE_CONTENT_PATH.length()
                    && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            /*
             * Do not modify a path that already ends with .html.
             */
            if (path.endsWith(".html")) {
                return path + suffix;
            }

            return path + ".html" + suffix;
        }

        private boolean isNotBlank(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }
}
