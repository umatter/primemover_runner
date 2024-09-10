package primemover.multilogin;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.InvalidArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.MultiLoginAgent;

public class MultiLoginBrowserProfileFactory implements ApplicationContextAware {

	private static Logger logger = LoggerFactory.getLogger(MulitLoginBrowserProfile.class);

	private ApplicationContext ctx;
	private static MultiLoginBrowserProfileFactory instance = new MultiLoginBrowserProfileFactory();

	public MulitLoginBrowserProfile getItem(String profileId) {
		MulitLoginBrowserProfile ret = new MulitLoginBrowserProfile(profileId);

		ctx.getAutowireCapableBeanFactory().autowireBean(ret);

		return ret;
	}

	public MulitLoginBrowserProfile getItem(Crawler crawler, RecordingProxy proxy) {
		// verification and casting
		IAgent agent = crawler.getAgent();
		if (!(agent instanceof MultiLoginAgent)) {
			throw new InvalidArgumentException("Only multilogin-agents are supported");
		}

		MultiLoginAgent typedAgent = (MultiLoginAgent) agent;
		String profileId = typedAgent.getMultiLoginId();

		if (!StringUtils.isEmpty(profileId)) {
			return this.getItem(profileId);
		}

		logger.info("creating new multilogin-profile for agent {}", agent.getId());

		// starting up helper class
		MulitLoginBrowserProfile ret = new MulitLoginBrowserProfile();

		// need to wire before doing any further!
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);

		ret.createNewProfile(typedAgent, proxy);

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
