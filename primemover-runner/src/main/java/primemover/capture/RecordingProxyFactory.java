package primemover.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import primemover.model.proxy.IProxy;

import javax.annotation.Nullable;

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

	/**
	 * A started recorder, or an exception and nothing left running.
	 *
	 * <p>
	 * The listener is up before setup is over: the keep-alive ping and the
	 * connectivity check (proxy.pingConnections / proxy.verifyConnections) run on
	 * the started recorder. A setup that throws there hands the caller no recorder
	 * to stop, so it is stopped here -- the same leak as a failed browser start
	 * (TaskOperator#discardRecorder): on ex63 on 2026-10-08 unstopped recorders
	 * had left 1,399 listening sockets and 4,329 threads in the runner JVM.
	 */
	public RecordingProxy createRecordingProxy(@Nullable IProxy chainedProxy) {
		if (!isInit) {
			throw new IllegalStateException("set the appliation context before usage.");
		}
		LOGGER.debug("creating recording proxy and wire it.");

		RecordingProxy ret = new RecordingProxy();
		ctx.getAutowireCapableBeanFactory().autowireBean(ret);

		try {
			ret.setup(chainedProxy);
		} catch (RuntimeException e) {
			try {
				ret.tearDown(true);
			} catch (Exception stop) {
				LOGGER.warn("could not stop a recorder whose setup failed; its listener may stay open", stop);
				e.addSuppressed(stop);
			}
			throw e;
		}

		return ret;
	}
}
