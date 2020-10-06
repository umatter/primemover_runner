package hsg.bot.e2e;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.Joblist;
import primemover.model.Crawler;
import primemover.runner.ITaskOperator;
import primemover.runner.MockJobManager;
import primemover.runner.TaskOperatorFactory;
import primemover.service.ICrawlerService;

@SpringBootTest
@AutoConfigureAfter(JacksonAutoConfiguration.class)
class LoadTasksFromServiceAndRunJobs {
	
	@Autowired
	private TaskOperatorFactory operatorFactory;
	
	@Autowired
	private ICrawlerService crawlerService;
	
	
	@Autowired
	private MockJobManager jobManager;
	
	@BeforeEach
	public void setup() {
		
	}

	@Test
	void LoadTasksAndWriteJobDataForCrawlerId1() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getTasksForCrawler(1l);
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = operatorFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	@Test
	void LoadTasksAndWriteJobDataForCrawlerId2() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getTasksForCrawler(2l);
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = operatorFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	@Test
	void LoadTasksAndWriteJobDataForCrawlerId3() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getTasksForCrawler(3l);
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = operatorFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	@Test
	void LoadTasksAndWriteJobDataForCrawlerId4() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getTasksForCrawler(4l);
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = operatorFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	@Test
	void LoadTasksAndWriteJobDataForCrawlerId5() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getTasksForCrawler(5l);
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = operatorFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	@Test
	void LoadTasksAndWriteJobDataForCrawlerId6() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getTasksForCrawler(6l);
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = operatorFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	/*
	@Test
	void LoadTasksAndWriteJobDataForCrawlerId98() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getTasksForCrawler(112l);
		
		if (taskList == null) {
			throw new IllegalStateException("Crawler not found");
		}
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			ITaskOperator service = operatorFactory.getItem();
			service.setup(task.getLeft());
			service.runJobList(task.getValue());
			service.tearDown();
		}
	}
	
	*/
	
	@Test
	void LoadTasksAndRunWithJobManager() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getCurrentTasks();
		
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			jobManager.addTask(task.getKey(), task.getRight());
		}
		
		jobManager.run();
		
	}
	
	
	@AfterEach
    public void tearDown() {
        
    }

}
