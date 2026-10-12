package primemover.anticaptcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Hands each session a fresh, wired captcha solver.
 *
 * <p>
 * {@code anticaptcha.solver} picks which: {@code api} (the default) solves over
 * Anti-Captcha's HTTP API and works on any browser, Multilogin X included;
 * {@code plugin} installs the Anti-Captcha browser extension, which works only
 * on plain Chrome/Firefox and Multilogin 6 -- on an MLX profile it installs
 * nothing and solves nothing. Any other value stops the runner at startup.
 */
public class CaptchaSolverFactory implements ApplicationContextAware {
	private static final Logger LOGGER = LoggerFactory.getLogger(CaptchaSolverFactory.class);

	@Value("${anticaptcha.solver:api}")
	private String solver = "api";

	private ApplicationContext ctx;
	private boolean isInit = false;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		LOGGER.debug("setting application context");
		validate();
		LOGGER.info("captcha solver: {}", solver.strip());
		this.ctx = applicationContext;

		isInit = true;
	}

	/** Refuse a solver name that is neither {@code api} nor {@code plugin}. */
	void validate() {
		if (!isApi() && !"plugin".equalsIgnoreCase(solver.strip())) {
			throw new IllegalStateException("anticaptcha.solver must be 'api' or 'plugin', not '" + solver + "'");
		}
	}

	private boolean isApi() {
		return "api".equalsIgnoreCase(solver.strip());
	}

	/** The configured solver, not yet wired. */
	ICaptchaSolver newSolver() {
		return isApi() ? new ApiCaptchaSolver() : new PluginCaptchaSolver();
	}

	public ICaptchaSolver getCaptchaSolver() {
		if (!isInit) {
			throw new IllegalStateException("set the appliation context before usage.");
		}
		LOGGER.debug("creating captcha solver and wire it.");

		ICaptchaSolver item = newSolver();
		ctx.getAutowireCapableBeanFactory().autowireBean(item);

		return item;
	}

}
