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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import primemover.util.SelectingHelper;

import com.anti_captcha.api.NoCaptchaProxyless;
import com.anti_captcha.api.response.TaskResultResponse.SolutionData;

import primemover.model.IJob;
import primemover.model.SelectorType;
import primemover.runner.OperatorContext;
import primemover.service.anticaptcha.AntiCaptchaService;
import primemover.service.anticaptcha.SolvingResponse;
import primemover.service.anticaptcha.SolvingSolution;


// find the captcha: https://gist.github.com/2captcha/2ee70fa1130e756e1693a5d4be4d8c70


public class CaptchaSolver {
	private static Logger logger = LoggerFactory.getLogger(CaptchaSolver.class);
	
	public CaptchaSolver() {
	}
	
	@Value("${anticaptcha.api.key}")
	private String apiKey;
	
	@Value("${anticaptcha.api.solvingTimeoutSeconds:120}")
	private Integer maxSolvingTimeoutSeconds = 120;
	
	@Autowired
	private AntiCaptchaService captchaService;
	
	private CaptchaSolverResult trySolveGoogleRecaptchaV2(OperatorContext context, IJob job, Boolean submit) {
		WebDriver driver = context.getDriver();
		logger.debug("Start solving captcha for job {}", job.getId());
		
		try {
			// check for captcha element
			WebElement elem = null;
			try {
				elem = SelectingHelper.getElementByWebdriver(driver, SelectorType.ID, "recaptcha");
			} catch (Exception e) {
				return CaptchaSolverResult.NOTFOUND;
			}					
			if (elem == null) {
				return CaptchaSolverResult.NOTFOUND;
			}
			

			// Start solving
			try {
				String websiteUrl = driver.getCurrentUrl();
				String siteKey = elem.getAttribute("data-sitekey");
				String siteSToken = elem.getAttribute("data-s");
				String submitCallback = elem.getAttribute("data-callback");
				
				logger.debug("collected data: sitekey: {}, s: {}, callback: {}", siteKey, siteSToken, submitCallback);
				
				ResponseEntity<SolvingResponse> response = null;
				try {
					
					response = captchaService.solveRecaptchaV2TaskProxyless(websiteUrl, siteKey, siteSToken, true);
					
				} catch (Exception e) {
					logger.warn("error solving captcha for job {}", job.getId(), e);
					return CaptchaSolverResult.ERROR;
				}
				
				if (response == null) {
					return CaptchaSolverResult.ERROR;
				}
				
				SolvingSolution solution = response.getBody().getSolution(); 			
				String responseToken = solution.getgRecaptchaResponse();
				logger.debug("captcha result for job {}", job.getId(), solution);
				
				if (solution.getCookies() != null) {
					for (String item : solution.getCookies()) {
						// driver.manage().addCookie(cookie);
					}
				}
				
				// write respone in Javascript as target is hidden!
				JavascriptExecutor jse = (JavascriptExecutor) driver;
				//jse.executeScript("var element=document.getElementById(\"g-recaptcha-response\"); element.style.display=\"\";");
				jse.executeScript("document.getElementById(\"g-recaptcha-response\").innerHTML = \"" + responseToken + "\";");
				
				// wait for any script to finish
				Thread.sleep(500l);
				
				// Kaskadierend Varianten ausprobieren
				boolean submitDone = false;
				
				// submit callback)
				if (StringUtils.hasText(submitCallback)) {
					try {
						String script = submitCallback + "(\"" + responseToken + "\");";
						logger.debug("trying submit callback via script '" + script + "' for job {}", job.getId());
						jse.executeScript(script);
						logger.debug("submit done for job {})", job.getId());
						submitDone = true;
					} catch (Exception e) {
						logger.debug("submit callback via script not working (job {})", job.getId(), e);
						// Silently ignoring the not existing submit button
					}
				}
				// submit button
				if (!submitDone) {
					try {
						WebElement elemSubmit = driver.findElement(By.xpath("//*[@type=\"submit\"]"));
						elemSubmit.click();
						submitDone = true;
						logger.debug("submitted form via submit-button (job {})", job.getId());
					} catch (Exception e) {
						logger.debug("nothing found to submit via submit-button (job {})", job.getId());
						// Silently ignoring the not existing submit button
					}
				}
				
				if (!submitDone) {
					try {
						WebElement captchaForm = driver.findElement(By.id("captcha-form"));
						captchaForm.submit();
						submitDone = true;
						logger.debug("submitted form via form-element (job {})", job.getId());
					} catch (NoSuchElementException e) {
						logger.debug("nothing found to submit via form-element(job {})", job.getId());
						// Silently ignoring the not existing submit on the form
					}
				}
				
				if (submitDone) {
					return CaptchaSolverResult.SUCCESS;
				} else {
					return CaptchaSolverResult.ERROR;
				}
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
