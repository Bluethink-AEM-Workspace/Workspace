package com.workspace.core.models.impl;

import com.workspace.core.models.ActionCardsModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Model(
    adaptables = {SlingHttpServletRequest.class, Resource.class},
    adapters = {ActionCardsModel.class},
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class ActionCardsModelImpl implements ActionCardsModel {

    @ValueMapValue(name = "title")
    private String sectionTitle;

    @ChildResource(name = "cardsDetails")
    private List<Resource> cardResources;

    private List<ActionCardItem> cards = new ArrayList<>();

    @PostConstruct
    protected void init() {
        if (cardResources != null) {
            for (Resource res : cardResources) {
                ActionCardItemImpl item = new ActionCardItemImpl(res);
                if (StringUtils.isNotBlank(item.getTitle()) || 
                    StringUtils.isNotBlank(item.getDescription()) || 
                    item.isHasCtas()) {
                    cards.add(item);
                }
            }
        }
    }

    @Override
    public String getSectionTitle() {
        return sectionTitle;
    }

    @Override
    public List<ActionCardItem> getCards() {
        return Collections.unmodifiableList(cards);
    }

    @Override
    public boolean isHasContent() {
        return !cards.isEmpty();
    }

    public static class ActionCardItemImpl implements ActionCardItem {
        private final String title;
        private final String description;
        private final String linkLabel1;
        private final String link1;
        private final String target1;
        private final String linkLabel2;
        private final String link2;
        private final String target2;
        private final boolean validCta1;
        private final boolean validCta2;

        public ActionCardItemImpl(Resource resource) {
            this.title = resource.getValueMap().get("title", String.class);
            this.description = resource.getValueMap().get("description", String.class);
            
            // CTA 1 Setup
            String lLabel1 = resource.getValueMap().get("linkLabel1", String.class);
            String lPath1 = sanitizePath(resource.getValueMap().get("link1", String.class));
            this.validCta1 = StringUtils.isNotBlank(lLabel1) && StringUtils.isNotBlank(lPath1);
            this.linkLabel1 = this.validCta1 ? lLabel1 : null;
            this.link1 = this.validCta1 ? lPath1 : null;
            this.target1 = isExternalLink(this.link1) ? "_blank" : "_self";

            // CTA 2 Setup
            String lLabel2 = resource.getValueMap().get("linkLabel2", String.class);
            String lPath2 = sanitizePath(resource.getValueMap().get("link2", String.class));
            this.validCta2 = StringUtils.isNotBlank(lLabel2) && StringUtils.isNotBlank(lPath2);
            this.linkLabel2 = this.validCta2 ? lLabel2 : null;
            this.link2 = this.validCta2 ? lPath2 : null;
            this.target2 = isExternalLink(this.link2) ? "_blank" : "_self";
        }

        private String sanitizePath(String path) {
            if (StringUtils.isBlank(path)) {
                return null;
            }

            // External Links
            if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("//")) {
                return path;
            }

            // Internal AEM Links
            if (path.startsWith("/content")) {
                return path.endsWith(".html") ? path : path + ".html";
            }

            return null;
        }

        private boolean isExternalLink(String path) {
            return StringUtils.isNotBlank(path) && 
                  (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("//"));
        }

        @Override public String getTitle() { return title; }
        @Override public String getDescription() { return description; }
        @Override public String getLinkLabel1() { return linkLabel1; }
        @Override public String getLink1() { return link1; }
        @Override public String getTarget1() { return target1; }
        @Override public String getLinkLabel2() { return linkLabel2; }
        @Override public String getLink2() { return link2; }
        @Override public String getTarget2() { return target2; }
        @Override public boolean isValidCta1() { return validCta1; }
        @Override public boolean isValidCta2() { return validCta2; }
        @Override public boolean isHasCtas() { return validCta1 || validCta2; }
    }
}