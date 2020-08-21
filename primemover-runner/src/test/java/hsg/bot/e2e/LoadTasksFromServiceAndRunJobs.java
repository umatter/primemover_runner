package hsg.bot.e2e;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.model.Joblist;
import primemover.model.Crawler;
import primemover.runner.ITaskOperator;
import primemover.runner.MockJobManager;
import primemover.runner.TaskOperatorFactory;
import primemover.service.ICrawlerService;

@SpringBootTest
class LoadTasksFromServiceAndRunJobs {
	
	@Autowired
	private TaskOperatorFactory operatorFactory;
	
	@Autowired
	private MockJobManager jobManager;
	
	@Autowired
	private ICrawlerService crawlerService;
	
	@BeforeEach
	public void setup() {
		
	}

	@Test
	void LoadTasksAndWriteJobData() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getCurrentTasks();
		
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
	void LoadTasksAndRunWithJobManager() {
		// get the jobList for each Crawler
		List<Pair<Crawler, Joblist>> taskList = crawlerService.getCurrentTasks();
		
		// Startzeiten setzen
		
		
		// run the tasks
		for (Pair<Crawler, Joblist> task : taskList) {
			jobManager.addTask(task.getKey(), task.getRight());
		}
		
		jobManager.run();
		
		// Missing wait...
	}
	*/
	
	@AfterEach
    public void tearDown() {
        
    }

}
