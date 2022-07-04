package primemover.runner.joblist;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.config.StartupMode;
import primemover.model.Crawler;
import primemover.model.Joblist;
import primemover.runner.TaskOperatorFactory;

public class JobListRunnerFactory  implements ApplicationContextAware {

	@Value("${global.mode}")
	private  StartupMode mode;
	private ApplicationContext ctx;
	
	@Autowired
	TaskOperatorFactory taskOperatorFactory;
	
	public JobListRunnerFactory() {
	}
	
	public JobListRunner createJobListRunner(Crawler crawler, Joblist jobs) {
		JobListRunner ret = new JobListRunner(taskOperatorFactory.getItem(), crawler, jobs);
		
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		
		return ret;
	}
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}
	

}
