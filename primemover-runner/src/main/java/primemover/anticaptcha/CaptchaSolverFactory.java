package primemover.anticaptcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class CaptchaSolverFactory implements ApplicationContextAware {
	private static final Logger LOGGER = LoggerFactory.getLogger(CaptchaSolverFactory.class);

	private ApplicationContext ctx;
	private boolean isInit = false;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		LOGGER.debug("setting application context");
		this.ctx = applicationContext;

		isInit = true;
	}

	public ICaptchaSolver getCaptchaSolver() {
		if (!isInit) {
			throw new IllegalStateException("set the appliation context before usage.");
		}
		LOGGER.debug("creating captcha solver and wire it.");

		PluginCaptchaSolver item = new PluginCaptchaSolver();
		ctx.getAutowireCapableBeanFactory().autowireBean(item);

		return item;
	}

}
