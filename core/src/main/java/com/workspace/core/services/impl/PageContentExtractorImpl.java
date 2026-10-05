package com.workspace.core.services.impl;

import com.day.cq.wcm.api.Page;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;

import java.util.HashMap;
import java.util.Map;

@Component(service = PageContentExtractorImpl.class, immediate = true)
public class PageContentExtractorImpl {

    public Map<String, Object> getPageDataForAlgolia(Page page) {
        Map<String, Object> pageData = new HashMap<>();

        if (page == null) {
            return pageData;
        }

        String path = page.getPath();
        String title = StringUtils.defaultIfEmpty(page.getPageTitle(), page.getTitle());
        String description = page.getDescription();

        StringBuilder fullContent = new StringBuilder();
        Resource contentResource = page.getContentResource();
        
        if (contentResource != null) {
            extractTextRecursively(contentResource, fullContent);
        }

        pageData.put("objectID", path);
        pageData.put("title", StringUtils.defaultString(title, page.getName()));
        pageData.put("path", path + ".html");
        pageData.put("description", StringUtils.defaultString(description, ""));
        pageData.put("content", fullContent.toString().trim());

        return pageData;
    }

    private void extractTextRecursively(Resource resource, StringBuilder fullContent) {
        ValueMap properties = resource.getValueMap();

        // Core Components & Custom Components text properties
        String[] textProperties = {
            "text", "jcr:title", "jcr:description", "subTitle", 
            "description", "title", "rtText", "cq:panelTitle"
        };

        for (String prop : textProperties) {
            if (properties.containsKey(prop)) {
                String value = properties.get(prop, String.class);
                if (StringUtils.isNotBlank(value)) {
                    // Strip HTML tags and clean up whitespace
                    String cleanText = value.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                    if (!cleanText.isEmpty()) {
                        fullContent.append(cleanText).append(" ");
                    }
                }
            }
        }

        for (Resource child : resource.getChildren()) {
            extractTextRecursively(child, fullContent);
        }
    }
}