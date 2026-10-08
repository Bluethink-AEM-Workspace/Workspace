package com.workspace.core.services;

import java.util.List;

public interface WebsiteCrawlerService {

    List<CrawledPage> crawl()
            throws WebsiteCrawlerException;

    class CrawledPage {

        private final String url;
        private final String title;
        private final String content;

        public CrawledPage(
                String url,
                String title,
                String content) {

            this.url = url;
            this.title = title;
            this.content = content;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }
    }

    class WebsiteCrawlerException
            extends Exception {

        private static final long serialVersionUID = 1L;

        public WebsiteCrawlerException(
                String message) {

            super(message);
        }

        public WebsiteCrawlerException(
                String message,
                Throwable cause) {

            super(message, cause);
        }
    }
}