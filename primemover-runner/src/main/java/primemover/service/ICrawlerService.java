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

	/**
	 * Video ids this crawler has already chosen, or NULL when we cannot tell.
	 *
	 * Null and an empty list are different answers and must stay different. Null
	 * means the history is unknown -- the API could not be reached, or answered
	 * 409 because the column does not exist -- and the decision function then
	 * reports already_watched as not_implemented and filters nothing, which is
	 * exactly what it did before this existed. An EMPTY list is the positive
	 * answer "this bot has watched nothing", and counts as the filter applying.
	 *
	 * Returning an empty list on failure would report a filter as applied while
	 * filtering nothing, which is the silent-success shape the degradation
	 * report exists to prevent.
	 */
	List<String> getWatchedVideoIds(Long crawlerId);
}