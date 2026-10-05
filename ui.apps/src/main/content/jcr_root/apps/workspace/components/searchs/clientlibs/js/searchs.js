document.addEventListener("DOMContentLoaded", function () {
    const searchComponents = document.querySelectorAll('[data-cmp-is="algolia-search"]');

    searchComponents.forEach(function (configEl) {
        const appId = configEl.getAttribute("data-app-id");
        const apiKey = configEl.getAttribute("data-search-key");
        const indexName = configEl.getAttribute("data-index-name");

        if (appId && apiKey && indexName && window.algoliasearch && window.instantsearch) {
            const searchClient = algoliasearch(appId, apiKey);

            // Scoped elements for multi-component support
            const searchBoxEl = configEl.querySelector('.cmp-searchs__searchbox');
            const hitsEl = configEl.querySelector('.cmp-searchs__hits');
            const paginationEl = configEl.querySelector('.cmp-searchs__pagination');

            const search = instantsearch({
                indexName: indexName,
                searchClient: searchClient,
                searchFunction(helper) {
                    if (helper.state.query.trim() === '') {
                        if (hitsEl) hitsEl.style.display = 'none';
                        if (paginationEl) paginationEl.style.display = 'none';
                        return; 
                    }

                    if (hitsEl) hitsEl.style.display = 'block';
                    if (paginationEl) paginationEl.style.display = 'block';
                    helper.search();
                }
            });

            search.addWidgets([
                instantsearch.widgets.searchBox({
                    container: searchBoxEl,
                    placeholder: 'Type to search...'
                }),
                instantsearch.widgets.hits({
                    container: hitsEl,
                    templates: {
                        empty: '<div class="cmp-searchs__no-results">No results found for <strong>"{{query}}"</strong></div>',
                        item: `
                          <div class="cmp-searchs__hit-item">
                            <a href="{{path}}" class="cmp-searchs__hit-title">{{#helpers.highlight}}{ "attribute": "title" }{{/helpers.highlight}}</a>
                            <div class="cmp-searchs__hit-path">{{path}}</div>
                          </div>
                        `
                    }
                }),
                instantsearch.widgets.pagination({
                    container: paginationEl
                })
            ]);

            search.start();
        }
    });
});