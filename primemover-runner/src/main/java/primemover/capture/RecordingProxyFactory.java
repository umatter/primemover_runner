package primemover.capture;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.config.StartupMode;

public class RecordingProxyFactory implements ApplicationContextAware {
	
	@Value("${global.mode}")
	private  StartupMode mode;
	private ApplicationContext ctx;
		
	public RecordingProxyFactory() {
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}
	
	
	public RecordingProxy createRecordingProxy() {
		RecordingProxy ret = new RecordingProxy();
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		
		return ret;
	}
	
}
