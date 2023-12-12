package primemover.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.model.proxy.IProxy;

public class ProxyDecoratorFactory implements ApplicationContextAware {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyDecoratorFactory.class);

	private ApplicationContext ctx;
	private boolean isInit = false;

	public ProxyDecoratorFactory() {
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		LOGGER.debug("setting application context");
		this.ctx = applicationContext;

		isInit = true;
	}

	public SessionDecorator decorateProxy(IProxy proxy) {
		if (!isInit) {
			throw new IllegalStateException("set the appliation context before usage.");
		}
		LOGGER.debug("decorating Proxy - {}", proxy);

		SessionDecorator decorated = new SessionDecorator(proxy);
		ctx.getAutowireCapableBeanFactory().autowireBean(decorated);
		return decorated;
	}
}
