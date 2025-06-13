package primemover.service;

import org.apache.commons.lang3.tuple.Pair;
import primemover.model.Crawler;
import primemover.model.Joblist;

import java.util.List;

public interface ICrawlerService {
	List<Pair<Crawler, Joblist>> getCurrentTasks();
	Boolean writeJobData(Long jobId, String filename, String description, String mimeType, String fileContent);
	Boolean writeS3JobData(Long jobId, String filename, String path, String description);
	List<Pair<Crawler, Joblist>> getTasksForCrawler(Long id);
}