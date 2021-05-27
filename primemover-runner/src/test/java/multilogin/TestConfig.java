package multilogin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import primemover.captcha.CaptchaSolver;
import primemover.capture.RecordingProxyFactory;
import primemover.runner.TaskOperatorFactory;
import primemover.service.AgentService;
import primemover.service.CrawlerService;
import primemover.service.ICrawlerService;
import primemover.service.OutletService;
import primemover.service.TermsService;
import primemover.util.MultiLoginBrowserProfileFactory;
import primemover.util.MultiLoginDefaultTemplateLoader;

@SpringBootConfiguration
public class TestConfig {
	
	@Value("${global.mode}")
	private String mode;
	
	@Bean(name="AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}
	
	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
	}
	
	@Bean("OutletService")
	OutletService getOutletService() {
		return new OutletService();
	}
	
	@Bean(name="CaptchaSolver")
	public CaptchaSolver getCaptchaSolver() {
		return new CaptchaSolver();
	}
	
	@Bean("TermsService")
	TermsService getTermsService() {
		return new TermsService();
	}
	
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplateBuilder().build();
	}
	
	@Bean(name="RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}	
	
	@Bean(name="MultiLoginDefaultTemplateLoader", initMethod="init")
	MultiLoginDefaultTemplateLoader getMultiLoginDefaultTemplateLoader() {
		return MultiLoginDefaultTemplateLoader.getInstance();
	}
	
	@Bean(name="MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}

	@Bean(name="TaskServiceFactory")
	TaskOperatorFactory getTaskServiceFactory() {
		return new TaskOperatorFactory();
	}
}
