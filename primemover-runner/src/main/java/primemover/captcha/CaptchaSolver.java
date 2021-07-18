package primemover.captcha;

import java.net.MalformedURLException;
import java.net.URL;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import com.anti_captcha.api.NoCaptchaProxyless;

import primemover.model.IJob;
import primemover.runner.OperatorContext;

public class CaptchaSolver {
	private static Logger logger = LoggerFactory.getLogger(CaptchaSolver.class);
	
	public CaptchaSolver() {
	}
	
	@Value("${anticaptcha.api.key}")
	private String apiKey;
	
	@Value("${anticaptcha.api.solvingTimeoutSeconds:300}")
	private Integer maxSolvingTimeoutSeconds = 300;
	
	private CaptchaSolverResult trySolveGoogleRecaptchaV2(OperatorContext context, IJob job, Boolean submit) {
		WebDriver driver = context.getDriver();
		logger.debug("Start solving captcha for job {}", job.getId());
		
		try {
			// check for type
			WebElement elem = driver.findElement(By.className("g-recaptcha"));
			if (elem == null) {
				return CaptchaSolverResult.NOTFOUND;
			}

			// Start solving
			try {
				String siteKey = elem.getAttribute("data-sitekey");
				String siteSToken = elem.getAttribute("data-stoken");
				
				NoCaptchaProxyless client = new NoCaptchaProxyless();
				client.setClientKey(apiKey);
				client.setWebsiteUrl(new URL(driver.getCurrentUrl()));
				client.setWebsiteKey(siteKey);
				
				if (StringUtils.hasText(siteSToken)) {
					client.setWebsiteSToken(siteSToken);
				}
				
				if(!client.createTask()) {
					logger.warn("failed to create anti-captcha task for job {}", job.getId());
					return CaptchaSolverResult.ERROR;
				}
				
				if (!client.waitForResult(maxSolvingTimeoutSeconds)) {
					logger.warn("timout solving captcha for job {}", job.getId());
					return CaptchaSolverResult.ERROR;
				}
				
				String response = client.getTaskSolution().getGRecaptchaResponse();
				
				// write respone in Javascript as target is hidden!
				JavascriptExecutor jse = (JavascriptExecutor) driver;
				jse.executeScript("document.getElementById(\"g-recaptcha-response\").innerHTML = \"" + response + "\";");
				
				// wait 1 Second for any script to finish
				Thread.sleep(1000l);
				
				if (submit) {
					// Kaskadierend Varianten ausprobieren
					boolean submitDone = false;
					try {
						WebElement elemSubmit = driver.findElement(By.xpath("//*[@type=\"submit\"]"));
						elemSubmit.click();
						submitDone = true;
						logger.debug("submitted form via submit-button (job {})", job.getId());
					} catch (Exception e) {
						logger.debug("nothing found to submit via submit-button (job {})", job.getId());
						// Silently ignoring the not existing submit button
					}
					
					if (!submitDone) {
						try {

							WebElement captchaForm = driver.findElement(By.className("captcha-form"));
							captchaForm.submit();
							submitDone = true;
							logger.debug("submitted form via form-element (job {})", job.getId());
						} catch (NoSuchElementException e) {
							logger.debug("nothing found to submit via form-element(job {})", job.getId());
							// Silently ignoring the not existing submit on the form
						}
					}
				}
				
				return CaptchaSolverResult.SUCCESS;
			} catch (MalformedURLException e) {
				logger.warn("invalid URL {} for anti-captcha for job {}", driver.getCurrentUrl(), job.getId(), e);
				return CaptchaSolverResult.ERROR;
			} catch (InterruptedException e) {
				logger.warn("Exception waiting for response for job {}", job.getId(), e);
				return CaptchaSolverResult.ERROR;
			} catch (Exception e) {
				logger.warn("Exception solving captcha for job {}", job.getId(), e);
				return CaptchaSolverResult.ERROR;
			}
			
		} catch (NoSuchElementException e) {
			// nothing found
			logger.debug("no captcha found for job {}", job.getId());
		}
		
		return CaptchaSolverResult.NOTFOUND;
	}
	
	public CaptchaSolverResult solve(OperatorContext context, IJob job, Boolean submit) {
		
		CaptchaSolverResult tmp = trySolveGoogleRecaptchaV2(context, job, submit);
		if (!(tmp == CaptchaSolverResult.NOTFOUND)) {
			return tmp;
		}
		
		// next variant here
		
		return CaptchaSolverResult.NOTFOUND;
	}
}
