package primemover.service;

import java.net.Proxy;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.capture.RecordingProxy;

public class IpServiceFactory implements ApplicationContextAware {
	private ApplicationContext ctx;

	public IpServiceFactory() {
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}

	public IpService getInstance(Proxy proxy) {
		IpService ret = new IpService();
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		ret.setProxy(proxy);

		return ret;
	}

	public IpService getInstance(RecordingProxy recorder) {
		IpService ret = new IpService();
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		recorder.applyProxy(ret);

		return ret;
	}

}
