(function (document, $) {
  "use strict";

  var registry = $(window).adaptTo("foundation-registry");

  registry.register("foundation.validation.validator", {
    selector: "[data-validation~='action-cards-minimum']",

    validate: function (element) {
      var items = $(element).find("coral-multifield-item");

      if (items.length === 0) {
        return "Minimum of 1 card is required.";
      }

      return null;
    },
  });
})(document, Granite.$);
