package primemover.util;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.capture.RecordingProxy;
import primemover.config.StartupMode;
import primemover.model.Crawler;
import primemover.runner.ITaskOperator;
import primemover.runner.TaskOperator;

public class MultiLoginBrowserProfileFactory implements ApplicationContextAware {
	
	private ApplicationContext ctx;
	private static MultiLoginBrowserProfileFactory instance = new MultiLoginBrowserProfileFactory();
	
	public MulitLoginBrowserProfile getItem(String profileId) {
		MulitLoginBrowserProfile ret = new MulitLoginBrowserProfile(profileId);
		
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		
		return ret;
	}
	
	public MulitLoginBrowserProfile getItem(Crawler crawler, RecordingProxy proxy) {
		MulitLoginBrowserProfile ret = new MulitLoginBrowserProfile(crawler, proxy);
		
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		
		return ret;
	}
	
	public static MultiLoginBrowserProfileFactory getInstance() {
		return instance;
	}


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}
	
	
}
