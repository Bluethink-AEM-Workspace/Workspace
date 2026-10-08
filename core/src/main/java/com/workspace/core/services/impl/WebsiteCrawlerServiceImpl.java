package com.workspace.core.services.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workspace.core.services.SolrConfigurationService;
import com.workspace.core.services.WebsiteCrawlerService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;
import com.workspace.core.services.WebsiteCrawlerService.WebsiteCrawlerException;

@Component(service = WebsiteCrawlerService.class)
public class WebsiteCrawlerServiceImpl
                implements WebsiteCrawlerService {

        private static final Logger LOG = LoggerFactory.getLogger(WebsiteCrawlerServiceImpl.class);

        private static final String USER_AGENT = "Workspace-AEM-Solr-Crawler/2.0";

        private static final String CONTENT_TYPE_HTML = "text/html";

        private static final int MAX_RESPONSE_SIZE_BYTES = 5 * 1024 * 1024;

        private static final int MAX_RETRY_ATTEMPTS = 2;

        private static final int MIN_CONTENT_LENGTH_FOR_FALLBACK = 100;

        private static final String PLAIN_HTML_SUFFIX = ".plain.html";

        private static final String DEFAULT_PAGE_TITLE = "Untitled";

        private static final Pattern TITLE_PATTERN = Pattern.compile(
                        "(?is)<title\\b[^>]*>(.*?)</title\\s*>");

        private static final Pattern H1_PATTERN = Pattern.compile(
                        "(?is)<h1\\b[^>]*>(.*?)</h1\\s*>");

        private static final Pattern MAIN_PATTERN = Pattern.compile(
                        "(?is)<main\\b[^>]*>(.*?)</main\\s*>");

        private static final Pattern BODY_PATTERN = Pattern.compile(
                        "(?is)<body\\b[^>]*>(.*?)</body\\s*>");

        private static final Pattern SCRIPT_PATTERN = Pattern.compile(
                        "(?is)<script\\b[^>]*>.*?</script\\s*>");

        private static final Pattern STYLE_PATTERN = Pattern.compile(
                        "(?is)<style\\b[^>]*>.*?</style\\s*>");

        private static final Pattern NOSCRIPT_PATTERN = Pattern.compile(
                        "(?is)<noscript\\b[^>]*>.*?</noscript\\s*>");

        private static final Pattern TEMPLATE_PATTERN = Pattern.compile(
                        "(?is)<template\\b[^>]*>.*?</template\\s*>");

        private static final Pattern SVG_PATTERN = Pattern.compile(
                        "(?is)<svg\\b[^>]*>.*?</svg\\s*>");

        private static final Pattern CANVAS_PATTERN = Pattern.compile(
                        "(?is)<canvas\\b[^>]*>.*?</canvas\\s*>");

        private static final Pattern IFRAME_PATTERN = Pattern.compile(
                        "(?is)<iframe\\b[^>]*>.*?</iframe\\s*>");

        private static final Pattern HEADER_PATTERN = Pattern.compile(
                        "(?is)<header\\b[^>]*>.*?</header\\s*>");

        private static final Pattern FOOTER_PATTERN = Pattern.compile(
                        "(?is)<footer\\b[^>]*>.*?</footer\\s*>");

        private static final Pattern NAV_PATTERN = Pattern.compile(
                        "(?is)<nav\\b[^>]*>.*?</nav\\s*>");

        private static final Pattern ASIDE_PATTERN = Pattern.compile(
                        "(?is)<aside\\b[^>]*>.*?</aside\\s*>");

        private static final Pattern FORM_PATTERN = Pattern.compile(
                        "(?is)<form\\b[^>]*>.*?</form\\s*>");

        private static final Pattern COMMENT_PATTERN = Pattern.compile(
                        "(?s)<!--.*?-->");

        private static final Pattern BLOCK_CLOSE_TAG_PATTERN = Pattern.compile(
                        "(?is)</(?:p|div|section|article|li|h[1-6]|ul|ol|blockquote|pre|table|tr|td|th|br)\\s*>");

        private static final Pattern TAG_PATTERN = Pattern.compile(
                        "(?is)<[^>]+>");

        private static final Pattern LINK_TAG_PATTERN = Pattern.compile(
                        "(?is)<a\\b[^>]*>");

        private static final Pattern HREF_ATTRIBUTE_PATTERN = Pattern.compile(
                        "(?is)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

        private static final Pattern CANONICAL_LINK_PATTERN = Pattern.compile(
                        "(?is)<link\\b[^>]*>");

        private static final Pattern REL_ATTRIBUTE_PATTERN = Pattern.compile(
                        "(?is)\\brel\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

        private static final Pattern CHARSET_META_PATTERN = Pattern.compile(
                        "(?is)<meta\\b[^>]*\\bcharset\\s*=\\s*[\"']?([^\\s\"'>;]+)");

        private static final Pattern CONTENT_TYPE_META_PATTERN = Pattern.compile(
                        "(?is)<meta\\b[^>]*\\bcontent\\s*=\\s*[\"'][^\"']*?charset=\\s*([^\\s\"';]+)");

        private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile(
                        "&#(?:x([0-9a-fA-F]+)|([0-9]+));");

        @Reference
        private SolrConfigurationService solrConfigurationService;

        @Override
        public List<CrawledPage> crawl()
                        throws WebsiteCrawlerException {

                String indexingTarget = solrConfigurationService.getIndexingTarget();

                int maxPages = solrConfigurationService.getExternalCrawlMaxPages();

                int connectionTimeout = solrConfigurationService
                                .getExternalCrawlConnectionTimeout();

                validateConfiguration(
                                indexingTarget,
                                maxPages,
                                connectionTimeout);

                LOG.info(
                                "Starting external website crawl: {}",
                                indexingTarget);

                LOG.info(
                                "External crawl maximum pages: {}",
                                maxPages);

                LOG.info(
                                "External crawl connection timeout: {} ms",
                                connectionTimeout);

                Queue<String> urlsToVisit = new ArrayDeque<>();

                Set<String> visitedUrls = new HashSet<>();

                Set<String> crawledDocumentUrls = new HashSet<>();

                List<CrawledPage> crawledPages = new ArrayList<>();

                String normalizedTarget = normalizeUrl(indexingTarget);

                if (normalizedTarget == null) {
                        throw new WebsiteCrawlerException(
                                        "Unable to normalize external crawl target: "
                                                        + indexingTarget);
                }

                urlsToVisit.add(normalizedTarget);

                URI rootUri = createUri(normalizedTarget);

                String rootHost = rootUri.getHost();

                RequestConfig requestConfig = RequestConfig.custom()
                                .setConnectTimeout(
                                                connectionTimeout)
                                .setConnectionRequestTimeout(
                                                connectionTimeout)
                                .setSocketTimeout(
                                                connectionTimeout)
                                .build();

                try (CloseableHttpClient httpClient = HttpClients.custom()
                                .setDefaultRequestConfig(
                                                requestConfig)
                                .setRedirectStrategy(
                                                new LaxRedirectStrategy())
                                .setUserAgent(
                                                USER_AGENT)
                                .disableAutomaticRetries()
                                .build()) {

                        while (!urlsToVisit.isEmpty()
                                        && crawledPages.size() < maxPages) {

                                String currentUrl = urlsToVisit.poll();

                                if (currentUrl == null
                                                || visitedUrls.contains(currentUrl)) {
                                        continue;
                                }

                                visitedUrls.add(currentUrl);

                                if (!isAllowedUrl(
                                                currentUrl,
                                                rootHost)) {

                                        LOG.debug(
                                                        "Skipping URL outside allowed host: {}",
                                                        currentUrl);
                                        continue;
                                }

                                try {

                                        CrawledResult result = fetchPage(
                                                        httpClient,
                                                        currentUrl);

                                        if (result == null) {
                                                continue;
                                        }

                                        String documentUrl = normalizeUrl(
                                                        result.canonicalUrl);

                                        if (documentUrl == null) {
                                                documentUrl = currentUrl;
                                        }

                                        if (!isAllowedUrl(
                                                        documentUrl,
                                                        rootHost)) {

                                                documentUrl = currentUrl;
                                        }

                                        /*
                                         * Prevent the same logical page from being added
                                         * more than once during the crawl.
                                         *
                                         * This protects against:
                                         * /page
                                         * /page/
                                         * URL fragments
                                         * tracking query parameters
                                         * canonical URL duplicates
                                         */
                                        if (crawledDocumentUrls.add(
                                                        documentUrl)) {

                                                CrawledPage page = new CrawledPage(
                                                                documentUrl,
                                                                safeValue(
                                                                                result.title,
                                                                                DEFAULT_PAGE_TITLE),
                                                                safeValue(
                                                                                result.content,
                                                                                ""));

                                                crawledPages.add(page);

                                                LOG.info(
                                                                "Crawled external page: {} "
                                                                                + "(content length: {}, links: {})",
                                                                documentUrl,
                                                                result.content.length(),
                                                                result.links.size());

                                        } else {

                                                LOG.debug(
                                                                "Skipping duplicate document URL: {}",
                                                                documentUrl);
                                        }

                                        /*
                                         * Continue discovering links even when the current
                                         * page itself was identified as a duplicate.
                                         */
                                        for (String link : result.links) {

                                                String normalizedLink = normalizeUrl(link);

                                                if (normalizedLink == null
                                                                || visitedUrls.contains(
                                                                                normalizedLink)
                                                                || urlsToVisit.contains(
                                                                                normalizedLink)) {
                                                        continue;
                                                }

                                                if (isAllowedUrl(
                                                                normalizedLink,
                                                                rootHost)) {

                                                        urlsToVisit.add(
                                                                        normalizedLink);
                                                }
                                        }

                                } catch (WebsiteCrawlerException e) {

                                        LOG.warn(
                                                        "Failed to crawl URL: {}",
                                                        currentUrl,
                                                        e);

                                } catch (Exception e) {

                                        LOG.warn(
                                                        "Unexpected error while crawling URL: {}",
                                                        currentUrl,
                                                        e);
                                }
                        }

                } catch (IOException e) {

                        throw new WebsiteCrawlerException(
                                        "Failed to initialize external website crawler",
                                        e);
                }

                LOG.info(
                                "External website crawl completed. "
                                                + "Pages crawled: {}, URLs visited: {}, "
                                                + "unique documents: {}",
                                crawledPages.size(),
                                visitedUrls.size(),
                                crawledDocumentUrls.size());

                return crawledPages;
        }

        private CrawledResult fetchPage(
                        CloseableHttpClient httpClient,
                        String url)
                        throws WebsiteCrawlerException {

                String html = fetchHtml(
                                httpClient,
                                url,
                                false);

                if (html == null
                                || html.trim().isEmpty()) {
                        return null;
                }

                String title = extractTitle(html);

                if (title.isEmpty()) {

                        title = extractFirstHeading(html);
                }

                String content = extractContent(html);

                Set<String> links = extractLinks(
                                html,
                                url);

                String canonicalUrl = extractCanonicalUrl(
                                html,
                                url);

                /*
                 * Edge Delivery Services fallback.
                 *
                 * The .plain.html rendition is useful when the normal rendered
                 * document does not contain enough extractable page content.
                 */
                if (content.length() < MIN_CONTENT_LENGTH_FOR_FALLBACK) {

                        String plainHtmlUrl = buildPlainHtmlUrl(url);

                        if (plainHtmlUrl != null
                                        && !plainHtmlUrl.equals(url)) {

                                LOG.debug(
                                                "Primary HTML content is small. "
                                                                + "Trying Edge Delivery plain HTML: {}",
                                                plainHtmlUrl);

                                String plainHtml = fetchHtml(
                                                httpClient,
                                                plainHtmlUrl,
                                                true);

                                if (plainHtml != null
                                                && !plainHtml.trim().isEmpty()) {

                                        String plainContent = extractContent(
                                                        plainHtml);

                                        if (plainContent.length() > content.length()) {

                                                LOG.info(
                                                                "Using .plain.html fallback for {}. "
                                                                                + "Content length improved from {} to {}",
                                                                url,
                                                                content.length(),
                                                                plainContent.length());

                                                content = plainContent;
                                        }

                                        if (title.isEmpty()) {

                                                title = extractTitle(
                                                                plainHtml);

                                                if (title.isEmpty()) {

                                                        title = extractFirstHeading(
                                                                        plainHtml);
                                                }
                                        }
                                }
                        }
                }

                return new CrawledResult(
                                title,
                                content,
                                links,
                                canonicalUrl);
        }

        private String fetchHtml(
                        CloseableHttpClient httpClient,
                        String url,
                        boolean allowPlainHtml)
                        throws WebsiteCrawlerException {

                WebsiteCrawlerException lastException = null;

                for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {

                        HttpGet request = new HttpGet(url);

                        request.setHeader(
                                        "User-Agent",
                                        USER_AGENT);

                        request.setHeader(
                                        "Accept",
                                        "text/html,application/xhtml+xml,"
                                                        + "application/xml;q=0.9,*/*;q=0.8");

                        request.setHeader(
                                        "Accept-Language",
                                        "en-US,en;q=0.9");

                        request.setHeader(
                                        "Cache-Control",
                                        "no-cache");

                        try (CloseableHttpResponse response = httpClient.execute(request)) {

                                int statusCode = response.getStatusLine()
                                                .getStatusCode();

                                if (statusCode < 200
                                                || statusCode >= 300) {

                                        /*
                                         * Retry temporary server-side failures.
                                         * A 404/403/400 is not retried because another
                                         * request is unlikely to change the result.
                                         */
                                        if (statusCode >= 500
                                                        && attempt < MAX_RETRY_ATTEMPTS) {

                                                LOG.debug(
                                                                "Temporary HTTP {} for {}. "
                                                                                + "Retrying attempt {}/{}",
                                                                statusCode,
                                                                url,
                                                                attempt + 1,
                                                                MAX_RETRY_ATTEMPTS);

                                                continue;
                                        }

                                        LOG.warn(
                                                        "Skipping URL because HTTP status "
                                                                        + "is not successful. URL: {}, status: {}",
                                                        url,
                                                        statusCode);

                                        return null;
                                }

                                HttpEntity entity = response.getEntity();

                                if (entity == null) {
                                        LOG.warn(
                                                        "Response has no entity. URL: {}",
                                                        url);
                                        return null;
                                }

                                ContentType contentType = ContentType.getOrDefault(
                                                entity);

                                String mimeType = contentType.getMimeType();

                                /*
                                 * Some servers omit Content-Type or send an unusual
                                 * value. We only reject clearly non-HTML responses.
                                 */
                                if (mimeType != null
                                                && !CONTENT_TYPE_HTML.equalsIgnoreCase(
                                                                mimeType)
                                                && !"application/xhtml+xml"
                                                                .equalsIgnoreCase(mimeType)) {

                                        LOG.debug(
                                                        "Skipping non-HTML response. "
                                                                        + "URL: {}, content type: {}",
                                                        url,
                                                        mimeType);

                                        return null;
                                }

                                byte[] bytes = readEntityWithLimit(
                                                entity,
                                                MAX_RESPONSE_SIZE_BYTES);

                                if (bytes.length == 0) {
                                        return null;
                                }

                                Charset charset = determineCharset(
                                                contentType,
                                                bytes);

                                String html = new String(
                                                bytes,
                                                charset);

                                if (html.trim().isEmpty()) {
                                        return null;
                                }

                                LOG.debug(
                                                "Fetched HTML page: {} "
                                                                + "({} bytes, charset: {}, plain fallback: {})",
                                                url,
                                                bytes.length,
                                                charset.name(),
                                                allowPlainHtml);

                                return html;

                        } catch (IOException e) {

                                lastException = new WebsiteCrawlerException(
                                                "Failed to fetch URL: " + url,
                                                e);

                                if (attempt < MAX_RETRY_ATTEMPTS) {

                                        LOG.debug(
                                                        "Fetch failed for {}. "
                                                                        + "Retrying attempt {}/{}",
                                                        url,
                                                        attempt + 1,
                                                        MAX_RETRY_ATTEMPTS);

                                } else {

                                        LOG.warn(
                                                        "Failed to fetch URL after {} attempts: {}",
                                                        MAX_RETRY_ATTEMPTS,
                                                        url);
                                }
                        }
                }

                if (lastException != null) {
                        throw lastException;
                }

                return null;
        }

        private byte[] readEntityWithLimit(
                        HttpEntity entity,
                        int maxBytes)
                        throws IOException {

                try (InputStream inputStream = entity.getContent()) {

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        byte[] buffer = new byte[8192];

                        int totalBytes = 0;

                        int bytesRead;

                        while ((bytesRead = inputStream.read(buffer)) != -1) {

                                totalBytes += bytesRead;

                                if (totalBytes > maxBytes) {

                                        LOG.warn(
                                                        "HTTP response exceeded maximum "
                                                                        + "allowed size of {} bytes",
                                                        maxBytes);

                                        EntityUtils.consumeQuietly(entity);

                                        return new byte[0];
                                }

                                outputStream.write(
                                                buffer,
                                                0,
                                                bytesRead);
                        }

                        return outputStream.toByteArray();
                }
        }

        private Charset determineCharset(
                        ContentType contentType,
                        byte[] bytes) {

                if (contentType != null
                                && contentType.getCharset() != null) {

                        return contentType.getCharset();
                }

                /*
                 * Detect charset from HTML metadata when HTTP headers
                 * do not specify it.
                 */
                String metadataSample = new String(
                                bytes,
                                StandardCharsets.ISO_8859_1);

                Matcher charsetMatcher = CHARSET_META_PATTERN.matcher(
                                metadataSample);

                if (charsetMatcher.find()) {

                        Charset charset = charsetFromName(
                                        charsetMatcher.group(1));

                        if (charset != null) {
                                return charset;
                        }
                }

                Matcher contentTypeMatcher = CONTENT_TYPE_META_PATTERN.matcher(
                                metadataSample);

                if (contentTypeMatcher.find()) {

                        Charset charset = charsetFromName(
                                        contentTypeMatcher.group(1));

                        if (charset != null) {
                                return charset;
                        }
                }

                return StandardCharsets.UTF_8;
        }

        private Charset charsetFromName(
                        String charsetName) {

                if (charsetName == null
                                || charsetName.trim().isEmpty()) {
                        return null;
                }

                try {

                        return Charset.forName(
                                        charsetName.trim());

                } catch (Exception e) {

                        LOG.debug(
                                        "Unsupported page charset: {}",
                                        charsetName);

                        return null;
                }
        }

        private String extractTitle(
                        String html) {

                if (html == null) {
                        return "";
                }

                Matcher matcher = TITLE_PATTERN.matcher(html);

                if (!matcher.find()) {
                        return "";
                }

                return cleanText(
                                matcher.group(1));
        }

        private String extractFirstHeading(
                        String html) {

                if (html == null) {
                        return "";
                }

                Matcher matcher = H1_PATTERN.matcher(html);

                if (!matcher.find()) {
                        return "";
                }

                return cleanText(
                                matcher.group(1));
        }

        private String extractContent(
                        String html) {

                if (html == null) {
                        return "";
                }

                String contentRoot = html;

                /*
                 * Prefer semantic <main> content.
                 */
                Matcher mainMatcher = MAIN_PATTERN.matcher(html);

                if (mainMatcher.find()) {

                        contentRoot = mainMatcher.group(1);

                } else {

                        /*
                         * Fall back to <body>.
                         */
                        Matcher bodyMatcher = BODY_PATTERN.matcher(html);

                        if (bodyMatcher.find()) {
                                contentRoot = bodyMatcher.group(1);
                        }
                }

                contentRoot = removeUnwantedElements(
                                contentRoot);

                /*
                 * Turn closing block-level elements into spaces before
                 * stripping tags, so words from separate elements do not
                 * get concatenated.
                 */
                contentRoot = BLOCK_CLOSE_TAG_PATTERN
                                .matcher(contentRoot)
                                .replaceAll(" ");

                contentRoot = TAG_PATTERN
                                .matcher(contentRoot)
                                .replaceAll(" ");

                return cleanText(
                                contentRoot);
        }

        private String removeUnwantedElements(
                        String html) {

                String result = html;

                result = SCRIPT_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = STYLE_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = NOSCRIPT_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = TEMPLATE_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = SVG_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = CANVAS_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = IFRAME_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                /*
                 * Site navigation/chrome should not dominate search content.
                 */
                result = HEADER_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = FOOTER_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = NAV_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = ASIDE_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = FORM_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                result = COMMENT_PATTERN.matcher(
                                result)
                                .replaceAll(" ");

                return result;
        }

        private Set<String> extractLinks(
                        String html,
                        String baseUrl) {

                Set<String> links = new LinkedHashSet<>();

                if (html == null
                                || html.trim().isEmpty()) {
                        return links;
                }

                Matcher linkTagMatcher = LINK_TAG_PATTERN.matcher(
                                html);

                while (linkTagMatcher.find()) {

                        String tag = linkTagMatcher.group();

                        String href = extractAttributeValue(
                                        tag,
                                        HREF_ATTRIBUTE_PATTERN);

                        if (href == null
                                        || href.trim().isEmpty()) {
                                continue;
                        }

                        String normalizedHref = href.trim();

                        if (isIgnoredHref(
                                        normalizedHref)) {
                                continue;
                        }

                        String resolvedUrl = resolveUrl(
                                        baseUrl,
                                        normalizedHref);

                        if (resolvedUrl != null) {
                                links.add(resolvedUrl);
                        }
                }

                return links;
        }

        private String extractCanonicalUrl(
                        String html,
                        String baseUrl) {

                if (html == null
                                || html.trim().isEmpty()) {
                        return null;
                }

                Matcher matcher = CANONICAL_LINK_PATTERN.matcher(
                                html);

                while (matcher.find()) {

                        String tag = matcher.group();

                        String rel = extractAttributeValue(
                                        tag,
                                        REL_ATTRIBUTE_PATTERN);

                        if (rel == null
                                        || !containsTokenIgnoreCase(
                                                        rel,
                                                        "canonical")) {
                                continue;
                        }

                        String href = extractAttributeValue(
                                        tag,
                                        HREF_ATTRIBUTE_PATTERN);

                        if (href == null
                                        || href.trim().isEmpty()) {
                                continue;
                        }

                        String canonicalUrl = resolveUrl(
                                        baseUrl,
                                        href.trim());

                        if (canonicalUrl != null) {

                                LOG.debug(
                                                "Canonical URL discovered. {} -> {}",
                                                baseUrl,
                                                canonicalUrl);

                                return canonicalUrl;
                        }
                }

                return null;
        }

        private String extractAttributeValue(
                        String tag,
                        Pattern attributePattern) {

                Matcher matcher = attributePattern.matcher(
                                tag);

                if (!matcher.find()) {
                        return null;
                }

                for (int i = 1; i <= matcher.groupCount(); i++) {

                        String value = matcher.group(i);

                        if (value != null) {
                                return decodeHtmlEntities(
                                                value.trim());
                        }
                }

                return null;
        }

        private boolean containsTokenIgnoreCase(
                        String value,
                        String token) {

                if (value == null
                                || token == null) {
                        return false;
                }

                String[] tokens = value.split("\\s+");

                for (String currentToken : tokens) {

                        if (token.equalsIgnoreCase(
                                        currentToken.trim())) {
                                return true;
                        }
                }

                return false;
        }

        private boolean isIgnoredHref(
                        String href) {

                if (href == null) {
                        return true;
                }

                String value = href.trim()
                                .toLowerCase();

                return value.isEmpty()
                                || value.startsWith("#")
                                || value.startsWith("javascript:")
                                || value.startsWith("mailto:")
                                || value.startsWith("tel:")
                                || value.startsWith("sms:")
                                || value.startsWith("data:")
                                || value.startsWith("blob:")
                                || value.startsWith("about:");
        }

        private String resolveUrl(
                        String baseUrl,
                        String href) {

                try {

                        URI baseUri = new URI(baseUrl);

                        URI linkUri = new URI(href);

                        URI resolvedUri = baseUri.resolve(
                                        linkUri);

                        return normalizeUrl(
                                        resolvedUri.toString());

                } catch (URISyntaxException e) {

                        LOG.debug(
                                        "Ignoring invalid URL: {}",
                                        href);

                        return null;
                }
        }

        private String buildPlainHtmlUrl(
                        String url) {

                try {

                        URI uri = new URI(url);

                        String path = uri.getPath();

                        if (path == null
                                        || path.isEmpty()) {
                                path = "/";
                        }

                        if (path.endsWith(
                                        PLAIN_HTML_SUFFIX)) {
                                return url;
                        }

                        if (path.endsWith("/")) {

                                path = path.substring(
                                                0,
                                                path.length() - 1);
                        }

                        path += PLAIN_HTML_SUFFIX;

                        URIBuilder builder = new URIBuilder();

                        builder.setScheme(
                                        uri.getScheme());

                        builder.setHost(
                                        uri.getHost());

                        if (uri.getPort() != -1) {
                                builder.setPort(
                                                uri.getPort());
                        }

                        builder.setPath(
                                        path);

                        /*
                         * Do not carry the original query parameters to the
                         * plain HTML rendition.
                         */
                        return builder.build()
                                        .toString();

                } catch (Exception e) {

                        LOG.debug(
                                        "Unable to build .plain.html URL for: {}",
                                        url);

                        return null;
                }
        }

        private String normalizeUrl(
                        String url) {

                if (url == null
                                || url.trim().isEmpty()) {
                        return null;
                }

                try {

                        URI uri = new URI(
                                        url.trim());

                        String scheme = uri.getScheme();

                        if (!"http".equalsIgnoreCase(
                                        scheme)
                                        && !"https".equalsIgnoreCase(
                                                        scheme)) {

                                return null;
                        }

                        String host = uri.getHost();

                        if (host == null
                                        || host.trim().isEmpty()) {
                                return null;
                        }

                        if (uri.getUserInfo() != null) {
                                return null;
                        }

                        String normalizedScheme = scheme.toLowerCase();

                        String normalizedHost = host.toLowerCase();

                        int port = uri.getPort();

                        /*
                         * Default HTTP/HTTPS ports do not need to be part
                         * of the canonical URL.
                         */
                        if (("http".equals(
                                        normalizedScheme)
                                        && port == 80)
                                        || ("https".equals(
                                                        normalizedScheme)
                                                        && port == 443)) {

                                port = -1;
                        }

                        String path = uri.getPath();

                        if (path == null
                                        || path.isEmpty()) {

                                path = "/";
                        }

                        /*
                         * Remove repeated trailing slash for normal page URLs,
                         * while keeping the root "/" intact.
                         */
                        while (path.length() > 1
                                        && path.endsWith("/")) {

                                path = path.substring(
                                                0,
                                                path.length() - 1);
                        }

                        String query = removeTrackingParameters(
                                        uri.getQuery());

                        URI normalizedUri = new URI(
                                        normalizedScheme,
                                        null,
                                        normalizedHost,
                                        port,
                                        path,
                                        query,
                                        null);

                        return normalizedUri.toString();

                } catch (URISyntaxException e) {

                        LOG.debug(
                                        "Ignoring invalid URL: {}",
                                        url);

                        return null;
                }
        }

        private String removeTrackingParameters(
                        String query) {

                if (query == null
                                || query.trim().isEmpty()) {
                        return null;
                }

                String[] parameters = query.split(
                                "&");

                StringBuilder cleanedQuery = new StringBuilder();

                for (String parameter : parameters) {

                        if (parameter == null
                                        || parameter.trim().isEmpty()) {
                                continue;
                        }

                        String parameterName = parameter;

                        int equalsIndex = parameter.indexOf('=');

                        if (equalsIndex >= 0) {

                                parameterName = parameter.substring(
                                                0,
                                                equalsIndex);
                        }

                        if (isTrackingParameter(
                                        parameterName)) {

                                continue;
                        }

                        if (cleanedQuery.length() > 0) {
                                cleanedQuery.append("&");
                        }

                        cleanedQuery.append(
                                        parameter);
                }

                if (cleanedQuery.length() == 0) {
                        return null;
                }

                return cleanedQuery.toString();
        }

        private boolean isTrackingParameter(
                        String parameterName) {

                if (parameterName == null) {
                        return false;
                }

                String name = parameterName.trim()
                                .toLowerCase();

                return name.startsWith("utm_")
                                || "gclid".equals(name)
                                || "fbclid".equals(name)
                                || "msclkid".equals(name)
                                || "mc_cid".equals(name)
                                || "mc_eid".equals(name)
                                || "_ga".equals(name)
                                || "_gl".equals(name);
        }

        private boolean isAllowedUrl(
                        String url,
                        String rootHost) {

                if (url == null
                                || rootHost == null) {
                        return false;
                }

                try {

                        URI uri = new URI(url);

                        String scheme = uri.getScheme();

                        if (!"http".equalsIgnoreCase(
                                        scheme)
                                        && !"https".equalsIgnoreCase(
                                                        scheme)) {
                                return false;
                        }

                        if (uri.getHost() == null) {
                                return false;
                        }

                        return rootHost.equalsIgnoreCase(
                                        uri.getHost());

                } catch (URISyntaxException e) {

                        return false;
                }
        }

        private void validateConfiguration(
                        String indexingTarget,
                        int maxPages,
                        int connectionTimeout)
                        throws WebsiteCrawlerException {

                if (indexingTarget == null
                                || indexingTarget.trim().isEmpty()) {

                        throw new WebsiteCrawlerException(
                                        "External crawl target is not configured");
                }

                if (!solrConfigurationService
                                .isExternalIndexingTarget()) {

                        LOG.info(
                                        "External website crawl rejected. "
                                                        + "Indexing target is not HTTP/HTTPS: {}",
                                        indexingTarget);

                        throw new WebsiteCrawlerException(
                                        "External crawl target must use HTTP "
                                                        + "or HTTPS: "
                                                        + indexingTarget);
                }

                if (maxPages <= 0) {

                        throw new WebsiteCrawlerException(
                                        "External crawl maximum pages must be greater than zero");
                }

                if (connectionTimeout <= 0) {

                        throw new WebsiteCrawlerException(
                                        "External crawl connection timeout "
                                                        + "must be greater than zero");
                }
        }

        private String cleanText(
                        String text) {

                if (text == null) {
                        return "";
                }

                String cleaned = decodeHtmlEntities(
                                text);

                /*
                 * Remove characters which commonly appear as invisible
                 * formatting artifacts in copied web content.
                 */
                cleaned = cleaned.replace(
                                "\u00A0",
                                " ");

                cleaned = cleaned.replace(
                                "\u200B",
                                "");

                cleaned = cleaned.replace(
                                "\u200C",
                                "");

                cleaned = cleaned.replace(
                                "\u200D",
                                "");

                cleaned = cleaned.replaceAll(
                                "\\s+",
                                " ");

                return cleaned.trim();
        }

        private String decodeHtmlEntities(
                        String text) {

                if (text == null
                                || text.isEmpty()) {
                        return "";
                }

                String decoded = text
                                .replace(
                                                "&nbsp;",
                                                " ")
                                .replace(
                                                "&amp;",
                                                "&")
                                .replace(
                                                "&lt;",
                                                "<")
                                .replace(
                                                "&gt;",
                                                ">")
                                .replace(
                                                "&quot;",
                                                "\"")
                                .replace(
                                                "&#39;",
                                                "'")
                                .replace(
                                                "&#x27;",
                                                "'")
                                .replace(
                                                "&apos;",
                                                "'")
                                .replace(
                                                "&mdash;",
                                                "—")
                                .replace(
                                                "&ndash;",
                                                "–")
                                .replace(
                                                "&hellip;",
                                                "…")
                                .replace(
                                                "&copy;",
                                                "©")
                                .replace(
                                                "&reg;",
                                                "®")
                                .replace(
                                                "&trade;",
                                                "™");

                Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(
                                decoded);

                StringBuffer result = new StringBuffer();

                while (matcher.find()) {

                        try {

                                int codePoint;

                                if (matcher.group(1) != null) {

                                        codePoint = Integer.parseInt(
                                                        matcher.group(1),
                                                        16);

                                } else {

                                        codePoint = Integer.parseInt(
                                                        matcher.group(2));
                                }

                                String replacement = new String(
                                                Character.toChars(
                                                                codePoint));

                                matcher.appendReplacement(
                                                result,
                                                Matcher.quoteReplacement(
                                                                replacement));

                        } catch (Exception e) {

                                matcher.appendReplacement(
                                                result,
                                                Matcher.quoteReplacement(
                                                                matcher.group()));
                        }
                }

                matcher.appendTail(
                                result);

                return result.toString();
        }

        private String safeValue(
                        String value,
                        String defaultValue) {

                if (value == null
                                || value.trim().isEmpty()) {

                        return defaultValue;
                }

                return value.trim();
        }

        private URI createUri(
                        String url)
                        throws WebsiteCrawlerException {

                try {

                        return new URI(
                                        url);

                } catch (URISyntaxException e) {

                        throw new WebsiteCrawlerException(
                                        "Invalid external crawl target: "
                                                        + url,
                                        e);
                }
        }

        private static class CrawledResult {

                private final String title;
                private final String content;
                private final Set<String> links;
                private final String canonicalUrl;

                private CrawledResult(
                                String title,
                                String content,
                                Set<String> links,
                                String canonicalUrl) {

                        this.title = title;

                        this.content = content;

                        this.links = links;

                        this.canonicalUrl = canonicalUrl;
                }
        }
}