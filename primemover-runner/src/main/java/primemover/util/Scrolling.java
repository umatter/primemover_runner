package primemover.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scrolling {

	private static Logger logger = LoggerFactory.getLogger(Scrolling.class);
	
	
	public static Boolean isScrolledToBootom(WebDriver driver) {
		JavascriptExecutor jse = (JavascriptExecutor)driver;

		/*
// document.body.scrollTop alone should do the job but that actually works only in case of Chrome.
// With IE and Firefox it also works sometimes (seemingly with very simple pages where you have
// only a <pre> or something like that) but I don't know when. This hack seems to work always.
var scrollTop = (document.documentElement && document.documentElement.scrollTop) || document.body.scrollTop;

// Grodriguez's fix for scrollHeight:
// accounting for cases where html/body are set to height:100%
var scrollHeight = (document.documentElement && document.documentElement.scrollHeight) || document.body.scrollHeight;

// >= is needed because if the horizontal scrollbar is visible then window.innerHeight includes
// it and in that case the left side of the equation is somewhat greater.
var scrolledToBottom = (scrollTop + window.innerHeight) >= scrollHeight;
		 */

		// Use defensive JavaScript that handles edge cases where document.body might be null
		StringBuilder script = new StringBuilder();
		script.append("try {");
		script.append("  let scrollTop = window.pageYOffset || document.documentElement.scrollTop || (document.body ? document.body.scrollTop : 0) || 0;");
		script.append("  let scrollHeight = Math.max(document.documentElement.scrollHeight || 0, document.body ? document.body.scrollHeight : 0);");
		script.append("  let viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;");
		script.append("  return (scrollTop + viewportHeight) >= (scrollHeight - 1);");
		script.append("} catch(e) { return false; }");
		Boolean res = (Boolean) jse.executeScript(script.toString());

		return res;
	}
	
	public static Boolean isScrolledToTop(WebDriver driver) {
		JavascriptExecutor jse = (JavascriptExecutor)driver;

		// Use defensive JavaScript with proper comparison (was using assignment = instead of comparison ==)
		StringBuilder script = new StringBuilder();
		script.append("try {");
		script.append("  let scrollPos = window.pageYOffset || document.documentElement.scrollTop || (document.body ? document.body.scrollTop : 0) || 0;");
		script.append("  return scrollPos <= 1;");
		script.append("} catch(e) { return false; }");
		Boolean res = (Boolean) jse.executeScript(script.toString());

		return res;
	}

	/**
	 * Gets the current vertical scroll position.
	 * Returns -1 if position cannot be determined.
	 */
	private static long getCurrentScrollPosition(JavascriptExecutor jse) {
		try {
			StringBuilder script = new StringBuilder();
			script.append("try {");
			script.append("  return Math.round(window.pageYOffset || ");
			script.append("    document.documentElement.scrollTop || ");
			script.append("    (document.body ? document.body.scrollTop : 0) || 0);");
			script.append("} catch(e) { return -1; }");
			Object result = jse.executeScript(script.toString());
			if (result instanceof Number) {
				return ((Number) result).longValue();
			}
			return -1;
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Attempts to scroll using document.documentElement.scrollTop as fallback.
	 * Some sites respond better to direct property modification.
	 */
	private static boolean tryFallbackScroll(JavascriptExecutor jse, int pixels, boolean down) {
		try {
			String direction = down ? "+" : "-";
			StringBuilder script = new StringBuilder();
			script.append("try {");
			script.append("  var before = window.pageYOffset || document.documentElement.scrollTop || 0;");
			script.append("  if (document.documentElement) {");
			script.append("    document.documentElement.scrollTop " + direction + "= " + pixels + ";");
			script.append("  }");
			script.append("  if (document.body) {");
			script.append("    document.body.scrollTop " + direction + "= " + pixels + ";");
			script.append("  }");
			script.append("  var after = window.pageYOffset || document.documentElement.scrollTop || 0;");
			script.append("  return Math.abs(after - before) > 0;");
			script.append("} catch(e) { return false; }");

			Object result = jse.executeScript(script.toString());
			return Boolean.TRUE.equals(result);
		} catch (Exception e) {
			return false;
		}
	}

	public static void scrollDownByJavascript(WebDriver driver, Integer length) {
		JavascriptExecutor jse = (JavascriptExecutor)driver;
   	 
		StringBuilder script = new StringBuilder();
		
		script.append("let scrollPos = window.scrollY || window.scrollTop || document.getElementsByTagName(\"html\")[0].scrollTop;");
		script.append("let scrollHeight = (document.documentElement && document.documentElement.scrollHeight) || document.body.scrollHeight;");
		script.append("let opt = {");
		script.append("left: 0,");
		script.append("top: scrollPos + scrollHeight / 100 * " + length + ",");
		script.append("behavior: 'smooth'");
		script.append("};");
		script.append("window.scrollTo(opt);"); // TODO: call to prototype?
		script.append("return true;");
		
		Boolean res = (Boolean) jse.executeScript(script.toString());
		logger.debug("Scrolling down with result: {}", res);
	}

	public static void scrollDownForDurationByJavascript(WebDriver driver, Integer seconds) {
		JavascriptExecutor jse = (JavascriptExecutor) driver;

		// Proactively try to dismiss any visible modals before starting scroll
		try {
			if (ModalDismisser.tryDismissModal(driver)) {
				logger.info("Dismissed modal before scrolling");
				Thread.sleep(300); // Wait for modal animation to complete
			}
		} catch (Exception e) {
			// Ignore errors during proactive dismissal
		}

		// Scroll incrementally over the specified duration
		// Use small steps to simulate natural scrolling behavior
		int totalMillis = seconds * 1000;
		int stepMillis = 100; // scroll every 100ms
		int steps = totalMillis / stepMillis;
		int pixelsPerStep = 50; // scroll 50 pixels per step (adjustable for natural feel)
		int consecutiveErrors = 0;
		int maxConsecutiveErrors = 5; // Stop if we get too many errors in a row
		int consecutiveNoMovement = 0; // Track scroll failures (page didn't move)
		int maxConsecutiveNoMovement = 3; // Threshold before trying modal dismiss

		for (int i = 0; i < steps; i++) {
			try {
				// Check if we've reached the bottom
				Boolean atBottom = isScrolledToBootom(driver);
				if (atBottom != null && atBottom) {
					logger.debug("Reached bottom of page after {} steps", i);
					break;
				}

				// Get position before scroll attempt
				long positionBefore = getCurrentScrollPosition(jse);

				// Scroll down by a small amount - use try-catch for defensive scrolling
				jse.executeScript("try { window.scrollBy({top: " + pixelsPerStep + ", behavior: 'smooth'}); } catch(e) {}");

				// Small delay to let scroll take effect
				Thread.sleep(20);

				// Get position after scroll attempt
				long positionAfter = getCurrentScrollPosition(jse);

				// Check if scroll actually worked (position should increase when scrolling down)
				if (positionBefore >= 0 && positionAfter >= 0 && positionAfter <= positionBefore) {
					consecutiveNoMovement++;
					logger.debug("Scroll did not move (stuck at {}), attempt {}", positionAfter, consecutiveNoMovement);

					if (consecutiveNoMovement >= maxConsecutiveNoMovement) {
						// Try to dismiss modal first
						logger.debug("Attempting to dismiss modal overlay");
						boolean dismissed = ModalDismisser.tryDismissModal(driver);
						if (dismissed) {
							logger.debug("Modal dismiss attempted, waiting for animation");
							Thread.sleep(200); // Wait for modal animation
							consecutiveNoMovement = 0; // Reset counter after dismiss
							continue; // Retry scroll on next iteration
						}

						// Try fallback scroll method
						logger.debug("Trying fallback scroll method (documentElement.scrollTop)");
						boolean fallbackWorked = tryFallbackScroll(jse, pixelsPerStep, true);
						if (fallbackWorked) {
							logger.debug("Fallback scroll succeeded");
							consecutiveNoMovement = 0;
						} else {
							logger.debug("Fallback scroll also failed, continuing");
							// Don't break - page might just be slow to respond
						}
					}
				} else {
					consecutiveNoMovement = 0; // Reset on successful scroll
				}

				consecutiveErrors = 0; // Reset on success
			} catch (Exception e) {
				consecutiveErrors++;
				logger.warn("Error during scroll step {}: {}", i, e.getMessage());
				if (consecutiveErrors >= maxConsecutiveErrors) {
					logger.warn("Too many consecutive scroll errors, stopping scroll");
					break;
				}
			}

			try {
				Thread.sleep(stepMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		logger.debug("Scrolled down for {} seconds", seconds);
	}

	public static void scrollUpForDurationByJavascript(WebDriver driver, Integer seconds) {
		JavascriptExecutor jse = (JavascriptExecutor) driver;

		// Proactively try to dismiss any visible modals before starting scroll
		try {
			if (ModalDismisser.tryDismissModal(driver)) {
				logger.info("Dismissed modal before scrolling");
				Thread.sleep(300); // Wait for modal animation to complete
			}
		} catch (Exception e) {
			// Ignore errors during proactive dismissal
		}

		// Scroll incrementally over the specified duration
		int totalMillis = seconds * 1000;
		int stepMillis = 100; // scroll every 100ms
		int steps = totalMillis / stepMillis;
		int pixelsPerStep = 50; // scroll 50 pixels per step
		int consecutiveErrors = 0;
		int maxConsecutiveErrors = 5; // Stop if we get too many errors in a row
		int consecutiveNoMovement = 0; // Track scroll failures (page didn't move)
		int maxConsecutiveNoMovement = 3; // Threshold before trying modal dismiss

		for (int i = 0; i < steps; i++) {
			try {
				// Check if we've reached the top
				Boolean atTop = isScrolledToTop(driver);
				if (atTop != null && atTop) {
					logger.debug("Reached top of page after {} steps", i);
					break;
				}

				// Get position before scroll attempt
				long positionBefore = getCurrentScrollPosition(jse);

				// Scroll up by a small amount - use try-catch for defensive scrolling
				jse.executeScript("try { window.scrollBy({top: -" + pixelsPerStep + ", behavior: 'smooth'}); } catch(e) {}");

				// Small delay to let scroll take effect
				Thread.sleep(20);

				// Get position after scroll attempt
				long positionAfter = getCurrentScrollPosition(jse);

				// Check if scroll actually worked (position should decrease when scrolling up)
				if (positionBefore >= 0 && positionAfter >= 0 && positionAfter >= positionBefore) {
					consecutiveNoMovement++;
					logger.debug("Scroll did not move (stuck at {}), attempt {}", positionAfter, consecutiveNoMovement);

					if (consecutiveNoMovement >= maxConsecutiveNoMovement) {
						// Try to dismiss modal first
						logger.debug("Attempting to dismiss modal overlay");
						boolean dismissed = ModalDismisser.tryDismissModal(driver);
						if (dismissed) {
							logger.debug("Modal dismiss attempted, waiting for animation");
							Thread.sleep(200); // Wait for modal animation
							consecutiveNoMovement = 0; // Reset counter after dismiss
							continue; // Retry scroll on next iteration
						}

						// Try fallback scroll method
						logger.debug("Trying fallback scroll method (documentElement.scrollTop)");
						boolean fallbackWorked = tryFallbackScroll(jse, pixelsPerStep, false); // false = scroll up
						if (fallbackWorked) {
							logger.debug("Fallback scroll succeeded");
							consecutiveNoMovement = 0;
						} else {
							logger.debug("Fallback scroll also failed, continuing");
							// Don't break - page might just be slow to respond
						}
					}
				} else {
					consecutiveNoMovement = 0; // Reset on successful scroll
				}

				consecutiveErrors = 0; // Reset on success
			} catch (Exception e) {
				consecutiveErrors++;
				logger.warn("Error during scroll step {}: {}", i, e.getMessage());
				if (consecutiveErrors >= maxConsecutiveErrors) {
					logger.warn("Too many consecutive scroll errors, stopping scroll");
					break;
				}
			}

			try {
				Thread.sleep(stepMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		logger.debug("Scrolled up for {} seconds", seconds);
	}


	public static void scrollUpByJavascript(WebDriver driver, Integer length) {
		JavascriptExecutor jse = (JavascriptExecutor)driver;
   	 
		StringBuilder script = new StringBuilder();
		script.append("let scrollPos = window.scrollY || window.scrollTop || document.getElementsByTagName(\"html\")[0].scrollTop;");
		script.append("let scrollHeight = Math.max(\r\n" + 
				"  document.body.scrollHeight, document.documentElement.scrollHeight,\r\n" + 
				"  document.body.offsetHeight, document.documentElement.offsetHeight,\r\n" + 
				"  document.body.clientHeight, document.documentElement.clientHeight\r\n" + 
				");");
		script.append("let opt = {");
		script.append("left: 0,");
		script.append("top: scrollPos - scrollHeight / 100 * " + length + ",");
		script.append("behavior: 'smooth'");
		script.append("};");
		script.append("window.scrollTo(opt);"); // TODO: call to prototype?
		script.append("return true;");
		
		Boolean res = (Boolean) jse.executeScript(script.toString());

		logger.debug("Scrolling up with result: {}", res);
	}
}
