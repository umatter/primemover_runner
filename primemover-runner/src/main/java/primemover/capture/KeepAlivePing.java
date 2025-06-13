package primemover.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Make a 'ping' to avoid closing of the proxy. We use the UrlVerificator to
 * make connections without actually requesting data. This will simulate a
 * 'ping'.
 * 
 * @author dburkolter
 *
 */
public class KeepAlivePing implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(KeepAlivePing.class);

	private UrlVerificator verificator;
	private int intervalSeconds;
	Timer timer = null;

	public KeepAlivePing(UrlVerificator verificator, int intervalSeconds) {
		super();
		this.verificator = verificator;
		this.intervalSeconds = intervalSeconds;
	}

	/**
	 * Implementing the ping call for the timer
	 *
	 */
	private class pingTask extends TimerTask {
		@Override
		public void run() {
			LOGGER.debug("running ping after {} seconds", intervalSeconds);
			boolean testResult = verificator.runCheck();

			if (testResult) {
				LOGGER.debug("ping was successfull");
			} else {
				LOGGER.warn("ping failed");
			}
		}
	}

	public void start() {
		if (timer != null) {
			LOGGER.error("trying to start already started ping");
			throw new IllegalStateException("Timer already started");
		}

		LOGGER.debug("starting ping every {} seconds", intervalSeconds);
		timer = new Timer();
		timer.scheduleAtFixedRate(new pingTask(), intervalSeconds * 1000, intervalSeconds * 1000);
	}

	@Override
	public void close() throws Exception {
		LOGGER.debug("stopping ping");
		timer.cancel();
		timer = null;
		verificator = null;
	}

}
