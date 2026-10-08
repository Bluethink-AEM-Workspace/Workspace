package com.workspace.core.services;

import com.day.cq.wcm.api.Page;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONObject;

import org.osgi.service.component.annotations.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Converts an AEM page into the Elasticsearch document format.
 */
@Component(service = ElasticsearchDocumentBuilder.class)
public class ElasticsearchDocumentBuilder {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ElasticsearchDocumentBuilder.class
            );

    private static final String DATE_FORMAT =
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    public String build(Page page) throws Exception {

        if (page == null) {
            throw new IllegalArgumentException(
                    "Page cannot be null"
            );
        }

        Resource contentResource =
                page.getContentResource();

        if (contentResource == null) {
            throw new IllegalStateException(
                    "Page content resource not found: "
                            + page.getPath()
            );
        }

        ValueMap properties =
                contentResource.getValueMap();

        JSONObject document =
                new JSONObject();

        /*
         * Stable document identifier.
         */
        document.put(
                "id",
                page.getPath()
        );

        /*
         * Stable AEM path.
         */
        document.put(
                "path",
                page.getPath()
        );

        /*
         * Page title.
         */
        String title =
                firstNonBlank(
                        page.getTitle(),
                        properties.get(
                                "jcr:title",
                                String.class
                        )
                );

        document.put(
                "title",
                title == null ? "" : title
        );

        /*
         * Description.
         */
        String description =
                firstNonBlank(
                        page.getDescription(),
                        properties.get(
                                "jcr:description",
                                String.class
                        )
                );

        document.put(
                "description",
                description == null
                        ? ""
                        : description
        );

        /*
         * Template.
         */
        String template =
                properties.get(
                        "cq:template",
                        String.class
                );

        document.put(
                "template",
                template == null
                        ? ""
                        : template
        );

        /*
         * Tags.
         */
        String[] tags =
                properties.get(
                        "cq:tags",
                        String[].class
                );

        if (tags != null) {
            document.put(
                    "tags",
                    tags
            );
        } else {
            document.put(
                    "tags",
                    new String[0]
            );
        }

        /*
         * Last modified date.
         */
        Calendar lastModified =
                properties.get(
                        "cq:lastModified",
                        Calendar.class
                );

        if (lastModified != null) {

            document.put(
                    "lastModified",
                    formatDate(
                            lastModified
                    )
            );

        } else {

            document.put(
                    "lastModified",
                    ""
            );
        }

        /*
         * Searchable content.
         *
         * This reads common text properties from the page.
         * It can be extended later for custom components.
         */
        document.put(
                "content",
                buildSearchableContent(
                        properties
                )
        );

        String json =
                document.toString();

        LOG.debug(
                "Built Elasticsearch document for page: {}",
                page.getPath()
        );

        return json;
    }

    private String buildSearchableContent(
            ValueMap properties
    ) {

        StringBuilder content =
                new StringBuilder();

        appendValue(
                content,
                properties.get(
                        "text",
                        String.class
                )
        );

        appendValue(
                content,
                properties.get(
                        "textIsRich",
                        String.class
                )
        );

        appendValue(
                content,
                properties.get(
                        "message",
                        String.class
                )
        );

        appendValue(
                content,
                properties.get(
                        "description",
                        String.class
                )
        );

        return content.toString().trim();
    }

    private void appendValue(
            StringBuilder builder,
            String value
    ) {

        if (value == null
                || value.trim().isEmpty()) {
            return;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }

        builder.append(value.trim());
    }

    private String firstNonBlank(
            String first,
            String second
    ) {

        if (first != null
                && !first.trim().isEmpty()) {
            return first;
        }

        if (second != null
                && !second.trim().isEmpty()) {
            return second;
        }

        return "";
    }

    private String formatDate(
            Calendar calendar
    ) {

        Calendar utc =
                (Calendar) calendar.clone();

        utc.setTimeZone(
                TimeZone.getTimeZone("UTC")
        );

        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        DATE_FORMAT
                );

        formatter.setTimeZone(
                TimeZone.getTimeZone("UTC")
        );

        return formatter.format(
                utc.getTime()
        );
    }
}