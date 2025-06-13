package primemover.service;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import primemover.capture.BasicAuthorization;
import primemover.capture.RecordingProxy;

import java.net.Proxy;
import java.util.Optional;

public class IpServiceFactory implements ApplicationContextAware {
	private ApplicationContext ctx;

	public IpServiceFactory() {
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.ctx = applicationContext;
	}

	public IpService getInstance(Proxy proxy, Optional<BasicAuthorization> basicAuth) {
		IpService ret = new IpService();
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		ret.setProxy(proxy, basicAuth);

		return ret;
	}

	public IpService getInstance(RecordingProxy recorder) {
		IpService ret = new IpService();
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);
		recorder.applyProxy(ret);

		return ret;
	}

}
