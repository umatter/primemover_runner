package primemover.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.runner.joblist.JobListRunner;

public class MockTaskManager implements ITaskManager {
	private static Logger logger = LoggerFactory.getLogger(MockTaskManager.class);

	private Integer maxThreads = 2; // maximum parallel executions of joblists
	
	private ExecutorService executorService;
	
	private List<FutureTask<Boolean>> tasks = new ArrayList<FutureTask<Boolean>>();

	@Autowired
	TaskOperatorFactory taskServiceFactory;
	
	public MockTaskManager() {
	}

	@Override
	public void setup() {
		executorService = Executors.newFixedThreadPool(maxThreads);
	}
	

	public void addTask (Crawler crawler, Joblist jobs) {
		JobListRunner runner = new JobListRunner(taskServiceFactory.getItem(), crawler, jobs);
		FutureTask<Boolean> fut = new FutureTask<Boolean>(runner);
		tasks.add(fut);
	}
	
	@Override
	public void run() {
		for (FutureTask<Boolean> task : tasks) {
			executorService.execute(task);
		}
		
		for (FutureTask<Boolean> task : tasks) {
			try {
				task.get();
			} catch (InterruptedException e) {
				logger.warn("Timeout running tasklist.", e);
			} catch (ExecutionException e) {
				logger.warn("Catched Exception running tasklist.", e);
			}
		}
	}

}
