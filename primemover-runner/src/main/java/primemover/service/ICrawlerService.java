package primemover.service;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import primemover.model.Crawler;
import primemover.model.Joblist;

public interface ICrawlerService {

	List<Pair<Crawler, Joblist>> getCurrentTasks();
	Boolean writeJobData(Long jobId, String filename, String description, String mimeType, String fileContent);
	List<Pair<Crawler, Joblist>> getTasksForCrawler(Long id);

}