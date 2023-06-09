function handle_cookie_action(didConsent) {
  if (didConsent) {
    if (typeof ga !== 'undefined') {
      ga('create', 'UA-82650858-2', 'auto');
      ga('send', 'pageview');
    }
  } else {
    if (typeof ga !== 'undefined') {
      ga('remove');
    }
    document.cookie = '_ga=;expires=Thu, 01 Jan 1970 00:00:01 GMT; path=/; domain=.synthetichealth.github.io';
    document.cookie = '_gat=;expires=Thu, 01 Jan 1970 00:00:01 GMT; path=/; domain=.synthetichealth.github.io';
    document.cookie = '_gid=;expires=Thu, 01 Jan 1970 00:00:01 GMT; path=/; domain=.synthetichealth.github.io';
  }
}

window.cookieconsent.initialise({
  "palette": {
    "popup": {
      "background": "#295677",
      "text": "#fff"
    },
    "button": {
      "background": "#f1d600"
    }
  },
  "revokable": true,
  "theme": "classic",
  "type": "opt-in",
  "content": {
    "message": "We use cookies to personalize content, provide site features, and analyze usage to help improve our site. We share information about your use of our site internally within MITRE. You can adjust your preferences at any time.<br/><input type='checkbox' id='cookiecheckbox' name='cookiecheckbox'><label for='cookiecheckbox'>PERFORMANCE AND FUNCTIONALITY</label><br/>These cookies allow us to analyze site traffic, so we can measure and improve website performance.<br/>",
    "allow": "Accept All Cookies",
    "deny": "Reject All Cookies",
    "link": "MITRE Cookie Notice",
    "href": "cookie_policy.html",
    "target": "_self"
  },
  onInitialise: function (status) {
    handle_cookie_action(this.hasConsented());
  },
  onStatusChange: function(status, chosenBefore) {
    handle_cookie_action(this.hasConsented());
  },
  onRevokeChoice: function() {
    handle_cookie_action(this.hasConsented());
  }
});
