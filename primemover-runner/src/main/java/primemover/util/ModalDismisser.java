package primemover.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for detecting and dismissing common modal overlays
 * that may block page scrolling (cookie consent, donation popups, etc.).
 *
 * Uses a conservative approach - only clicks clearly visible close/accept buttons.
 */
public class ModalDismisser {

    private static Logger logger = LoggerFactory.getLogger(ModalDismisser.class);

    // Common close button CSS selectors (conservative list)
    // Order matters: more specific/reliable selectors first
    private static final String[] CLOSE_BUTTON_SELECTORS = {
        // Aria labels (most reliable)
        "[aria-label*='close' i]",
        "[aria-label*='dismiss' i]",
        "[aria-label*='accept' i]",

        // Generic close buttons
        "button[class*='close' i]",
        "[class*='close-button' i]",
        "[class*='close-btn' i]",

        // Modal-specific close buttons
        "[class*='modal'] button[class*='close' i]",
        "[class*='overlay'] button[class*='close' i]",
        "[class*='popup'] button[class*='close' i]",
        "[class*='banner'] button[class*='close' i]",
        ".modal .close",
        ".modal-close",
        "[data-dismiss='modal']",

        // Cookie consent - common libraries
        "#onetrust-accept-btn-handler",      // OneTrust
        ".cc-dismiss",                        // CookieConsent
        ".cc-btn.cc-allow",                   // CookieConsent allow
        "[class*='CybotCookiebot'] button",   // Cookiebot
        "#CybotCookiebotDialogBodyButtonAccept",

        // Cookie consent - generic patterns
        "[class*='cookie'] button",
        "[class*='consent'] button",
        "[class*='privacy'] button[class*='accept' i]",
        "[class*='gdpr'] button",
        "[id*='cookie'] button",
        "[id*='consent'] button",

        // Common button text patterns (via data attributes or class hints)
        "button[class*='accept' i]",
        "button[class*='agree' i]",
        "button[class*='understand' i]",
        "button[class*='got-it' i]",
        "button[class*='allow' i]",

        // Newsletter/subscription popups
        "[class*='newsletter'] button[class*='close' i]",
        "[class*='subscribe'] button[class*='close' i]",
        "[class*='signup'] button[class*='close' i]",

        // Paywall/membership/support popups
        "[class*='paywall'] button[class*='close' i]",
        "[class*='paywall'] [class*='close']",
        "[class*='membership'] button[class*='close' i]",
        "[class*='support'] button[class*='close' i]",
        "[class*='contribution'] button[class*='close' i]",
        "[class*='donate'] button[class*='close' i]",
        "[class*='splash'] button[class*='close' i]",
        "[class*='splash'] [class*='close']",
        "[class*='interstitial'] button[class*='close' i]",
        "[class*='interstitial'] [class*='close']",

        // Generic close icon patterns (often used for X buttons)
        "[class*='icon-close']",
        "[class*='close-icon']",
        "[class*='btn-close']",
        "svg[class*='close']",
        "[class*='dismiss-button']"
    };

    // Common button text patterns for cookie consent and promotional popups (case-insensitive matching)
    private static final String[] BUTTON_TEXT_PATTERNS = {
        "accept", "i accept", "yes, i accept", "accept all", "accept cookies",
        "agree", "i agree", "agree all",
        "ok", "okay", "got it", "got it!",
        "allow", "allow all", "allow cookies",
        "understand", "i understand",
        "continue", "confirm",
        "no thanks", "no, thanks", "no, thank you", "not now", "maybe later",
        "reject", "decline", "dismiss", "skip", "close"
    };

    /**
     * Attempts to dismiss common modal overlays by clicking visible close buttons.
     * Uses conservative approach - only clicks clearly visible close/accept buttons.
     * First tries CSS selectors, then falls back to matching button text content.
     *
     * @param driver the WebDriver instance
     * @return true if a dismissal was attempted (button found and clicked)
     */
    public static boolean tryDismissModal(WebDriver driver) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;

        try {
            // First try: CSS selectors
            StringBuilder script = new StringBuilder();
            script.append("try {");
            script.append("  var selectors = [");
            for (int i = 0; i < CLOSE_BUTTON_SELECTORS.length; i++) {
                script.append("'").append(CLOSE_BUTTON_SELECTORS[i]).append("'");
                if (i < CLOSE_BUTTON_SELECTORS.length - 1) {
                    script.append(",");
                }
            }
            script.append("  ];");
            script.append("  for (var i = 0; i < selectors.length; i++) {");
            script.append("    try {");
            script.append("      var el = document.querySelector(selectors[i]);");
            // Check visibility: offsetParent is null for hidden elements
            script.append("      if (el && el.offsetParent !== null) {");
            script.append("        el.click();");
            script.append("        return true;");
            script.append("      }");
            script.append("    } catch(e) {}");
            script.append("  }");
            script.append("  return false;");
            script.append("} catch(e) { return false; }");

            Object result = jse.executeScript(script.toString());
            if (Boolean.TRUE.equals(result)) {
                return true;
            }

            // Second try: Match buttons by text content
            result = tryDismissByButtonText(jse);
            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            logger.warn("Modal dismissal error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Tries to find and click buttons based on their text content.
     * Useful for cookie consent banners where buttons have text like "Accept", "I agree", etc.
     */
    private static boolean tryDismissByButtonText(JavascriptExecutor jse) {
        try {
            StringBuilder script = new StringBuilder();
            script.append("try {");
            script.append("  var textPatterns = [");
            for (int i = 0; i < BUTTON_TEXT_PATTERNS.length; i++) {
                script.append("'").append(BUTTON_TEXT_PATTERNS[i]).append("'");
                if (i < BUTTON_TEXT_PATTERNS.length - 1) {
                    script.append(",");
                }
            }
            script.append("  ];");
            script.append("  var buttons = document.querySelectorAll('button, [role=\"button\"], a[class*=\"btn\"], a[class*=\"button\"]');");
            script.append("  for (var i = 0; i < buttons.length; i++) {");
            script.append("    var btn = buttons[i];");
            script.append("    if (!btn.offsetParent) continue;");  // Skip hidden buttons
            script.append("    var text = (btn.innerText || btn.textContent || '').toLowerCase().trim();");
            script.append("    for (var j = 0; j < textPatterns.length; j++) {");
            script.append("      if (text === textPatterns[j] || text.indexOf(textPatterns[j]) !== -1) {");
            script.append("        btn.click();");
            script.append("        return true;");
            script.append("      }");
            script.append("    }");
            script.append("  }");
            script.append("  return false;");
            script.append("} catch(e) { return false; }");

            Object result = jse.executeScript(script.toString());
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Detects if body scroll is likely locked by a modal overlay.
     * Checks if overflow is set to hidden on body or html element.
     *
     * @param driver the WebDriver instance
     * @return true if body overflow is hidden
     */
    public static boolean isBodyScrollLocked(WebDriver driver) {
        JavascriptExecutor jse = (JavascriptExecutor) driver;

        try {
            String script =
                "try {" +
                "  var bodyStyle = window.getComputedStyle(document.body);" +
                "  var htmlStyle = window.getComputedStyle(document.documentElement);" +
                "  return bodyStyle.overflow === 'hidden' || bodyStyle.overflowY === 'hidden' ||" +
                "         htmlStyle.overflow === 'hidden' || htmlStyle.overflowY === 'hidden';" +
                "} catch(e) { return false; }";

            Object result = jse.executeScript(script);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
