package primemover.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import primemover.anticaptcha.CaptchaSolvingType;
import primemover.api.converter.PrimemoverApiConverter;
import primemover.api.model.CrawlerResult;
import primemover.model.Crawler;
import primemover.model.Joblist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MockCrawlerService implements ICrawlerService {
	
	@Qualifier("ServiceRestTemplate")
	@Autowired
	private RestTemplate restTemplate;
	
	@Value("${anticaptcha.defaultMode:NEVER}")
	private CaptchaSolvingType captchaSolvingDefault;
	

	private String filePath;
	private Boolean writeResult = true;
	
	public MockCrawlerService() {
	}
	
	private ObjectMapper getObjectMapper() {
		List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
		
		for (Object converter : converters) {
			if (converter instanceof MappingJackson2HttpMessageConverter) {
				MappingJackson2HttpMessageConverter jconverter = (MappingJackson2HttpMessageConverter) converter;
				return jconverter.getObjectMapper();
			}
		}
        
		return null; 
	}
	
	public void setMockFile(String filePath) {
		this.filePath = filePath;
	}
	
	public void setMockWriteResult(Boolean result) {
		this.writeResult = result;
	}

	@Override
	public List<Pair<Crawler, Joblist>> getCurrentTasks() {
		
		if (StringUtils.isEmpty(this.filePath)) {
			throw new IllegalStateException("Set filepath first");
		}
		
		try {
			ObjectMapper mapper = getObjectMapper();
			
			String json = new String(Files.readAllBytes(Paths.get(filePath)));
			
			CrawlerResult apiCrawler = mapper.readValue(json, CrawlerResult.class);
			return PrimemoverApiConverter.convertFromApi(apiCrawler, captchaSolvingDefault);
		} catch (JsonMappingException e) {
			throw new IllegalStateException("could not convert input file", e);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("could not convert input file", e);
		} catch (IOException e) {
			throw new IllegalStateException("could not read input file", e);
		}
	}

	@Override
	public Boolean writeJobData(Long jobId, String filename, String description, String mimeType, String fileContent) {
		return writeResult;
	}

	@Override
	public List<Pair<Crawler, Joblist>> getTasksForCrawler(Long id) {
		List<Pair<Crawler, Joblist>> res = getCurrentTasks();
		res.removeIf(p -> p.getLeft().getId() != id);
		return res;
	}

	@Override
	public Boolean writeS3JobData(Long jobId, String filename, String path, String description) {
		return writeResult;
	}
}
