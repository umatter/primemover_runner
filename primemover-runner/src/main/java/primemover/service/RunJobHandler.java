package primemover.service;

import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.WebDriver;

import primemover.model.IJob;
import primemover.model.VisitJob;

public class RunJobHandler {

	// Interface for our IJob-Handlers
	private interface Handler {
	 void handle(WebDriver driver, IJob job);
	}
	
	// Handler registration
	@SuppressWarnings("rawtypes")
	private static final Map<Class,Handler> dispatch = new HashMap<Class,Handler>();
	
	// Populate handlers
	static {
	 dispatch.put(VisitJob.class, new Handler() {
	     public void handle(WebDriver driver, IJob job) {
	    	 VisitJob typedJob = (VisitJob) job;
	    	 driver.get(typedJob.getUrl());
	    	 selenium.util.Waiter.waitUntilJSReady(driver, 60);
	     }
	 });
	}
	
	// Call the job specific handler 
	public static void handle(WebDriver driver, IJob job) {
	 Handler h = dispatch.get(job.getClass());
	 if (h == null) {
		 throw new UnsupportedOperationException("This type is not yet supported");
	 }
	 h.handle(driver, job); // <<== Here is the magic
	}
}



