package com.workspace.core.services.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.workspace.core.services.SolrConfigurationService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;
import com.workspace.core.services.WebsiteCrawlerService.WebsiteCrawlerException;

class WebsiteCrawlerServiceImplTest {

    private WebsiteCrawlerServiceImpl crawlerService;

    private SolrConfigurationService solrConfigurationService;

    @BeforeEach
    void setUp() throws Exception {

        solrConfigurationService = mock(SolrConfigurationService.class);

        crawlerService = new WebsiteCrawlerServiceImpl();

        Field field = WebsiteCrawlerServiceImpl.class
                .getDeclaredField(
                        "solrConfigurationService");

        field.setAccessible(true);

        field.set(
                crawlerService,
                solrConfigurationService);
    }

    // ---------------------------------------------------------------------
    // Configuration validation
    // ---------------------------------------------------------------------

    @Test
    void testCrawlFailsWhenTargetIsNull() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(null);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(10);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(5000);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "External crawl target is not configured",
                exception.getMessage());
    }

    @Test
    void testCrawlFailsWhenTargetIsBlank() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("   ");

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(10);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(5000);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "External crawl target is not configured",
                exception.getMessage());
    }

    @Test
    void testCrawlFailsWhenExternalIndexingTargetIsDisabled() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("https://example.com");

        when(solrConfigurationService
                .isExternalIndexingTarget())
                .thenReturn(false);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(10);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(5000);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "External crawl target must use HTTP or HTTPS: "
                        + "https://example.com",
                exception.getMessage());
    }

    @Test
    void testCrawlFailsWhenMaxPagesIsZero() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("https://example.com");

        when(solrConfigurationService
                .isExternalIndexingTarget())
                .thenReturn(true);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(0);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(5000);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "External crawl maximum pages must be greater than zero",
                exception.getMessage());
    }

    @Test
    void testCrawlFailsWhenMaxPagesIsNegative() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("https://example.com");

        when(solrConfigurationService
                .isExternalIndexingTarget())
                .thenReturn(true);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(-1);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(5000);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "External crawl maximum pages must be greater than zero",
                exception.getMessage());
    }

    @Test
    void testCrawlFailsWhenConnectionTimeoutIsZero() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("https://example.com");

        when(solrConfigurationService
                .isExternalIndexingTarget())
                .thenReturn(true);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(10);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(0);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "External crawl connection timeout must be greater than zero",
                exception.getMessage());
    }

    @Test
    void testCrawlFailsWhenConnectionTimeoutIsNegative() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("https://example.com");

        when(solrConfigurationService
                .isExternalIndexingTarget())
                .thenReturn(true);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(10);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(-100);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "External crawl connection timeout must be greater than zero",
                exception.getMessage());
    }

    @Test
    void testCrawlFailsWhenTargetCannotBeNormalized() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("ftp://example.com");

        when(solrConfigurationService
                .isExternalIndexingTarget())
                .thenReturn(true);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(10);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(5000);

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> crawlerService.crawl());

        assertEquals(
                "Unable to normalize external crawl target: ftp://example.com",
                exception.getMessage());
    }

    // ---------------------------------------------------------------------
    // Successful end-to-end crawl
    // ---------------------------------------------------------------------

    @Test
    void testCrawlSuccessfullyCrawlsPage() throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0);

        try {

            server.createContext(
                    "/",
                    exchange -> {

                        String html = "<html>"
                                + "<head>"
                                + "<title>Home Page</title>"
                                + "</head>"
                                + "<body>"
                                + "<main>"
                                + "<h1>Welcome</h1>"
                                + "<p>This is the home page.</p>"
                                + "</main>"
                                + "</body>"
                                + "</html>";

                        sendHtmlResponse(
                                exchange,
                                html);
                    });

            server.start();

            int port = server.getAddress().getPort();

            String url = "http://localhost:" + port + "/";

            configureCrawler(
                    url,
                    10,
                    5000);

            List<CrawledPage> pages = crawlerService.crawl();

            assertNotNull(pages);

            assertEquals(
                    1,
                    pages.size());

            CrawledPage page = pages.get(0);

            assertEquals(
                    "Home Page",
                    page.getTitle());

            assertTrue(
                    page.getContent()
                            .contains(
                                    "This is the home page."));

        } finally {

            server.stop(0);
        }
    }

    @Test
    void testCrawlDiscoversInternalLinks() throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0);

        try {

            server.createContext(
                    "/",
                    exchange -> {

                        String html = "<html>"
                                + "<head>"
                                + "<title>Home</title>"
                                + "</head>"
                                + "<body>"
                                + "<main>"
                                + "<p>Home content.</p>"
                                + "<a href=\"/products\">"
                                + "Products"
                                + "</a>"
                                + "</main>"
                                + "</body>"
                                + "</html>";

                        sendHtmlResponse(
                                exchange,
                                html);
                    });

            server.createContext(
                    "/products",
                    exchange -> {

                        String html = "<html>"
                                + "<head>"
                                + "<title>Products</title>"
                                + "</head>"
                                + "<body>"
                                + "<main>"
                                + "<p>Product content.</p>"
                                + "</main>"
                                + "</body>"
                                + "</html>";

                        sendHtmlResponse(
                                exchange,
                                html);
                    });

            server.start();

            int port = server.getAddress().getPort();

            String url = "http://localhost:" + port + "/";

            configureCrawler(
                    url,
                    10,
                    5000);

            List<CrawledPage> pages = crawlerService.crawl();

            assertEquals(
                    2,
                    pages.size());

        } finally {

            server.stop(0);
        }
    }

    @Test
    void testCrawlHonorsMaximumPages() throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0);

        try {

            server.createContext(
                    "/",
                    exchange -> {

                        String html = "<html>"
                                + "<head>"
                                + "<title>Home</title>"
                                + "</head>"
                                + "<body>"
                                + "<main>"
                                + "<p>Home content.</p>"
                                + "<a href=\"/one\">One</a>"
                                + "<a href=\"/two\">Two</a>"
                                + "</main>"
                                + "</body>"
                                + "</html>";

                        sendHtmlResponse(
                                exchange,
                                html);
                    });

            server.createContext(
                    "/one",
                    exchange -> {

                        String html = "<html>"
                                + "<title>One</title>"
                                + "<body>"
                                + "<main>One content</main>"
                                + "</body>"
                                + "</html>";

                        sendHtmlResponse(
                                exchange,
                                html);
                    });

            server.createContext(
                    "/two",
                    exchange -> {

                        String html = "<html>"
                                + "<title>Two</title>"
                                + "<body>"
                                + "<main>Two content</main>"
                                + "</body>"
                                + "</html>";

                        sendHtmlResponse(
                                exchange,
                                html);
                    });

            server.start();

            int port = server.getAddress().getPort();

            String url = "http://localhost:" + port + "/";

            configureCrawler(
                    url,
                    1,
                    5000);

            List<CrawledPage> pages = crawlerService.crawl();

            assertEquals(
                    1,
                    pages.size());

        } finally {

            server.stop(0);
        }
    }

    // ---------------------------------------------------------------------
    // safeValue
    // ---------------------------------------------------------------------

    @Test
    void testSafeValueReturnsOriginalValue()
            throws Exception {

        String result = invokePrivate(
                "safeValue",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "Products",
                "Untitled");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testSafeValueTrimsValue()
            throws Exception {

        String result = invokePrivate(
                "safeValue",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "  Products  ",
                "Untitled");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testSafeValueUsesDefaultForNull()
            throws Exception {

        String result = invokePrivate(
                "safeValue",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                new Object[] {
                        null,
                        "Untitled"
                });

        assertEquals(
                "Untitled",
                result);
    }

    @Test
    void testSafeValueUsesDefaultForBlank()
            throws Exception {

        String result = invokePrivate(
                "safeValue",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "   ",
                "Untitled");

        assertEquals(
                "Untitled",
                result);
    }

    // ---------------------------------------------------------------------
    // Title extraction
    // ---------------------------------------------------------------------

    @Test
    void testExtractTitle() throws Exception {

        String result = invokePrivate(
                "extractTitle",
                new Class<?>[] {
                        String.class
                },
                "<title>Products</title>");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testExtractTitleWithAttributes()
            throws Exception {

        String result = invokePrivate(
                "extractTitle",
                new Class<?>[] {
                        String.class
                },
                "<title class=\"page-title\">"
                        + "Products"
                        + "</title>");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testExtractTitleWithHtmlEntities()
            throws Exception {

        String result = invokePrivate(
                "extractTitle",
                new Class<?>[] {
                        String.class
                },
                "<title>Products &amp; AEM</title>");

        assertEquals(
                "Products & AEM",
                result);
    }

    @Test
    void testExtractTitleWhenMissing()
            throws Exception {

        String result = invokePrivate(
                "extractTitle",
                new Class<?>[] {
                        String.class
                },
                "<html><body>Hello</body></html>");

        assertEquals(
                "",
                result);
    }

    @Test
    void testExtractTitleWithNull()
            throws Exception {

        String result = invokePrivate(
                "extractTitle",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // H1 extraction
    // ---------------------------------------------------------------------

    @Test
    void testExtractFirstHeading()
            throws Exception {

        String result = invokePrivate(
                "extractFirstHeading",
                new Class<?>[] {
                        String.class
                },
                "<h1>Products</h1>"
                        + "<h1>Second</h1>");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testExtractFirstHeadingWithAttributes()
            throws Exception {

        String result = invokePrivate(
                "extractFirstHeading",
                new Class<?>[] {
                        String.class
                },
                "<h1 class=\"hero\">Products</h1>");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testExtractFirstHeadingWhenMissing()
            throws Exception {

        String result = invokePrivate(
                "extractFirstHeading",
                new Class<?>[] {
                        String.class
                },
                "<p>Products</p>");

        assertEquals(
                "",
                result);
    }

    @Test
    void testExtractFirstHeadingWithNull()
            throws Exception {

        String result = invokePrivate(
                "extractFirstHeading",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // Content extraction
    // ---------------------------------------------------------------------

    @Test
    void testExtractContentPrefersMain()
            throws Exception {

        String html = "<body>"
                + "<p>Body content</p>"
                + "<main>"
                + "<p>Main content</p>"
                + "</main>"
                + "</body>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Main content",
                result);
    }

    @Test
    void testExtractContentFallsBackToBody()
            throws Exception {

        String html = "<body>"
                + "<p>Body content</p>"
                + "</body>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Body content",
                result);
    }

    @Test
    void testExtractContentWithoutMainOrBody()
            throws Exception {

        String html = "<p>Standalone content</p>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Standalone content",
                result);
    }

    @Test
    void testExtractContentRemovesScripts()
            throws Exception {

        String html = "<main>"
                + "<p>Hello</p>"
                + "<script>bad content</script>"
                + "<p>World</p>"
                + "</main>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Hello World",
                result);
    }

    @Test
    void testExtractContentRemovesStyles()
            throws Exception {

        String html = "<main>"
                + "<style>.class { color: red; }</style>"
                + "<p>Hello</p>"
                + "</main>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Hello",
                result);
    }

    @Test
    void testExtractContentRemovesNavigation()
            throws Exception {

        String html = "<main>"
                + "<nav>Navigation</nav>"
                + "<p>Actual content</p>"
                + "</main>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Actual content",
                result);
    }

    @Test
    void testExtractContentRemovesHeaderAndFooter()
            throws Exception {

        String html = "<body>"
                + "<header>Header</header>"
                + "<p>Actual content</p>"
                + "<footer>Footer</footer>"
                + "</body>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Actual content",
                result);
    }

    @Test
    void testExtractContentRemovesMultipleUnwantedElements()
            throws Exception {

        String html = "<main>"
                + "<script>script</script>"
                + "<style>style</style>"
                + "<noscript>noscript</noscript>"
                + "<template>template</template>"
                + "<svg>svg</svg>"
                + "<canvas>canvas</canvas>"
                + "<iframe>iframe</iframe>"
                + "<aside>aside</aside>"
                + "<form>form</form>"
                + "<p>Real content</p>"
                + "</main>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Real content",
                result);
    }

    @Test
    void testExtractContentPreservesSpacesBetweenBlocks()
            throws Exception {

        String html = "<main>"
                + "<p>Hello</p>"
                + "<div>World</div>"
                + "<section>AEM</section>"
                + "</main>";

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                html);

        assertEquals(
                "Hello World AEM",
                result);
    }

    @Test
    void testExtractContentWithNull()
            throws Exception {

        String result = invokePrivate(
                "extractContent",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // removeUnwantedElements
    // ---------------------------------------------------------------------

    @Test
    void testRemoveUnwantedElements()
            throws Exception {

        String html = "<header>Header</header>"
                + "<nav>Nav</nav>"
                + "<main>Main</main>"
                + "<footer>Footer</footer>";

        String result = invokePrivate(
                "removeUnwantedElements",
                new Class<?>[] {
                        String.class
                },
                html);

        assertTrue(
                result.contains(
                        "<main>Main</main>"));

        assertFalse(
                result.contains(
                        "Header"));

        assertFalse(
                result.contains(
                        "Nav"));

        assertFalse(
                result.contains(
                        "Footer"));
    }

    // ---------------------------------------------------------------------
    // Link extraction
    // ---------------------------------------------------------------------

    @Test
    void testExtractLinks()
            throws Exception {

        String html = "<a href=\"/products\">Products</a>"
                + "<a href=\"/services\">Services</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertEquals(
                2,
                links.size());

        assertTrue(
                links.contains(
                        "https://example.com/products"));

        assertTrue(
                links.contains(
                        "https://example.com/services"));
    }

    @Test
    void testExtractLinksResolvesRelativeUrl()
            throws Exception {

        String html = "<a href=\"products\">Products</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com/catalog/");

        assertTrue(
                links.contains(
                        "https://example.com/catalog/products"));
    }

    @Test
    void testExtractLinksRemovesTrackingParameters()
            throws Exception {

        String html = "<a href=\"/products?utm_source=google&id=10\">"
                + "Products"
                + "</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.contains(
                        "https://example.com/products?id=10"));
    }

    @Test
    void testExtractLinksIgnoresFragment()
            throws Exception {

        String html = "<a href=\"#section\">Section</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.isEmpty());
    }

    @Test
    void testExtractLinksIgnoresJavascript()
            throws Exception {

        String html = "<a href=\"javascript:void(0)\">Click</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.isEmpty());
    }

    @Test
    void testExtractLinksIgnoresMailto()
            throws Exception {

        String html = "<a href=\"mailto:test@example.com\">Email</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.isEmpty());
    }

    @Test
    void testExtractLinksIgnoresTel()
            throws Exception {

        String html = "<a href=\"tel:123456789\">Call</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.isEmpty());
    }

    @Test
    void testExtractLinksIgnoresDataUrl()
            throws Exception {

        String html = "<a href=\"data:text/plain,hello\">Data</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.isEmpty());
    }

    @Test
    void testExtractLinksIgnoresNullHtml()
            throws Exception {

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                null,
                "https://example.com");

        assertTrue(
                links.isEmpty());
    }

    @Test
    void testExtractLinksSupportsSingleQuotedHref()
            throws Exception {

        String html = "<a href='/products'>Products</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.contains(
                        "https://example.com/products"));
    }

    @Test
    void testExtractLinksSupportsUnquotedHref()
            throws Exception {

        String html = "<a href=/products>Products</a>";

        @SuppressWarnings("unchecked")
        Set<String> links = invokePrivate(
                "extractLinks",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertTrue(
                links.contains(
                        "https://example.com/products"));
    }

    // ---------------------------------------------------------------------
    // Canonical URL
    // ---------------------------------------------------------------------

    @Test
    void testExtractCanonicalUrl()
            throws Exception {

        String html = "<head>"
                + "<link rel=\"canonical\" "
                + "href=\"https://example.com/products\">"
                + "</head>";

        String result = invokePrivate(
                "extractCanonicalUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testExtractCanonicalUrlWithMultipleRelTokens()
            throws Exception {

        String html = "<link rel=\"alternate canonical\" "
                + "href=\"/products\">";

        String result = invokePrivate(
                "extractCanonicalUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testExtractCanonicalUrlIsCaseInsensitive()
            throws Exception {

        String html = "<link rel=\"ALTERNATE CANONICAL\" "
                + "href=\"/products\">";

        String result = invokePrivate(
                "extractCanonicalUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testExtractCanonicalUrlIgnoresNonCanonicalLink()
            throws Exception {

        String html = "<link rel=\"stylesheet\" "
                + "href=\"/style.css\">";

        String result = invokePrivate(
                "extractCanonicalUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                html,
                "https://example.com");

        assertNull(result);
    }

    @Test
    void testExtractCanonicalUrlWithNull()
            throws Exception {

        String result = invokePrivate(
                "extractCanonicalUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                null,
                "https://example.com");

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // Attribute extraction
    // ---------------------------------------------------------------------

    @Test
    void testExtractAttributeValueWithDoubleQuotes()
            throws Exception {

        String tag = "<a href=\"https://example.com/products\">";

        String result = invokePrivate(
                "extractAttributeValue",
                new Class<?>[] {
                        String.class,
                        java.util.regex.Pattern.class
                },
                tag,
                getHrefPattern());

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testExtractAttributeValueWithSingleQuotes()
            throws Exception {

        String tag = "<a href='https://example.com/products'>";

        String result = invokePrivate(
                "extractAttributeValue",
                new Class<?>[] {
                        String.class,
                        java.util.regex.Pattern.class
                },
                tag,
                getHrefPattern());

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testExtractAttributeValueWithoutQuotes()
            throws Exception {

        String tag = "<a href=https://example.com/products>";

        String result = invokePrivate(
                "extractAttributeValue",
                new Class<?>[] {
                        String.class,
                        java.util.regex.Pattern.class
                },
                tag,
                getHrefPattern());

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testExtractAttributeValueDecodesHtmlEntities()
            throws Exception {

        String tag = "<a href=\"/products?a=1&amp;b=2\">";

        String result = invokePrivate(
                "extractAttributeValue",
                new Class<?>[] {
                        String.class,
                        java.util.regex.Pattern.class
                },
                tag,
                getHrefPattern());

        assertEquals(
                "/products?a=1&b=2",
                result);
    }

    @Test
    void testExtractAttributeValueWhenMissing()
            throws Exception {

        String tag = "<a class=\"link\">";

        String result = invokePrivate(
                "extractAttributeValue",
                new Class<?>[] {
                        String.class,
                        java.util.regex.Pattern.class
                },
                tag,
                getHrefPattern());

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // Token matching
    // ---------------------------------------------------------------------

    @Test
    void testContainsTokenIgnoreCase()
            throws Exception {

        Boolean result = invokePrivate(
                "containsTokenIgnoreCase",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "alternate CANONICAL",
                "canonical");

        assertTrue(result);
    }

    @Test
    void testContainsTokenIgnoreCaseReturnsFalse()
            throws Exception {

        Boolean result = invokePrivate(
                "containsTokenIgnoreCase",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "alternate stylesheet",
                "canonical");

        assertFalse(result);
    }

    @Test
    void testContainsTokenIgnoreCaseWithNullValue()
            throws Exception {

        Boolean result = invokePrivate(
                "containsTokenIgnoreCase",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                null,
                "canonical");

        assertFalse(result);
    }

    // ---------------------------------------------------------------------
    // Ignored href validation
    // ---------------------------------------------------------------------

    @Test
    void testIsIgnoredHrefForFragment()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                "#section");

        assertTrue(result);
    }

    @Test
    void testIsIgnoredHrefForJavascript()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                "javascript:void(0)");

        assertTrue(result);
    }

    @Test
    void testIsIgnoredHrefForMailto()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                "mailto:test@example.com");

        assertTrue(result);
    }

    @Test
    void testIsIgnoredHrefForTel()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                "tel:123456789");

        assertTrue(result);
    }

    @Test
    void testIsIgnoredHrefForSms()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                "sms:123456789");

        assertTrue(result);
    }

    @Test
    void testIsIgnoredHrefForData()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                "data:text/plain,hello");

        assertTrue(result);
    }

    @Test
    void testIsIgnoredHrefForNormalUrl()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                "https://example.com");

        assertFalse(result);
    }

    @Test
    void testIsIgnoredHrefWithNull()
            throws Exception {

        Boolean result = invokePrivate(
                "isIgnoredHref",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertTrue(result);
    }

    // ---------------------------------------------------------------------
    // URL resolution
    // ---------------------------------------------------------------------

    @Test
    void testResolveUrl()
            throws Exception {

        String result = invokePrivate(
                "resolveUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://example.com/products/",
                "details");

        assertEquals(
                "https://example.com/products/details",
                result);
    }

    @Test
    void testResolveUrlWithAbsoluteUrl()
            throws Exception {

        String result = invokePrivate(
                "resolveUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://example.com",
                "https://other.com/page");

        assertEquals(
                "https://other.com/page",
                result);
    }

    @Test
    void testResolveUrlWithInvalidHref()
            throws Exception {

        String result = invokePrivate(
                "resolveUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://example.com",
                "http://[invalid");

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // Plain HTML URL
    // ---------------------------------------------------------------------

    @Test
    void testBuildPlainHtmlUrl()
            throws Exception {

        String result = invokePrivate(
                "buildPlainHtmlUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products");

        assertEquals(
                "https://example.com/products.plain.html",
                result);
    }

    @Test
    void testBuildPlainHtmlUrlWithTrailingSlash()
            throws Exception {

        String result = invokePrivate(
                "buildPlainHtmlUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products/");

        assertEquals(
                "https://example.com/products.plain.html",
                result);
    }

    @Test
    void testBuildPlainHtmlUrlWithQueryParameters()
            throws Exception {

        String result = invokePrivate(
                "buildPlainHtmlUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products"
                        + "?utm_source=test&id=10");

        assertEquals(
                "https://example.com/products.plain.html",
                result);
    }

    @Test
    void testBuildPlainHtmlUrlAlreadyPlainHtml()
            throws Exception {

        String url = "https://example.com/products.plain.html";

        String result = invokePrivate(
                "buildPlainHtmlUrl",
                new Class<?>[] {
                        String.class
                },
                url);

        assertEquals(
                url,
                result);
    }

    @Test
    void testBuildPlainHtmlUrlWithInvalidUrl()
            throws Exception {

        String result = invokePrivate(
                "buildPlainHtmlUrl",
                new Class<?>[] {
                        String.class
                },
                "http://[invalid");

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // URL normalization
    // ---------------------------------------------------------------------

    @Test
    void testNormalizeUrl()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "HTTPS://Example.COM/products/");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testNormalizeUrlRemovesDefaultHttpPort()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "http://example.com:80/products");

        assertEquals(
                "http://example.com/products",
                result);
    }

    @Test
    void testNormalizeUrlRemovesDefaultHttpsPort()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com:443/products");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testNormalizeUrlKeepsNonDefaultPort()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com:8443/products");

        assertEquals(
                "https://example.com:8443/products",
                result);
    }

    @Test
    void testNormalizeUrlRemovesTrackingParameters()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products"
                        + "?utm_source=google"
                        + "&id=10"
                        + "&gclid=abc");

        assertEquals(
                "https://example.com/products?id=10",
                result);
    }

    @Test
    void testNormalizeUrlRemovesAllTrackingParameters()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products"
                        + "?utm_source=google"
                        + "&gclid=abc"
                        + "&fbclid=def"
                        + "&msclkid=ghi"
                        + "&mc_cid=jkl"
                        + "&mc_eid=mno"
                        + "&_ga=pqr"
                        + "&_gl=stu");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testNormalizeUrlKeepsNormalQueryParameters()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products"
                        + "?category=aem"
                        + "&page=2");

        assertEquals(
                "https://example.com/products"
                        + "?category=aem"
                        + "&page=2",
                result);
    }

    @Test
    void testNormalizeUrlRootPath()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/");

        assertEquals(
                "https://example.com/",
                result);
    }

    @Test
    void testNormalizeUrlRemovesRepeatedTrailingSlashes()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products///");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testNormalizeUrlRemovesFragment()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products#section");

        assertEquals(
                "https://example.com/products",
                result);
    }

    @Test
    void testNormalizeUrlRejectsUnsupportedScheme()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "ftp://example.com/file");

        assertNull(result);
    }

    @Test
    void testNormalizeUrlRejectsUserInfo()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "https://user:password@example.com/page");

        assertNull(result);
    }

    @Test
    void testNormalizeUrlRejectsInvalidUrl()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "http://[invalid");

        assertNull(result);
    }

    @Test
    void testNormalizeUrlWithNull()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertNull(result);
    }

    @Test
    void testNormalizeUrlWithBlank()
            throws Exception {

        String result = invokePrivate(
                "normalizeUrl",
                new Class<?>[] {
                        String.class
                },
                "   ");

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // Tracking parameter validation
    // ---------------------------------------------------------------------

    @Test
    void testIsTrackingParameterForUtm()
            throws Exception {

        Boolean result = invokePrivate(
                "isTrackingParameter",
                new Class<?>[] {
                        String.class
                },
                "utm_source");

        assertTrue(result);
    }

    @Test
    void testIsTrackingParameterForUtmCaseInsensitive()
            throws Exception {

        Boolean result = invokePrivate(
                "isTrackingParameter",
                new Class<?>[] {
                        String.class
                },
                "UTM_SOURCE");

        assertTrue(result);
    }

    @Test
    void testIsTrackingParameterForGclid()
            throws Exception {

        Boolean result = invokePrivate(
                "isTrackingParameter",
                new Class<?>[] {
                        String.class
                },
                "gclid");

        assertTrue(result);
    }

    @Test
    void testIsTrackingParameterForFbclid()
            throws Exception {

        Boolean result = invokePrivate(
                "isTrackingParameter",
                new Class<?>[] {
                        String.class
                },
                "fbclid");

        assertTrue(result);
    }

    @Test
    void testIsTrackingParameterForNormalParameter()
            throws Exception {

        Boolean result = invokePrivate(
                "isTrackingParameter",
                new Class<?>[] {
                        String.class
                },
                "page");

        assertFalse(result);
    }

    @Test
    void testIsTrackingParameterWithNull()
            throws Exception {

        Boolean result = invokePrivate(
                "isTrackingParameter",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertFalse(result);
    }

    // ---------------------------------------------------------------------
    // Allowed URL validation
    // ---------------------------------------------------------------------

    @Test
    void testIsAllowedUrlWithSameHost()
            throws Exception {

        Boolean result = invokePrivate(
                "isAllowedUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://example.com/products",
                "example.com");

        assertTrue(result);
    }

    @Test
    void testIsAllowedUrlIsCaseInsensitive()
            throws Exception {

        Boolean result = invokePrivate(
                "isAllowedUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://EXAMPLE.COM/products",
                "example.com");

        assertTrue(result);
    }

    @Test
    void testIsAllowedUrlRejectsDifferentHost()
            throws Exception {

        Boolean result = invokePrivate(
                "isAllowedUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://other.com/products",
                "example.com");

        assertFalse(result);
    }

    @Test
    void testIsAllowedUrlRejectsSubdomain()
            throws Exception {

        Boolean result = invokePrivate(
                "isAllowedUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://www.example.com/products",
                "example.com");

        assertFalse(result);
    }

    @Test
    void testIsAllowedUrlRejectsFtp()
            throws Exception {

        Boolean result = invokePrivate(
                "isAllowedUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "ftp://example.com/file",
                "example.com");

        assertFalse(result);
    }

    @Test
    void testIsAllowedUrlWithNullUrl()
            throws Exception {

        Boolean result = invokePrivate(
                "isAllowedUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                null,
                "example.com");

        assertFalse(result);
    }

    @Test
    void testIsAllowedUrlWithNullHost()
            throws Exception {

        Boolean result = invokePrivate(
                "isAllowedUrl",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "https://example.com",
                null);

        assertFalse(result);
    }

    // ---------------------------------------------------------------------
    // HTML entity decoding
    // ---------------------------------------------------------------------

    @Test
    void testDecodeHtmlEntities()
            throws Exception {

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                "&amp; &lt; &gt; &quot; &#39;");

        assertEquals(
                "& < > \" '",
                result);
    }

    @Test
    void testDecodeHtmlEntitiesWithNamedEntities()
            throws Exception {

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                "&nbsp;&mdash;&ndash;&hellip;"
                        + "&copy;&reg;&trade;");

        assertEquals(
                " —–…©®™",
                result);
    }

    @Test
    void testDecodeHtmlEntitiesWithDecimalEntity()
            throws Exception {

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                "&#65;");

        assertEquals(
                "A",
                result);
    }

    @Test
    void testDecodeHtmlEntitiesWithHexEntity()
            throws Exception {

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                "&#x41;");

        assertEquals(
                "A",
                result);
    }

    @Test
    void testDecodeHtmlEntitiesWithUppercaseHexEntity()
            throws Exception {

        String value = "&#X41;";

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                value);

        assertEquals(
                value,
                result);
    }

    @Test
    void testDecodeHtmlEntitiesWithUnicodeEntity()
            throws Exception {

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                "&#x1F600;");

        assertEquals(
                "😀",
                result);
    }

    @Test
    void testDecodeHtmlEntitiesKeepsInvalidNumericEntity()
            throws Exception {

        String value = "&#x110000;";

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                value);

        assertEquals(
                value,
                result);
    }

    @Test
    void testDecodeHtmlEntitiesWithNull()
            throws Exception {

        String result = invokePrivate(
                "decodeHtmlEntities",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // cleanText
    // ---------------------------------------------------------------------

    @Test
    void testCleanTextNormalizesWhitespace()
            throws Exception {

        String result = invokePrivate(
                "cleanText",
                new Class<?>[] {
                        String.class
                },
                "  Hello   World  ");

        assertEquals(
                "Hello World",
                result);
    }

    @Test
    void testCleanTextRemovesZeroWidthCharacters()
            throws Exception {

        String result = invokePrivate(
                "cleanText",
                new Class<?>[] {
                        String.class
                },
                "Hello\u200BWorld\u200C\u200D");

        assertEquals(
                "HelloWorld",
                result);
    }

    @Test
    void testCleanTextReplacesNonBreakingSpace()
            throws Exception {

        String result = invokePrivate(
                "cleanText",
                new Class<?>[] {
                        String.class
                },
                "Hello\u00A0World");

        assertEquals(
                "Hello World",
                result);
    }

    @Test
    void testCleanTextDecodesEntities()
            throws Exception {

        String result = invokePrivate(
                "cleanText",
                new Class<?>[] {
                        String.class
                },
                "Hello &amp; World");

        assertEquals(
                "Hello & World",
                result);
    }

    @Test
    void testCleanTextWithNull()
            throws Exception {

        String result = invokePrivate(
                "cleanText",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // Charset handling
    // ---------------------------------------------------------------------

    @Test
    void testCharsetFromName()
            throws Exception {

        Charset result = invokePrivate(
                "charsetFromName",
                new Class<?>[] {
                        String.class
                },
                "UTF-8");

        assertEquals(
                StandardCharsets.UTF_8,
                result);
    }

    @Test
    void testCharsetFromNameIsCaseInsensitive()
            throws Exception {

        Charset result = invokePrivate(
                "charsetFromName",
                new Class<?>[] {
                        String.class
                },
                "utf-8");

        assertEquals(
                StandardCharsets.UTF_8,
                result);
    }

    @Test
    void testCharsetFromNameWithInvalidCharset()
            throws Exception {

        Charset result = invokePrivate(
                "charsetFromName",
                new Class<?>[] {
                        String.class
                },
                "INVALID-CHARSET");

        assertNull(result);
    }

    @Test
    void testCharsetFromNameWithNull()
            throws Exception {

        Charset result = invokePrivate(
                "charsetFromName",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // determineCharset
    // ---------------------------------------------------------------------

    @Test
    void testDetermineCharsetUsesHttpCharset()
            throws Exception {

        ContentType contentType = ContentType.create(
                "text/html",
                StandardCharsets.ISO_8859_1);

        Charset result = invokePrivate(
                "determineCharset",
                new Class<?>[] {
                        ContentType.class,
                        byte[].class
                },
                contentType,
                "Hello".getBytes(
                        StandardCharsets.ISO_8859_1));

        assertEquals(
                StandardCharsets.ISO_8859_1,
                result);
    }

    @Test
    void testDetermineCharsetUsesHtmlMetaCharset()
            throws Exception {

        String html = "<meta charset=\"ISO-8859-1\">"
                + "<p>Hello</p>";

        Charset result = invokePrivate(
                "determineCharset",
                new Class<?>[] {
                        ContentType.class,
                        byte[].class
                },
                ContentType.create("text/html"),
                html.getBytes(
                        StandardCharsets.ISO_8859_1));

        assertEquals(
                StandardCharsets.ISO_8859_1,
                result);
    }

    @Test
    void testDetermineCharsetUsesContentTypeMeta()
            throws Exception {

        String html = "<meta content=\"text/html; charset=ISO-8859-1\">"
                + "<p>Hello</p>";

        Charset result = invokePrivate(
                "determineCharset",
                new Class<?>[] {
                        ContentType.class,
                        byte[].class
                },
                ContentType.create("text/html"),
                html.getBytes(
                        StandardCharsets.ISO_8859_1));

        assertEquals(
                StandardCharsets.ISO_8859_1,
                result);
    }

    @Test
    void testDetermineCharsetDefaultsToUtf8()
            throws Exception {

        byte[] bytes = "<p>Hello</p>"
                .getBytes(
                        StandardCharsets.UTF_8);

        Charset result = invokePrivate(
                "determineCharset",
                new Class<?>[] {
                        ContentType.class,
                        byte[].class
                },
                ContentType.create("text/html"),
                bytes);

        assertEquals(
                StandardCharsets.UTF_8,
                result);
    }

    // ---------------------------------------------------------------------
    // HTTP response reading
    // ---------------------------------------------------------------------

    @Test
    void testFetchHtmlSuccessfulResponse()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        HttpEntity entity = mock(HttpEntity.class);

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_OK,
                                "OK"));

        when(response.getEntity())
                .thenReturn(entity);

        when(entity.getContentType())
                .thenReturn(
                        new BasicHeader(
                                "Content-Type",
                                "text/html; charset=UTF-8"));

        when(entity.getContent())
                .thenReturn(
                        new ByteArrayInputStream(
                                "<html><body>Hello</body></html>"
                                        .getBytes(
                                                StandardCharsets.UTF_8)));

        String result = invokePrivate(
                "fetchHtml",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class,
                        boolean.class
                },
                httpClient,
                "https://example.com",
                false);

        assertEquals(
                "<html><body>Hello</body></html>",
                result);
    }

    @Test
    void testFetchHtmlAcceptsXhtml()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        HttpEntity entity = mock(HttpEntity.class);

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_OK,
                                "OK"));

        when(response.getEntity())
                .thenReturn(entity);

        when(entity.getContentType())
                .thenReturn(
                        new BasicHeader(
                                "Content-Type",
                                "application/xhtml+xml"));

        when(entity.getContent())
                .thenReturn(
                        new ByteArrayInputStream(
                                "<html><body>Hello</body></html>"
                                        .getBytes(
                                                StandardCharsets.UTF_8)));

        String result = invokePrivate(
                "fetchHtml",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class,
                        boolean.class
                },
                httpClient,
                "https://example.com",
                false);

        assertNotNull(result);

        assertTrue(
                result.contains("Hello"));
    }

    @Test
    void testFetchHtmlReturnsNullFor404()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_NOT_FOUND,
                                "Not Found"));

        String result = invokePrivate(
                "fetchHtml",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class,
                        boolean.class
                },
                httpClient,
                "https://example.com/missing",
                false);

        assertNull(result);
    }

    @Test
    void testFetchHtmlReturnsNullForNonHtml()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        HttpEntity entity = mock(HttpEntity.class);

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_OK,
                                "OK"));

        when(response.getEntity())
                .thenReturn(entity);

        when(entity.getContentType())
                .thenReturn(
                        new BasicHeader(
                                "Content-Type",
                                "application/pdf"));

        String result = invokePrivate(
                "fetchHtml",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class,
                        boolean.class
                },
                httpClient,
                "https://example.com/file.pdf",
                false);

        assertNull(result);
    }

    @Test
    void testFetchHtmlReturnsNullWhenEntityMissing()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_OK,
                                "OK"));

        when(response.getEntity())
                .thenReturn(null);

        String result = invokePrivate(
                "fetchHtml",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class,
                        boolean.class
                },
                httpClient,
                "https://example.com",
                false);

        assertNull(result);
    }

    @Test
    void testFetchHtmlRetriesServerError()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse firstResponse = mock(CloseableHttpResponse.class);

        CloseableHttpResponse secondResponse = mock(CloseableHttpResponse.class);

        HttpEntity secondEntity = mock(HttpEntity.class);

        when(httpClient.execute(any()))
                .thenReturn(
                        firstResponse,
                        secondResponse);

        when(firstResponse.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                "Server Error"));

        when(secondResponse.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_OK,
                                "OK"));

        when(secondResponse.getEntity())
                .thenReturn(secondEntity);

        when(secondEntity.getContentType())
                .thenReturn(
                        new BasicHeader(
                                "Content-Type",
                                "text/html; charset=UTF-8"));

        when(secondEntity.getContent())
                .thenReturn(
                        new ByteArrayInputStream(
                                "<html>Hello</html>"
                                        .getBytes(
                                                StandardCharsets.UTF_8)));

        String result = invokePrivate(
                "fetchHtml",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class,
                        boolean.class
                },
                httpClient,
                "https://example.com",
                false);

        assertEquals(
                "<html>Hello</html>",
                result);
    }

    @Test
    void testFetchHtmlReturnsNullAfterTwoServerErrors()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                "Server Error"));

        String result = invokePrivate(
                "fetchHtml",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class,
                        boolean.class
                },
                httpClient,
                "https://example.com",
                false);

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // readEntityWithLimit
    // ---------------------------------------------------------------------

    @Test
    void testReadEntityWithLimitReadsContent()
            throws Exception {

        HttpEntity entity = mock(HttpEntity.class);

        byte[] data = "Hello World"
                .getBytes(
                        StandardCharsets.UTF_8);

        when(entity.getContent())
                .thenReturn(
                        new ByteArrayInputStream(data));

        byte[] result = invokePrivate(
                "readEntityWithLimit",
                new Class<?>[] {
                        HttpEntity.class,
                        int.class
                },
                entity,
                1024);

        assertArrayEquals(
                data,
                result);
    }

    @Test
    void testReadEntityWithLimitReturnsEmptyWhenTooLarge()
            throws Exception {

        HttpEntity entity = mock(HttpEntity.class);

        byte[] data = new byte[20];

        when(entity.getContent())
                .thenReturn(
                        new ByteArrayInputStream(data));

        byte[] result = invokePrivate(
                "readEntityWithLimit",
                new Class<?>[] {
                        HttpEntity.class,
                        int.class
                },
                entity,
                10);

        assertEquals(
                0,
                result.length);
    }

    // ---------------------------------------------------------------------
    // createUri
    // ---------------------------------------------------------------------

    @Test
    void testCreateUri()
            throws Exception {

        java.net.URI result = invokePrivate(
                "createUri",
                new Class<?>[] {
                        String.class
                },
                "https://example.com/products");

        assertEquals(
                "https://example.com/products",
                result.toString());
    }

    @Test
    void testCreateUriWithInvalidUri() {

        WebsiteCrawlerException exception = assertThrows(
                WebsiteCrawlerException.class,
                () -> invokePrivate(
                        "createUri",
                        new Class<?>[] {
                                String.class
                        },
                        "http://[invalid"));

        assertEquals(
                "Invalid external crawl target: http://[invalid",
                exception.getMessage());
    }

    // ---------------------------------------------------------------------
    // fetchPage
    // ---------------------------------------------------------------------

    @Test
    void testFetchPageReturnsNullForEmptyHtml()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        HttpEntity entity = mock(HttpEntity.class);

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_OK,
                                "OK"));

        when(response.getEntity())
                .thenReturn(entity);

        when(entity.getContentType())
                .thenReturn(
                        new BasicHeader(
                                "Content-Type",
                                "text/html; charset=UTF-8"));

        when(entity.getContent())
                .thenReturn(
                        new ByteArrayInputStream(
                                "   "
                                        .getBytes(
                                                StandardCharsets.UTF_8)));

        Object result = invokePrivate(
                "fetchPage",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class
                },
                httpClient,
                "https://example.com");

        assertNull(result);
    }

    @Test
    void testFetchPageUsesH1WhenTitleIsMissing()
            throws Exception {

        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);

        HttpEntity entity = mock(HttpEntity.class);

        String html = "<html>"
                + "<body>"
                + "<main>"
                + "<h1>Products</h1>"
                + "<p>"
                + "This page contains enough content to avoid "
                + "the plain HTML fallback and verify title extraction."
                + "</p>"
                + "</main>"
                + "</body>"
                + "</html>";

        when(httpClient.execute(any()))
                .thenReturn(response);

        when(response.getStatusLine())
                .thenReturn(
                        new BasicStatusLine(
                                new ProtocolVersion(
                                        "HTTP",
                                        1,
                                        1),
                                HttpStatus.SC_OK,
                                "OK"));

        when(response.getEntity())
                .thenReturn(entity);

        when(entity.getContentType())
                .thenReturn(
                        new BasicHeader(
                                "Content-Type",
                                "text/html; charset=UTF-8"));

        when(entity.getContent())
                .thenReturn(
                        new ByteArrayInputStream(
                                html.getBytes(
                                        StandardCharsets.UTF_8)));

        Object result = invokePrivate(
                "fetchPage",
                new Class<?>[] {
                        CloseableHttpClient.class,
                        String.class
                },
                httpClient,
                "https://example.com/products");

        assertNotNull(result);

        assertEquals(
                "Products",
                getPrivateField(
                        result,
                        "title"));

        assertTrue(
                ((String) getPrivateField(
                        result,
                        "content"))
                        .contains(
                                "This page contains enough content"));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void configureCrawler(
            String target,
            int maxPages,
            int timeout) {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn(target);

        when(solrConfigurationService
                .isExternalIndexingTarget())
                .thenReturn(true);

        when(solrConfigurationService.getExternalCrawlMaxPages())
                .thenReturn(maxPages);

        when(solrConfigurationService
                .getExternalCrawlConnectionTimeout())
                .thenReturn(timeout);
    }

    private <T> T invokePrivate(
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments)
            throws Exception {

        Method method = WebsiteCrawlerServiceImpl.class
                .getDeclaredMethod(
                        methodName,
                        parameterTypes);

        method.setAccessible(true);

        try {

            @SuppressWarnings("unchecked")
            T result = (T) method.invoke(
                    crawlerService,
                    arguments);

            return result;

        } catch (InvocationTargetException e) {

            Throwable cause = e.getCause();

            if (cause instanceof Exception) {
                throw (Exception) cause;
            }

            if (cause instanceof Error) {
                throw (Error) cause;
            }

            throw e;
        }
    }

    private Object getPrivateField(
            Object object,
            String fieldName)
            throws Exception {

        Field field = object.getClass()
                .getDeclaredField(
                        fieldName);

        field.setAccessible(true);

        return field.get(object);
    }

    private java.util.regex.Pattern getHrefPattern()
            throws Exception {

        Field field = WebsiteCrawlerServiceImpl.class
                .getDeclaredField(
                        "HREF_ATTRIBUTE_PATTERN");

        field.setAccessible(true);

        return (java.util.regex.Pattern) field.get(null);
    }

    private void sendHtmlResponse(
            HttpExchange exchange,
            String html)
            throws java.io.IOException {

        byte[] response = html.getBytes(
                StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/html; charset=UTF-8");

        exchange.sendResponseHeaders(
                HttpStatus.SC_OK,
                response.length);

        try (java.io.OutputStream outputStream = exchange.getResponseBody()) {

            outputStream.write(response);
        }
    }
}