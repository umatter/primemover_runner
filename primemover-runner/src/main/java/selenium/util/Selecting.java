package selenium.util;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import primemover.model.DecisionType;
import primemover.model.SelectorType;

public class Selecting {

	public static List<WebElement> getElementsByWebdriver(WebDriver driver, SelectorType type, String selector) {
		List<WebElement> elements;
		switch (type) {
		case CLASS:
			elements = driver.findElements(By.className(selector));
			break;
		case CSS:
			elements = driver.findElements(By.cssSelector(selector));
			break;
		case ID:
			elements = driver.findElements(By.id(selector));
			break;
		case XPATH:
			elements = driver.findElements(By.xpath(selector));
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}

		return elements;
	}

	/***
	 * Will return the first found element on the page
	 * 
	 * @param driver
	 * @param type
	 * @param selector
	 * @return
	 */
	public static WebElement getElementByWebdriver(WebDriver driver, SelectorType type, String selector) {
		WebElement element;
		switch (type) {
		case CLASS:
			element = driver.findElement(By.className(selector));
			break;
		case CSS:
			element = driver.findElement(By.cssSelector(selector));
			break;
		case ID:
			element = driver.findElement(By.id(selector));
			break;
		case XPATH:
			element = driver.findElement(By.xpath(selector));
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}

		return element;
	}

	public static WebElement chooseElement(List<WebElement> elements, DecisionType type) {

		WebElement selection = null;

		if (elements.size() > 0) {

			switch (type) {
			case CALCULATED:
				throw new UnsupportedOperationException("Not implemented yet");
				// break;
			case FIRST:
				selection = elements.get(0);
				break;
			case LAST:
				selection = elements.get(elements.size() - 1);
				break;
			case RANDOM:
				Long idx = Math.round(Math.random() * (elements.size() - 1));
				
				selection = elements.get(idx.intValue());				
				break;
			default:
				throw new UnsupportedOperationException("Not implemented yet");
			}
		}
		return selection;
	}
}
