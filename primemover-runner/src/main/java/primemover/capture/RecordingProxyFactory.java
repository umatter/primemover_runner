package primemover.capture;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.model.proxy.IProxy;

public class RecordingProxyFactory implements ApplicationContextAware {
	private static final Logger LOGGER = LoggerFactory.getLogger(RecordingProxyFactory.class);

	private ApplicationContext ctx;
	private boolean isInit = false;

	public RecordingProxyFactory() {
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		LOGGER.debug("setting application context");
		this.ctx = applicationContext;

		isInit = true;
	}

	public RecordingProxy createRecordingProxy(@Nullable IProxy chainedProxy) {
		if (!isInit) {
			throw new IllegalStateException("set the appliation context before usage.");
		}
		LOGGER.debug("creating recording proxy and wire it.");

		RecordingProxy ret = new RecordingProxy();
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);

		if (chainedProxy == null) {
			ret.setup(null);
		} else {
			ret.setup(chainedProxy);
		}

		return ret;
	}
}
