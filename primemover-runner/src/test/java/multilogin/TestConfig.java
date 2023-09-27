package multilogin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.anticaptcha.CaptchaSolverFactory;
import primemover.anticaptcha.service.AntiCaptchaService;
import primemover.capture.ProxyDecoratorFactory;
import primemover.capture.RecordingProxyFactory;
import primemover.multilogin.MultiLoginBrowserProfileFactory;
import primemover.multilogin.MultiLoginDefaultTemplateLoader;
import primemover.runner.TaskOperatorFactory;
import primemover.service.AgentService;
import primemover.service.CrawlerService;
import primemover.service.ICrawlerService;
import primemover.service.JobService;
import primemover.service.OutletService;
import primemover.service.TermsService;
import primemover.service.token.TokenGenerator;
import primemover.service.token.TokenRequestInterceptor;

@SpringBootConfiguration
public class TestConfig {

	@Value("${global.mode}")
	private String mode;

	@Value("${backend.requestTimeoutSeconds:60}")
	private Long requestTimeoutSeconds;

	@Value("${backend.connectionTimeoutSeconds:10}")
	private Long connectionTimeoutSeconds;

	@Bean(name = "AgentService")
	AgentService getAgentService() {
		return new AgentService();
	}

	@Bean(name = "JobService")
	JobService getJobService() {
		return new JobService();
	}

	@Bean("CrawlerService")
	ICrawlerService getCrawlerService() {
		return new CrawlerService();
	}

	@Bean("OutletService")
	OutletService getOutletService() {
		return new OutletService();
	}

	@Bean(name = "CaptchaSolverFactory")
	public CaptchaSolverFactory getCaptchaSolverFactory() {
		return new CaptchaSolverFactory();
	}

	@Bean("TermsService")
	TermsService getTermsService() {
		return new TermsService();
	}

	@Bean(name = "tokenGenerator")
	TokenGenerator getTokenGenerator() {
		return new TokenGenerator();
	}

	@Bean(name = "TokenRequestInterceptor")
	TokenRequestInterceptor requestInterceptor() {
		return new TokenRequestInterceptor();
	}

	@Bean("ServiceRestTemplate")
	public RestTemplate restTemplate(TokenRequestInterceptor requestInterceptor) {
		// In Java code, it is like this:
		// objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		// false);

		RestTemplate restTemplate = new RestTemplateBuilder().build();
		List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();

		for (Object converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				MappingJackson2HttpMessageConverter jconverter = (MappingJackson2HttpMessageConverter) converter;
				ObjectMapper mapper = jconverter.getObjectMapper();
				mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
			}
		}

		List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
		if (CollectionUtils.isEmpty(interceptors)) {
			interceptors = new ArrayList<>();
		}
		interceptors.add(requestInterceptor);
		restTemplate.setInterceptors(interceptors);

		return restTemplate;
	}

	@Bean(name = "RecordingProxyFactory")
	RecordingProxyFactory getRecorderFactory() {
		return new RecordingProxyFactory();
	}

	@Bean(name = "MultiLoginDefaultTemplateLoader", initMethod = "init")
	MultiLoginDefaultTemplateLoader getMultiLoginDefaultTemplateLoader() {
		return MultiLoginDefaultTemplateLoader.getInstance();
	}

	@Bean(name = "MultiLoginBrowserProfileFactory")
	MultiLoginBrowserProfileFactory getMultiLoginBrowserProfileFactory() {
		return new MultiLoginBrowserProfileFactory();
	}

	@Bean(name = "TaskServiceFactory")
	TaskOperatorFactory getTaskServiceFactory() {
		return new TaskOperatorFactory();
	}

	@Bean(name = "AntiCaptchaRestTemplate")
	public RestTemplate antiCaptchaRestTemplate() {
		// In Java code, it is like this:
		// objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		// false);
		RestTemplate restTemplate = new RestTemplateBuilder()
				.setConnectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
				.setReadTimeout(Duration.ofSeconds(requestTimeoutSeconds)).build();

		List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
		for (Object converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				MappingJackson2HttpMessageConverter jconverter = (MappingJackson2HttpMessageConverter) converter;
				ObjectMapper mapper = jconverter.getObjectMapper();
				mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
			}
		}

		return restTemplate;
	}

	@Bean(name = "AntiCaptchaService")
	public AntiCaptchaService getAntiCaptchaService() {
		return new AntiCaptchaService();
	}

	@Bean(name = "ProxyDecoratorFactory")
	public ProxyDecoratorFactory getProxyDecoratorFactory() {
		return new ProxyDecoratorFactory();
	}
}
