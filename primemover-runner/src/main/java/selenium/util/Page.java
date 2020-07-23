package selenium.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

public class Page {

	public static String getPageSourceFromDriver(WebDriver driver) {
		return driver.getPageSource();
	}
	
	public static String getPageSourceFromJavascript(WebDriver driver) {
		return ((JavascriptExecutor) driver).executeScript("return document.documentElement.outerHTML;").toString();
	}
	
	public static String getBodyeFromJavascript(WebDriver driver) {
		return ((JavascriptExecutor) driver).executeScript("return document.body.outerHTML;").toString();
	}
	
	
	public static Boolean isPageReady(WebDriver driver) {
		return ((JavascriptExecutor) driver).executeScript("return document.readyState").toString().equals("complete");
	}
	
	
	
	/*
	# HTML from element with some JavaScript
	element = driver.find_element_by_css_selector("#hireme")
	html = driver.execute_script("return arguments[0].outerHTML;", element)

	# HTML from element with `get_attribute`
	element = driver.find_element_by_css_selector("#hireme")
	html = element.get_attribute('outerHTML')

	To get the inner HTML (tag excluded):

	# HTML from `<html>`
	html = driver.execute_script("return document.documentElement.innerHTML;")

	# HTML from `<body>`
	html = driver.execute_script("return document.body.innerHTML;")

	# HTML from element with some JavaScript
	element = driver.find_element_by_css_selector("#hireme")
	html = driver.execute_script("return arguments[0].innerHTML;", element)

	# HTML from element with `get_attribute`
	element = driver.find_element_by_css_selector("#hireme")
	html = element.get_attribute('innerHTML')
	
	*/
}
