(function () {
  "use strict";

  var components = document.querySelectorAll('[data-cmp-is="solr-search"]');

  if (!components.length) {
    return;
  }

  components.forEach(function (component) {
    var input = component.querySelector(".cmp-solr-search__input");

    var button = component.querySelector(".cmp-solr-search__button");

    var status = component.querySelector(".cmp-solr-search__status");

    var resultsList = component.querySelector(".cmp-solr-search__results-list");

    if (!input || !button || !status || !resultsList) {
      return;
    }

    var MAX_SNIPPETS = 3;

    var CONTENT_CONTEXT_BEFORE = 150;

    var CONTENT_CONTEXT_AFTER = 250;

    /*
     * Number of search results displayed on one page.
     */
    var RESULTS_PER_PAGE = 5;

    /*
     * Stores the complete result set returned by Solr.
     */
    var allResults = [];

    /*
     * Current search query used for:
     * - snippets
     * - highlighting
     */
    var currentQuery = "";

    /*
     * Current pagination page.
     */
    var currentPage = 1;

    function search() {
      var query = input.value.trim();

      if (!query) {
        status.textContent = "Please enter a search term.";

        resultsList.innerHTML = "";

        removePagination();

        allResults = [];

        currentQuery = "";

        currentPage = 1;

        return;
      }

      status.textContent = "Searching...";

      resultsList.innerHTML = "";

      removePagination();

      allResults = [];

      currentQuery = query;

      currentPage = 1;

      var url = "/bin/workspace/solr/search?q=" + encodeURIComponent(query);

      fetch(url, {
        method: "GET",
        headers: {
          Accept: "application/json",
        },
      })
        .then(function (response) {
          if (!response.ok) {
            throw new Error(
              "Search request failed with status " + response.status,
            );
          }

          return response.json();
        })
        .then(function (data) {
          renderResults(data);
        })
        .catch(function (error) {
          console.error("Solr search error:", error);

          status.textContent =
            "Unable to complete the search. Please try again.";

          resultsList.innerHTML = "";

          removePagination();

          allResults = [];

          currentPage = 1;
        });
    }

    function renderResults(data) {
      resultsList.innerHTML = "";

      removePagination();

      allResults = data.results || [];

      var totalResults = data.totalResults || 0;

      currentQuery = normalizeValue(data.query);

      currentPage = 1;

      if (totalResults === 0 || !allResults.length) {
        status.textContent = "No results found.";

        return;
      }

      renderCurrentPage(totalResults);
    }

    function renderCurrentPage(totalResults) {
      resultsList.innerHTML = "";

      var totalPages = Math.ceil(allResults.length / RESULTS_PER_PAGE);

      /*
       * Protect against an invalid page number
       * after the result set changes.
       */
      if (currentPage < 1) {
        currentPage = 1;
      }

      if (currentPage > totalPages) {
        currentPage = totalPages;
      }

      var startIndex = (currentPage - 1) * RESULTS_PER_PAGE;

      var endIndex = Math.min(startIndex + RESULTS_PER_PAGE, allResults.length);

      var pageResults = allResults.slice(startIndex, endIndex);

      pageResults.forEach(function (result) {
        renderResultItem(result);
      });

      /*
       * Keep the total result count visible while also
       * showing which pagination page the user is on.
       */
      status.textContent =
        totalResults +
        (totalResults === 1 ? " result found." : " results found.") +
        " Page " +
        currentPage +
        " of " +
        totalPages +
        ".";

      if (totalPages > 1) {
        renderPagination(totalPages);
      }
    }

    function renderResultItem(result) {
      var resultItem = document.createElement("article");

      resultItem.className = "cmp-solr-search__result";

      renderId(resultItem, result);

      renderTitle(resultItem, result);

      renderContent(resultItem, result, currentQuery);

      resultsList.appendChild(resultItem);
    }

    function renderId(resultItem, result) {
      var id = normalizeValue(result.id);

      if (!id) {
        return;
      }

      var field = createField("ID");

      field.value.textContent = id;

      resultItem.appendChild(field.container);
    }

    function renderTitle(resultItem, result) {
      var title = normalizeValue(result.pageTitle);

      if (!title) {
        title = "Untitled";
      }

      var field = createField("Page Title");

      var resultUrl = buildResultUrl(result);

      if (resultUrl) {
        var link = document.createElement("a");

        link.className = "cmp-solr-search__result-title-link";

        link.href = resultUrl;

        link.textContent = title;

        /*
         * AEM pages stay in the same tab.
         * External pages open in a new tab.
         */
        if (!isAemResult(result)) {
          link.target = "_blank";

          link.rel = "noopener noreferrer";
        }

        field.value.appendChild(link);
      } else {
        field.value.textContent = title;
      }

      resultItem.appendChild(field.container);
    }

    function renderContent(resultItem, result, query) {
      var content = normalizeValue(result.pageContent);

      if (!content) {
        return;
      }

      var snippets = createContentSnippets(content, query);

      if (!snippets.length) {
        return;
      }

      var field = createField("Page Content");

      snippets.forEach(function (snippet, index) {
        var snippetElement = document.createElement("div");

        snippetElement.className = "cmp-solr-search__result-snippet";

        if (index < snippets.length - 1) {
          snippetElement.style.marginBottom = "10px";
        }

        appendHighlightedText(snippetElement, snippet, query);

        field.value.appendChild(snippetElement);
      });

      resultItem.appendChild(field.container);
    }

    function createField(labelText) {
      var container = document.createElement("div");

      container.className = "cmp-solr-search__result-field";

      var label = document.createElement("div");

      label.className = "cmp-solr-search__result-label";

      label.textContent = labelText;

      var value = document.createElement("div");

      value.className = "cmp-solr-search__result-value";

      container.appendChild(label);

      container.appendChild(value);

      return {
        container: container,
        value: value,
      };
    }

    function createContentSnippets(content, query) {
      var normalized = content.replace(/\s+/g, " ").trim();

      if (!normalized || !query) {
        return [];
      }

      var terms = getSearchTerms(query);

      if (!terms.length) {
        return [];
      }

      var lowerContent = normalized.toLowerCase();

      var occurrences = [];

      /*
       * Find every occurrence of every search term.
       */
      terms.forEach(function (term) {
        var normalizedTerm = term.toLowerCase();

        var searchIndex = 0;

        while (searchIndex < lowerContent.length) {
          var matchIndex = lowerContent.indexOf(normalizedTerm, searchIndex);

          if (matchIndex === -1) {
            break;
          }

          occurrences.push({
            start: matchIndex,
            end: matchIndex + normalizedTerm.length,
          });

          searchIndex = matchIndex + normalizedTerm.length;
        }
      });

      if (!occurrences.length) {
        return [createFallbackSnippet(normalized)];
      }

      /*
       * Sort matches from beginning to end.
       */
      occurrences.sort(function (first, second) {
        return first.start - second.start;
      });

      /*
       * Remove overlapping occurrences.
       */
      occurrences = mergeOccurrences(occurrences);

      /*
       * Select up to three useful occurrences.
       */
      var selectedOccurrences = selectSnippetOccurrences(occurrences);

      var snippets = [];

      selectedOccurrences.forEach(function (occurrence) {
        var start = Math.max(0, occurrence.start - CONTENT_CONTEXT_BEFORE);

        var end = Math.min(
          normalized.length,
          occurrence.end + CONTENT_CONTEXT_AFTER,
        );

        /*
         * Move beginning to a word boundary.
         */
        if (start > 0) {
          var previousSpace = normalized.lastIndexOf(" ", start);

          if (previousSpace >= 0) {
            start = previousSpace + 1;
          }
        }

        /*
         * Move ending to a word boundary.
         */
        if (end < normalized.length) {
          var nextSpace = normalized.indexOf(" ", end);

          if (nextSpace >= 0) {
            end = nextSpace;
          }
        }

        var snippet = normalized.substring(start, end);

        if (start > 0) {
          snippet = "... " + snippet;
        }

        if (end < normalized.length) {
          snippet = snippet + " ...";
        }

        snippets.push(snippet);
      });

      return snippets;
    }

    function selectSnippetOccurrences(occurrences) {
      if (occurrences.length <= MAX_SNIPPETS) {
        return occurrences;
      }

      var selected = [];

      /*
       * Always include the first occurrence.
       */
      selected.push(occurrences[0]);

      /*
       * Prefer occurrences separated by approximately
       * one complete snippet width.
       */
      var minimumDistance = CONTENT_CONTEXT_BEFORE + CONTENT_CONTEXT_AFTER;

      for (
        var i = 1;
        i < occurrences.length && selected.length < MAX_SNIPPETS;
        i++
      ) {
        var candidate = occurrences[i];

        var tooClose = selected.some(function (selectedOccurrence) {
          return (
            Math.abs(candidate.start - selectedOccurrence.start) <
            minimumDistance
          );
        });

        if (!tooClose) {
          selected.push(candidate);
        }
      }

      /*
       * If matches are all close together, fill the
       * remaining slots so we still show up to 3.
       */
      if (selected.length < MAX_SNIPPETS) {
        for (
          var j = 1;
          j < occurrences.length && selected.length < MAX_SNIPPETS;
          j++
        ) {
          if (selected.indexOf(occurrences[j]) === -1) {
            selected.push(occurrences[j]);
          }
        }
      }

      selected.sort(function (first, second) {
        return first.start - second.start;
      });

      return selected.slice(0, MAX_SNIPPETS);
    }

    function mergeOccurrences(occurrences) {
      if (!occurrences.length) {
        return [];
      }

      var merged = [
        {
          start: occurrences[0].start,
          end: occurrences[0].end,
        },
      ];

      for (var i = 1; i < occurrences.length; i++) {
        var current = occurrences[i];

        var previous = merged[merged.length - 1];

        /*
         * Merge overlapping matches.
         */
        if (current.start <= previous.end) {
          previous.end = Math.max(previous.end, current.end);
        } else {
          merged.push({
            start: current.start,
            end: current.end,
          });
        }
      }

      return merged;
    }

    function createFallbackSnippet(content) {
      var MAX_LENGTH = 420;

      if (content.length <= MAX_LENGTH) {
        return content;
      }

      var snippet = content.substring(0, MAX_LENGTH);

      var lastSpace = snippet.lastIndexOf(" ");

      if (lastSpace > 0) {
        snippet = snippet.substring(0, lastSpace);
      }

      return snippet + "...";
    }

    function appendHighlightedText(container, text, query) {
      if (!text) {
        return;
      }

      var terms = getSearchTerms(query);

      if (!terms.length) {
        container.textContent = text;

        return;
      }

      /*
       * Longest terms first.
       */
      terms.sort(function (first, second) {
        return second.length - first.length;
      });

      var expression = terms.map(escapeRegExp).join("|");

      var pattern = new RegExp("(" + expression + ")", "gi");

      var lastIndex = 0;

      var match;

      while ((match = pattern.exec(text)) !== null) {
        if (match.index > lastIndex) {
          container.appendChild(
            document.createTextNode(text.substring(lastIndex, match.index)),
          );
        }

        var highlighted = document.createElement("mark");

        highlighted.className = "cmp-solr-search__highlight";

        highlighted.textContent = match[0];

        container.appendChild(highlighted);

        lastIndex = pattern.lastIndex;
      }

      if (lastIndex < text.length) {
        container.appendChild(
          document.createTextNode(text.substring(lastIndex)),
        );
      }
    }

    function getSearchTerms(query) {
      if (!query) {
        return [];
      }

      return query
        .trim()
        .split(/\s+/)
        .filter(function (term) {
          return term.length > 0;
        });
    }

    function escapeRegExp(value) {
      return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    }

    /*
     * Creates the actual destination URL.
     */
    function buildResultUrl(result) {
      var pagePath = normalizeValue(result.pagePath);

      var pageUrl = normalizeValue(result.pageUrl);

      /*
       * AEM pages are stored as repository paths.
       *
       * Example:
       * /content/workspace/language-masters/en/products
       *
       * becomes:
       * http://localhost:4502/content/workspace/language-masters/en/products.html
       */
      if (pagePath && pagePath.charAt(0) === "/") {
        var aemPath = pagePath;

        if (!aemPath.toLowerCase().endsWith(".html")) {
          aemPath += ".html";
        }

        return window.location.origin.replace(/\/$/, "") + aemPath;
      }

      /*
       * External page.
       */
      if (pageUrl && isSafeHttpUrl(pageUrl)) {
        return pageUrl;
      }

      if (pagePath && isSafeHttpUrl(pagePath)) {
        return pagePath;
      }

      return "";
    }

    function isAemResult(result) {
      var pagePath = normalizeValue(result.pagePath);

      return pagePath && pagePath.charAt(0) === "/";
    }

    function isSafeHttpUrl(value) {
      try {
        var url = new URL(value, window.location.origin);

        return url.protocol === "http:" || url.protocol === "https:";
      } catch (error) {
        return false;
      }
    }

    function normalizeValue(value) {
      if (value === null || value === undefined) {
        return "";
      }

      if (Array.isArray(value)) {
        return value.join(" ").trim();
      }

      if (typeof value === "object") {
        return JSON.stringify(value);
      }

      return String(value).trim();
    }

    /*
     * Creates and displays pagination controls.
     */
    function renderPagination(totalPages) {
      removePagination();

      var pagination = document.createElement("nav");

      pagination.className = "cmp-solr-search__pagination";

      pagination.setAttribute("aria-label", "Search result pages");

      /*
       * Previous button.
       */
      var previousButton = document.createElement("button");

      previousButton.type = "button";

      previousButton.className =
        "cmp-solr-search__pagination-button cmp-solr-search__pagination-previous";

      previousButton.textContent = "Previous";

      previousButton.disabled = currentPage === 1;

      previousButton.setAttribute("aria-label", "Previous page");

      previousButton.addEventListener("click", function () {
        if (currentPage > 1) {
          currentPage--;

          renderCurrentPage(allResults.length);

          scrollToResults();
        }
      });

      pagination.appendChild(previousButton);

      /*
       * Numbered pages.
       */
      for (var pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
        var pageButton = document.createElement("button");

        pageButton.type = "button";

        pageButton.className = "cmp-solr-search__pagination-button";

        pageButton.textContent = pageNumber;

        pageButton.setAttribute("aria-label", "Go to page " + pageNumber);

        if (pageNumber === currentPage) {
          pageButton.classList.add(
            "cmp-solr-search__pagination-button--active",
          );

          pageButton.setAttribute("aria-current", "page");
        }

        pageButton.addEventListener(
          "click",
          (function (selectedPage) {
            return function () {
              currentPage = selectedPage;

              renderCurrentPage(allResults.length);

              scrollToResults();
            };
          })(pageNumber),
        );

        pagination.appendChild(pageButton);
      }

      /*
       * Next button.
       */
      var nextButton = document.createElement("button");

      nextButton.type = "button";

      nextButton.className =
        "cmp-solr-search__pagination-button cmp-solr-search__pagination-next";

      nextButton.textContent = "Next";

      nextButton.disabled = currentPage === totalPages;

      nextButton.setAttribute("aria-label", "Next page");

      nextButton.addEventListener("click", function () {
        if (currentPage < totalPages) {
          currentPage++;

          renderCurrentPage(allResults.length);

          scrollToResults();
        }
      });

      pagination.appendChild(nextButton);

      /*
       * Put pagination immediately after the result list.
       */
      resultsList.insertAdjacentElement("afterend", pagination);
    }

    function removePagination() {
      var pagination = component.querySelector(".cmp-solr-search__pagination");

      if (pagination) {
        pagination.remove();
      }
    }

    function scrollToResults() {
      var resultsTop =
        resultsList.getBoundingClientRect().top + window.pageYOffset - 100;

      window.scrollTo({
        top: Math.max(0, resultsTop),
        behavior: "smooth",
      });
    }

    button.addEventListener("click", search);

    input.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        event.preventDefault();

        search();
      }
    });
  });
})();
