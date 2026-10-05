package com.workspace.core.services.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Workspace - Algolia Search Configuration")
public @interface AlgoliaConfig {

    @AttributeDefinition(name = "Algolia Application ID")
    String appId() default "";

    @AttributeDefinition(name = "Algolia Admin API Key (Backend Indexing Only)")
    String adminApiKey() default "";

    @AttributeDefinition(name = "Algolia Search-Only API Key (Frontend Safe)")
    String searchApiKey() default "";

    @AttributeDefinition(name = "Algolia Index Name")
    String indexName() default "aem_pages_index";
}