(function (document, window) {

    "use strict";

    var SEARCH_ENDPOINT =
        "/bin/workspace/elasticsearch/search-mvp";

    var PAGE_SIZE = 10;

    var component;
    var form;
    var input;
    var button;
    var results;
    var summary;
    var validation;
    var pagination;

    var currentPage = 1;
    var currentQuery = "";
    var loading = false;


    /* =========================================================
       INITIALIZE
       ========================================================= */

    function init() {

        component = document.querySelector(
            '[data-component="elasticsearch-search"]'
        );

        if (!component) {
            console.error(
                "[ES Search] Search component not found."
            );
            return;
        }

        form = component.querySelector(
            "[data-elasticsearch-search-form]"
        );

        input = component.querySelector(
            "[data-elasticsearch-search-input]"
        );

        button = component.querySelector(
            "[data-elasticsearch-search-button]"
        );

        results = component.querySelector(
            "[data-search-results]"
        );

        summary = component.querySelector(
            "[data-search-summary]"
        );

        validation = component.querySelector(
            "[data-search-validation]"
        );

        pagination = component.querySelector(
            "[data-search-pagination]"
        );


        /*
         * Agar HTML mein pagination container
         * nahi hai to JS khud create karega.
         */

        if (!pagination) {

            pagination =
                document.createElement("div");

            pagination.setAttribute(
                "data-search-pagination",
                ""
            );

            pagination.className =
                "elasticsearch-search__pagination";

            component.appendChild(
                pagination
            );
        }


        if (!input || !results) {

            console.error(
                "[ES Search] Required elements missing."
            );

            return;
        }


        bindEvents();


        console.info(
            "[ES Search] Search component initialized."
        );
    }


    /* =========================================================
       ESCAPE HTML
       ========================================================= */

    function escapeHtml(value) {

        if (
            value === null ||
            value === undefined
        ) {
            return "";
        }

        return String(value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }


    /* =========================================================
       GET TOTAL
       ========================================================= */

    function getTotal(hits) {

        if (!hits || !hits.total) {
            return 0;
        }

        if (
            typeof hits.total === "number"
        ) {
            return hits.total;
        }

        return Number(
            hits.total.value
        ) || 0;
    }


    /* =========================================================
       SUMMARY
       ========================================================= */

    function renderSummary(
        total,
        page,
        totalPages
    ) {

        if (!summary) {
            return;
        }

        if (total === 0) {

            summary.textContent =
                '0 results found for "' +
                currentQuery +
                '".';

            return;
        }


        /*
         * Dynamic summary:
         *
         * 1 results found. Page 1 of 1.
         *
         * 68 results found. Page 1 of 7.
         */

        summary.textContent =
            total +
            " results found. Page " +
            page +
            " of " +
            totalPages +
            ".";
    }


    /* =========================================================
       PAGE URL
       ========================================================= */

    function getPageUrl(path) {

        if (!path) {
            return "#";
        }

        path =
            String(path).trim();

        if (!path) {
            return "#";
        }


        /*
         * External page.
         */

        if (
            /^https?:\/\//i.test(path)
        ) {
            return path;
        }


        /*
         * Already .html
         */

        if (
            /\.html(?:[?#].*)?$/i.test(path)
        ) {
            return path;
        }


        /*
         * AEM page path.
         */

        if (
            path.charAt(0) === "/"
        ) {
            return path + ".html";
        }

        return "#";
    }


    /* =========================================================
       PLAIN TEXT
       ========================================================= */

    function plainText(value) {

        if (!value) {
            return "";
        }

        var div =
            document.createElement("div");

        div.innerHTML =
            String(value);

        return (
            div.textContent ||
            div.innerText ||
            ""
        )
            .replace(/\s+/g, " ")
            .trim();
    }


    /* =========================================================
       COUNT OCCURRENCES
       ========================================================= */

    function countOccurrences(
        text,
        query
    ) {

        if (!text || !query) {
            return 0;
        }

        var source =
            String(text).toLowerCase();

        var searchQuery =
            String(query)
                .trim()
                .toLowerCase();

        if (!searchQuery) {
            return 0;
        }


        var count = 0;
        var position = 0;


        /*
         * Counts overlapping occurrences.
         *
         * Example:
         *
         * text  = aaa
         * query = aa
         *
         * result = 2
         */

        while (true) {

            var index =
                source.indexOf(
                    searchQuery,
                    position
                );

            if (index === -1) {
                break;
            }

            count++;

            position =
                index + 1;
        }

        return count;
    }


    /* =========================================================
       RENDER ELASTICSEARCH HIGHLIGHT
       ========================================================= */

    function renderHighlight(
        fragment
    ) {

        if (!fragment) {
            return "";
        }

        var temp =
            document.createElement("div");

        temp.innerHTML =
            String(fragment);


        function nodeToHtml(node) {

            /*
             * Text node.
             */

            if (
                node.nodeType ===
                Node.TEXT_NODE
            ) {

                return escapeHtml(
                    node.nodeValue
                );
            }


            /*
             * Ignore comments and others.
             */

            if (
                node.nodeType !==
                Node.ELEMENT_NODE
            ) {
                return "";
            }


            var childHtml = "";


            for (
                var i = 0;
                i < node.childNodes.length;
                i++
            ) {

                childHtml +=
                    nodeToHtml(
                        node.childNodes[i]
                    );
            }


            /*
             * Only em is allowed.
             */

            if (
                node.tagName &&
                node.tagName.toLowerCase() ===
                "em"
            ) {

                return (
                    "<em>" +
                    childHtml +
                    "</em>"
                );
            }


            return childHtml;
        }


        var html = "";


        for (
            var i = 0;
            i < temp.childNodes.length;
            i++
        ) {

            html +=
                nodeToHtml(
                    temp.childNodes[i]
                );
        }


        return html;
    }


    /* =========================================================
       FALLBACK HIGHLIGHT
       ========================================================= */

    function highlightText(
        text,
        query
    ) {

        if (!text) {
            return "";
        }

        var safe =
            escapeHtml(text);


        if (!query) {
            return safe;
        }


        var words =
            query
                .trim()
                .split(/\s+/)
                .filter(function (word) {

                    return word.length > 0;

                });


        if (!words.length) {
            return safe;
        }


        words.sort(function (a, b) {

            return b.length - a.length;

        });


        var pattern =
            words
                .map(function (word) {

                    return word.replace(
                        /[.*+?^${}()|[\]\\]/g,
                        "\\$&"
                    );

                })
                .join("|");


        try {

            return safe.replace(
                new RegExp(
                    "(" + pattern + ")",
                    "gi"
                ),
                "<em>$1</em>"
            );

        } catch (error) {

            return safe;
        }
    }


    /* =========================================================
       GET ELASTICSEARCH HIGHLIGHT
       ========================================================= */

    function getHitHighlight(
        hit
    ) {

        if (
            !hit ||
            !hit.highlight
        ) {
            return "";
        }


        var highlight =
            hit.highlight;


        var fields = [
            "content",
            "description",
            "title"
        ];


        for (
            var i = 0;
            i < fields.length;
            i++
        ) {

            var fragments =
                highlight[
                    fields[i]
                ];


            if (
                Array.isArray(fragments) &&
                fragments.length > 0
            ) {

                return fragments
                    .slice(0, 2)
                    .map(function (fragment) {

                        return renderHighlight(
                            fragment
                        );

                    })
                    .join(" ... ");
            }
        }


        return "";
    }


    /* =========================================================
       RESULT
       ========================================================= */

    function renderResult(
        hit
    ) {

        var source =
            hit && hit._source
                ? hit._source
                : {};


        var title =
            source.title ||
            source.pageTitle ||
            source.name ||
            "Untitled Page";


        var path =
            source.path ||
            source.url ||
            "";


        var description =
            plainText(
                source.description || ""
            );


        var content =
            plainText(
                source.content || ""
            );


        /*
         * Occurrence count across:
         *
         * title
         * description
         * content
         */

        var occurrenceSource =
            title +
            " " +
            description +
            " " +
            content;


        var occurrenceCount =
            countOccurrences(
                occurrenceSource,
                currentQuery
            );


        /*
         * Elasticsearch highlight.
         */

        var elasticHighlight =
            getHitHighlight(hit);


        /*
         * If ES highlight is unavailable,
         * frontend highlighting is used.
         */

        var displayContent =
            elasticHighlight ||
            highlightText(
                description ||
                content ||
                title,
                currentQuery
            );


        /*
         * Page URL.
         */

        var pageUrl =
            getPageUrl(path);


        var html = "";


        html +=
            '<article class="' +
            'elasticsearch-search__result">';


        /* =====================================================
           ID + OCCURRENCE COUNT
           ===================================================== */

        html +=
            '<div class="' +
            'elasticsearch-search__result-id">';


        html +=
            "<strong>ID</strong><br>";


        /*
         * IMPORTANT:
         *
         * Path intentionally removed from UI.
         *
         * Dynamic search query + dynamic occurrence count
         * is displayed here.
         *
         * Example:
         *
         * es found 16 times on this page
         *
         * played found 2 times on this page
         */

        html +=
            "<strong>" +
            escapeHtml(currentQuery) +
            " found " +
            occurrenceCount +
            " " +
            (
                occurrenceCount === 1
                    ? "time"
                    : "times"
            ) +
            " on this page" +
            "</strong>";


        html +=
            "</div>";


        /* =====================================================
           PAGE TITLE
           ===================================================== */

        html +=
            '<div class="' +
            'elasticsearch-search__result-title">';


        html +=
            "<strong>PAGE TITLE</strong><br>";


        if (pageUrl !== "#") {

            html +=
                '<a href="' +
                escapeHtml(pageUrl) +
                '" target="_blank" ' +
                'rel="noopener noreferrer">' +
                escapeHtml(title) +
                "</a>";

        } else {

            html +=
                escapeHtml(title);
        }


        html +=
            "</div>";


        /* =====================================================
           PAGE CONTENT
           ===================================================== */

        html +=
            '<div class="' +
            'elasticsearch-search__result-content">';


        html +=
            "<strong>PAGE CONTENT</strong><br>";


        html +=
            displayContent ||
            "No content available.";


        html +=
            "</div>";


        html +=
            "</article>";


        return html;
    }


    /* =========================================================
       PAGE BUTTON
       ========================================================= */

    function createPageButton(
        text,
        page,
        disabled,
        active
    ) {

        var pageButton =
            document.createElement("button");


        pageButton.type =
            "button";


        pageButton.textContent =
            text;


        pageButton.disabled =
            disabled;


        pageButton.className =
            "elasticsearch-search__page-button";


        if (active) {

            pageButton.classList.add(
                "is-active"
            );

            pageButton.setAttribute(
                "aria-current",
                "page"
            );
        }


        pageButton.addEventListener(
            "click",
            function () {

                if (
                    disabled ||
                    loading
                ) {
                    return;
                }


                currentPage =
                    page;


                search();
            }
        );


        return pageButton;
    }


    /* =========================================================
       PAGINATION
       ========================================================= */

    function renderPagination(
        total
    ) {

        if (!pagination) {
            return;
        }


        pagination.innerHTML =
            "";


        var totalPages =
            Math.max(
                1,
                Math.ceil(
                    total / PAGE_SIZE
                )
            );


        console.log(
            "[ES Search] Total results:",
            total
        );


        console.log(
            "[ES Search] Current page:",
            currentPage
        );


        console.log(
            "[ES Search] Total pages:",
            totalPages
        );


        /*
         * Only one page.
         */

        if (totalPages <= 1) {

            pagination.style.display =
                "none";

            return;
        }


        /*
         * Multiple pages.
         */

        pagination.style.display =
            "flex";

        pagination.style.visibility =
            "visible";


        /*
         * Previous
         */

        pagination.appendChild(
            createPageButton(
                "Previous",
                currentPage - 1,
                currentPage === 1,
                false
            )
        );


        /*
         * Direct page buttons.
         */

        for (
            var page = 1;
            page <= totalPages;
            page++
        ) {

            pagination.appendChild(
                createPageButton(
                    String(page),
                    page,
                    page === currentPage,
                    page === currentPage
                )
            );
        }


        /*
         * Next
         */

        pagination.appendChild(
            createPageButton(
                "Next",
                currentPage + 1,
                currentPage === totalPages,
                false
            )
        );
    }


    /* =========================================================
       NO RESULTS
       ========================================================= */

    function showNoResults() {

        results.innerHTML =
            '<div class="' +
            'elasticsearch-search__no-results">' +
            "<h3>No results found</h3>" +
            "<p>Try another keyword.</p>" +
            "</div>";


        renderSummary(
            0,
            1,
            1
        );


        if (pagination) {

            pagination.innerHTML =
                "";

            pagination.style.display =
                "none";
        }
    }


    /* =========================================================
       SEARCH ERROR
       ========================================================= */

    function showError() {

        results.innerHTML =
            '<div class="' +
            'elasticsearch-search__error">' +
            "<h3>Search failed</h3>" +
            "<p>" +
            "Unable to fetch search results. " +
            "Please try again." +
            "</p>" +
            "</div>";


        if (summary) {

            summary.textContent =
                "Search request failed.";
        }


        if (pagination) {

            pagination.innerHTML =
                "";

            pagination.style.display =
                "none";
        }
    }


    /* =========================================================
       SEARCH
       ========================================================= */

    function search() {

        var query =
            input.value.trim();


        /*
         * Empty query.
         */

        if (!query) {

            showValidation(
                "Please enter a search keyword."
            );


            results.innerHTML =
                "";


            if (summary) {

                summary.textContent =
                    "";
            }


            if (pagination) {

                pagination.innerHTML =
                    "";

                pagination.style.display =
                    "none";
            }


            return;
        }


        showValidation("");


        currentQuery =
            query;


        loading = true;


        results.innerHTML =
            '<div class="' +
            'elasticsearch-search__loading">' +
            "Searching..." +
            "</div>";


        if (summary) {

            summary.textContent =
                "Searching...";
        }


        if (pagination) {

            pagination.innerHTML =
                "";

            pagination.style.display =
                "none";
        }


        /*
         * q     = search query
         * page  = current page
         * size  = 10
         */

        var requestUrl =
            SEARCH_ENDPOINT +
            "?q=" +
            encodeURIComponent(
                query
            ) +
            "&page=" +
            encodeURIComponent(
                currentPage
            ) +
            "&size=" +
            encodeURIComponent(
                PAGE_SIZE
            );


        console.log(
            "[ES Search] Request:",
            requestUrl
        );


        fetch(
            requestUrl,
            {
                method: "GET",

                headers: {
                    "Accept":
                        "application/json"
                },

                credentials:
                    "same-origin"
            }
        )

        .then(function (response) {

            if (!response.ok) {

                throw new Error(
                    "HTTP " +
                    response.status
                );
            }


            return response.json();
        })


        .then(function (data) {

            console.log(
                "[ES Search] Response:",
                data
            );


            var elasticsearch =
                data &&
                data.elasticsearch;


            var hitsObject =
                elasticsearch &&
                elasticsearch.hits;


            if (!hitsObject) {

                throw new Error(
                    "Invalid Elasticsearch response."
                );
            }


            var total =
                getTotal(
                    hitsObject
                );


            var totalPages =
                Math.max(
                    1,
                    Math.ceil(
                        total / PAGE_SIZE
                    )
                );


            /*
             * Protect against invalid page.
             */

            if (
                currentPage >
                totalPages
            ) {

                currentPage =
                    totalPages;


                search();

                return;
            }


            /*
             * No results.
             */

            if (
                total === 0 ||
                !Array.isArray(
                    hitsObject.hits
                ) ||
                hitsObject.hits.length === 0
            ) {

                showNoResults();

                return;
            }


            /*
             * Dynamic summary.
             *
             * Example:
             *
             * 1 results found. Page 1 of 1.
             *
             * 68 results found. Page 1 of 7.
             */

            renderSummary(
                total,
                currentPage,
                totalPages
            );


            /*
             * Render results.
             */

            results.innerHTML =
                hitsObject.hits
                    .map(function (hit) {

                        return renderResult(
                            hit
                        );

                    })
                    .join("");


            /*
             * Render pagination.
             */

            renderPagination(
                total
            );
        })


        .catch(function (error) {

            console.error(
                "[ES Search] Search error:",
                error
            );


            showError();
        })


        .finally(function () {

            loading = false;
        });
    }


    /* =========================================================
       VALIDATION
       ========================================================= */

    function showValidation(
        message
    ) {

        if (!validation) {
            return;
        }


        validation.textContent =
            message || "";


        validation.hidden =
            !message;
    }


    /* =========================================================
       EVENTS
       ========================================================= */

    function bindEvents() {

        /*
         * Form submit.
         */

        if (form) {

            form.addEventListener(
                "submit",
                function (event) {

                    event.preventDefault();


                    currentPage =
                        1;


                    search();
                }
            );
        }


        /*
         * Search button.
         */

        if (button) {

            button.addEventListener(
                "click",
                function (event) {

                    event.preventDefault();


                    currentPage =
                        1;


                    search();
                }
            );
        }


        /*
         * Enter key.
         */

        input.addEventListener(
            "keydown",
            function (event) {

                if (
                    event.key ===
                    "Enter"
                ) {

                    event.preventDefault();


                    currentPage =
                        1;


                    search();
                }
            }
        );


        /*
         * Clear results when input
         * becomes empty.
         */

        input.addEventListener(
            "input",
            function () {

                if (
                    !input.value.trim()
                ) {

                    currentQuery =
                        "";

                    currentPage =
                        1;


                    results.innerHTML =
                        "";


                    if (summary) {

                        summary.textContent =
                            "";
                    }


                    showValidation("");


                    if (pagination) {

                        pagination.innerHTML =
                            "";

                        pagination.style.display =
                            "none";
                    }
                }
            }
        );
    }


    /* =========================================================
       START
       ========================================================= */

    if (
        document.readyState ===
        "loading"
    ) {

        document.addEventListener(
            "DOMContentLoaded",
            init
        );

    } else {

        init();
    }

})(document, window);