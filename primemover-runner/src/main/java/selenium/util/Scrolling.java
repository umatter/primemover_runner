package selenium.util;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class Scrolling {

	public static void scrollDownByJavascript(WebDriver driver, Integer length) {
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
		script.append("top: scrollPos + scrollHeight / 100 * " + length + ",");
		script.append("behaviour: 'smooth'");
		script.append("};");
		script.append("window.scrollTo(opt);"); // TODO: call to prototype?
		script.append("return true;");
		
		Boolean res = (Boolean) jse.executeScript(script.toString(), 10);
		System.out.println("Scrolling down with result: " + res);
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
		
		Boolean res = (Boolean) jse.executeScript(script.toString(), 10);
		
		System.out.println("Scrolling up with result: " + res);
	}
}
