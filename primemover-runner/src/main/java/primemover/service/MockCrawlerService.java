package primemover.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import primemover.converter.PrimemoverApiConverter;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.model.api.CrawlerResult;

public class MockCrawlerService implements ICrawlerService {
	
	private ObjectMapper mapper = new ObjectMapper();
	private String filePath;
	private Boolean writeResult = true;
	
	public MockCrawlerService() {
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
			String json = new String(Files.readAllBytes(Paths.get(filePath)));
			
			CrawlerResult apiCrawler = mapper.readValue(json, CrawlerResult.class);
			return PrimemoverApiConverter.convertFromApi(apiCrawler);
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
		return getCurrentTasks();
	}

}
