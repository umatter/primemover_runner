package primemover.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Scrolling {
	
	
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
		
		StringBuilder script = new StringBuilder();
		script.append("let scrollTop = (document.documentElement && document.documentElement.scrollTop) || document.body.scrollTop;");
		script.append("let scrollHeight = (document.documentElement && document.documentElement.scrollHeight) || document.body.scrollHeight;");
		script.append("let scrolledToBottom = (scrollTop + window.innerHeight) >= scrollHeight;");
		script.append("return scrolledToBottom");
		Boolean res = (Boolean) jse.executeScript(script.toString());
		
		return res;
	}
	
	public static Boolean isScrolledToTop(WebDriver driver) {
		JavascriptExecutor jse = (JavascriptExecutor)driver;
		
		StringBuilder script = new StringBuilder();
		script.append("let scrollPos = window.scrollY || window.scrollTop || document.getElementsByTagName(\"html\")[0].scrollTop;");
		script.append("return scrollPos = 0;");
		Boolean res = (Boolean) jse.executeScript(script.toString());
		
		return res;
	}

	public static void scrollDownByJavascript(WebDriver driver, Integer length) {
		JavascriptExecutor jse = (JavascriptExecutor)driver;
   	 
		StringBuilder script = new StringBuilder();
		
		script.append("let scrollPos = window.scrollY || window.scrollTop || document.getElementsByTagName(\"html\")[0].scrollTop;");
		script.append("let scrollHeight = (document.documentElement && document.documentElement.scrollHeight) || document.body.scrollHeight;");
		script.append("let opt = {");
		script.append("left: 0,");
		script.append("top: scrollPos + scrollHeight / 100 * " + length + ",");
		script.append("behaviour: 'smooth'");
		script.append("};");
		script.append("window.scrollTo(opt);"); // TODO: call to prototype?
		script.append("return true;");
		
		Boolean res = (Boolean) jse.executeScript(script.toString());
		System.out.println("Scrolling down with result: " + res);
	}
	
	public static void scrollDownForDurationByJavascript(WebDriver driver, Integer seconds) {
		JavascriptExecutor jse = (JavascriptExecutor) driver;
		
		
	}
	
	public static void scrollUpForDurationByJavascript(WebDriver driver, Integer seconds) {
		JavascriptExecutor jse = (JavascriptExecutor) driver;
		
		
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
		script.append("behaviour: 'smooth'");
		script.append("};");
		script.append("window.scrollTo(opt);"); // TODO: call to prototype?
		script.append("return true;");
		
		Boolean res = (Boolean) jse.executeScript(script.toString());
		
		System.out.println("Scrolling up with result: " + res);
	}
}
