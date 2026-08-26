/*
 * Consent-dialog handler, executed in the page via WebDriver.executeScript.
 *
 *   arguments[0]  choice: "accept" | "reject" | "ignore"
 *   arguments[1]  mode:   "act" (click) | "probe" (detect only)
 *
 * Returns null when no consent dialog is visible, otherwise
 *   {cmp, present:true, clicked, action, label, reason}
 *
 * Detection is CMP-first: the YouTube/Google consent bump and the common
 * consent-management platforms are matched by their own markup, and only then a
 * generic "visible container that smells of cookies/consent with a button in
 * it" rule. Within a dialog the button is chosen by the requested action, never
 * by position, so an "accept" arm can never click "Reject all" by accident.
 * Wording is matched in EN/DE/FR/IT/ES on both the visible text and aria-label.
 */
return (function (choice, mode) {
  var ACCEPT_WORDS = [
    'accept all', 'accept the use', 'accept cookies', 'i accept', 'agree to all', 'agree', 'allow all', 'allow cookies',
    'alle akzeptieren', 'akzeptieren', 'zustimmen', 'einverstanden', 'alle zulassen', 'alle cookies erlauben',
    'tout accepter', 'accepter tout', 'accepter', "j'accepte",
    'accetta tutto', 'accetta tutti', 'accetta', 'accetto', 'consenti tutti',
    'aceptar todo', 'aceptar todas', 'aceptar'
  ];
  var REJECT_WORDS = [
    'reject all', 'reject the use', 'reject', 'decline all', 'decline', 'refuse all', 'refuse', 'deny all', 'deny',
    'only necessary', 'necessary only', 'essential only', 'only essential', 'necessary cookies only', 'continue without accepting',
    'alle ablehnen', 'ablehnen', 'nur notwendige', 'nur erforderliche', 'nur essenzielle', 'nur essentielle', 'ohne zustimmung',
    'tout refuser', 'refuser tout', 'refuser', 'continuer sans accepter',
    'rifiuta tutto', 'rifiuta tutti', 'rifiuta', 'solo necessari', 'continua senza accettare',
    'rechazar todo', 'rechazar todas', 'rechazar', 'solo necesarias'
  ];
  var NOT_A_CHOICE = ['more options', 'manage', 'settings', 'customise', 'customize', 'preferences', 'learn more',
    'weitere optionen', 'einstellungen', 'anpassen', 'verwalten', 'mehr erfahren',
    'plus d’options', "plus d'options", 'personnaliser', 'gérer', 'en savoir plus',
    'altre opzioni', 'personalizza', 'gestisci', 'más opciones', 'personalizar', 'configurar'];

  function norm(s) {
    return (s || '').replace(/\s+/g, ' ').replace(/[“”"]/g, '').trim().toLowerCase();
  }
  function visible(el) {
    if (!el) return false;
    try {
      var cs = window.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity || '1') === 0) return false;
      var r = el.getBoundingClientRect();
      return r.width > 0 && r.height > 0;
    } catch (e) { return false; }
  }
  function q(root, sel) {
    var out = [];
    try { out = Array.prototype.slice.call(root.querySelectorAll(sel)); } catch (e) {}
    // usercentrics and a few others render inside a shadow root
    try {
      if (root.shadowRoot) out = out.concat(Array.prototype.slice.call(root.shadowRoot.querySelectorAll(sel)));
    } catch (e) {}
    return out;
  }
  function firstVisible(root, sel) {
    var els = q(root, sel);
    for (var i = 0; i < els.length; i++) if (visible(els[i])) return els[i];
    return null;
  }
  function buttonText(b) {
    return norm(b.innerText || b.textContent) + ' | ' + norm(b.getAttribute('aria-label')) + ' | ' + norm(b.getAttribute('title')) + ' | ' + norm(b.value);
  }
  function matches(text, words) {
    for (var i = 0; i < words.length; i++) if (text.indexOf(words[i]) !== -1) return words[i];
    return null;
  }
  function isNotAChoice(text) {
    return matches(text, NOT_A_CHOICE) !== null;
  }
  // Pick the button for the requested action inside a root, by wording only.
  function pickByWords(root, action) {
    var buttons = q(root, 'button, [role="button"], input[type="button"], input[type="submit"], a[class*="btn"], a[class*="button"]');
    for (var i = 0; i < buttons.length; i++) {
      var b = buttons[i];
      if (!visible(b)) continue;
      var t = buttonText(b);
      if (isNotAChoice(t)) continue;
      var isReject = matches(t, REJECT_WORDS) !== null;
      if (action === 'reject' && isReject) return b;
      // "continue without accepting" contains "accept": reject wording wins
      if (action === 'accept' && !isReject && matches(t, ACCEPT_WORDS) !== null) return b;
    }
    return null;
  }

  var CMPS = [
    { name: 'youtube',
      roots: ['ytd-consent-bump-v2-lightbox', 'ytd-consent-bump-v2-renderer', 'ytd-consent-bump-lightbox', 'yt-consent-bump-v2-lightbox'],
      accept: null, reject: null },
    { name: 'google-consent-page',
      roots: [],
      hostMatch: /^consent\.(youtube|google)\./,
      accept: 'button[aria-label*="Accept" i], form[action*="/save"] button', reject: 'button[aria-label*="Reject" i]' },
    { name: 'onetrust',
      roots: ['#onetrust-banner-sdk', '#onetrust-consent-sdk'],
      accept: '#onetrust-accept-btn-handler', reject: '#onetrust-reject-all-handler' },
    { name: 'cookiebot',
      roots: ['#CybotCookiebotDialog'],
      accept: '#CybotCookiebotDialogBodyLevelButtonLevelOptinAllowAll, #CybotCookiebotDialogBodyButtonAccept',
      reject: '#CybotCookiebotDialogBodyButtonDecline, #CybotCookiebotDialogBodyLevelButtonLevelOptinDeclineAll' },
    { name: 'didomi',
      roots: ['#didomi-host', '#didomi-popup'],
      accept: '#didomi-notice-agree-button', reject: '#didomi-notice-disagree-button' },
    { name: 'usercentrics',
      roots: ['#usercentrics-root', '#usercentrics-cmp-ui'],
      accept: '[data-testid="uc-accept-all-button"]', reject: '[data-testid="uc-deny-all-button"]' },
    { name: 'trustarc',
      roots: ['#truste-consent-track', '#consent_blackbar'],
      accept: '#truste-consent-button', reject: '#truste-consent-required' },
    { name: 'quantcast',
      roots: ['.qc-cmp2-container', '#qc-cmp2-container'],
      accept: null, reject: null },
    { name: 'sourcepoint',
      roots: ['.message-container', '#sp_message_container_' ],
      accept: null, reject: null },
    { name: 'generic',
      roots: ['[id*="cookie" i]', '[class*="cookie" i]', '[id*="consent" i]', '[class*="consent" i]',
              '[id*="gdpr" i]', '[class*="gdpr" i]', '[aria-label*="cookie" i]', '[aria-label*="consent" i]',
              '[id*="privacy" i][role="dialog"]', '[class*="privacy" i][role="dialog"]'],
      accept: null, reject: null }
  ];

  function findRoot(cmp) {
    if (cmp.hostMatch) {
      return cmp.hostMatch.test(window.location.host) ? document.body : null;
    }
    for (var i = 0; i < cmp.roots.length; i++) {
      var els = [];
      try { els = Array.prototype.slice.call(document.querySelectorAll(cmp.roots[i])); } catch (e) {}
      for (var j = 0; j < els.length; j++) {
        var el = els[j];
        // A CMP wrapper often has no box of its own (YouTube's lightbox wraps a
        // position:fixed dialog), so "present" means the wrapper or a control
        // inside it is visible.
        if (!visible(el) && !firstVisible(el, '[role="dialog"], button, [role="button"]')) continue;
        if (cmp.name === 'generic') {
          if (!visible(el)) continue;
          // a link "cookie policy" in the footer also matches; require a real button inside
          if (el.tagName === 'A' || el.tagName === 'BUTTON' || el.tagName === 'SCRIPT') continue;
          if (!firstVisible(el, 'button, [role="button"], input[type="button"], input[type="submit"]')) continue;
          if (!(pickByWords(el, 'accept') || pickByWords(el, 'reject'))) continue;
        }
        return el;
      }
    }
    return null;
  }

  for (var c = 0; c < CMPS.length; c++) {
    var cmp = CMPS[c];
    var root = findRoot(cmp);
    if (!root) continue;

    var result = { cmp: cmp.name, present: true, clicked: false, action: choice, label: null, reason: null };
    if (mode === 'probe') return result;
    if (choice !== 'accept' && choice !== 'reject') {
      result.action = 'ignore';
      result.reason = 'policy is ignore';
      return result;
    }
    var button = null;
    var explicit = choice === 'accept' ? cmp.accept : cmp.reject;
    if (explicit) button = firstVisible(root, explicit);
    if (!button) button = pickByWords(root, choice);
    if (!button) {
      result.reason = 'no ' + choice + ' button found';
      var labels = [];
      var all = q(root, 'button, [role="button"]');
      for (var k = 0; k < all.length && labels.length < 8; k++) if (visible(all[k])) labels.push(buttonText(all[k]).split(' | ')[0]);
      result.label = labels.join(' / ');
      return result;
    }
    result.label = (norm(button.innerText || button.textContent) || norm(button.getAttribute('aria-label')));
    try {
      button.scrollIntoView({ block: 'center' });
    } catch (e) {}
    try {
      button.click();
      result.clicked = true;
    } catch (e) {
      result.reason = 'click failed: ' + e;
    }
    return result;
  }
  return null;
})(arguments[0], arguments[1]);
