package com.workspace.core.config;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Workspace Solr Index Configuration", description = "Configuration for AEM and external website Solr indexing")
public @interface SolrIndexConfiguration {

    @AttributeDefinition(name = "AEM Base URL", description = "Base URL of the AEM author instance")
    String aemBaseUrl() default "http://localhost:4502";

    @AttributeDefinition(name = "Solr Base URL", description = "Base URL of the Solr workspace core")
    String solrBaseUrl() default "http://localhost:8983/solr/workspace";

    @AttributeDefinition(name = "Indexing Target", description = "AEM content path or external website URL to index")
    String indexingTarget() default "/content/workspace/";

    @AttributeDefinition(name = "External Crawl Maximum Pages", description = "Maximum number of pages to crawl when indexing an external website")
    int externalCrawlMaxPages() default 50;

    @AttributeDefinition(name = "External Crawl Connection Timeout", description = "Connection timeout in milliseconds for external website crawling")
    int externalCrawlConnectionTimeout() default 10000;
}