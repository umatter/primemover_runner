package primemover.runner.jobHandling.jobHandlers;

import primemover.model.IJob;
import primemover.model.geosurf.IpInfo;
import primemover.model.jobs.AssertProxyByGeosurfJob;
import primemover.runner.OperatorContext;
import primemover.runner.jobHandling.IJobHandler;
import org.springframework.util.StringUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssertProxyByGeosurfJobHandler implements IJobHandler {

	private static Logger logger = LoggerFactory.getLogger(AssertProxyByGeosurfJobHandler.class);

    private ObjectMapper mapper = new ObjectMapper();
    
    public void handle(OperatorContext context, IJob job) {
        logger.debug("running job");

        WebDriver driver = context.getDriver();
        AssertProxyByGeosurfJob typedJob = (AssertProxyByGeosurfJob) job;
        
        // get IP-Info from geosurf
        driver.get("https://geo.geosurf.io/");
                        
        // JSON is displayed within a pre-Tag in Chrome...
        String json = driver.findElement(By.tagName("pre")).getText();
        
        // map object
        try {
            IpInfo ipInfo = mapper.readValue(json, IpInfo.class);
            
            // verify
            if (StringUtils.hasText(typedJob.getIp())) {
                assertEquals(typedJob.getIp(), ipInfo.getIp());
            }
            if (StringUtils.hasText(typedJob.getCountry())) {
                assertEquals(typedJob.getCountry(), ipInfo.getCountry());
            }
            if (StringUtils.hasText(typedJob.getCity())) {
                assertEquals(typedJob.getCity(), ipInfo.getCity());
            }
            
        } catch (JsonMappingException e) {
            throw new AssertionError("Error mapping json", e);
        } catch (JsonProcessingException e) {
            throw new AssertionError("Error processing json", e);
        }
    }
}
