package primemover.anticaptcha;

import java.util.concurrent.ArrayBlockingQueue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.Range;

import primemover.model.proxy.IProxy;

public class FeedbackProxyFactory {
	private @Nonnull Range<Integer> portRange;
	private @Nonnull ArrayBlockingQueue<Integer> stack;

	public FeedbackProxyFactory(int rangeStart, int rangeEnd) throws InterruptedException {
		portRange = Range.between(rangeStart, rangeEnd);
		stack = new ArrayBlockingQueue<Integer>(rangeEnd - rangeStart + 1, true);
		for (int i = rangeStart; i <= rangeEnd; i++) {
			stack.put(i);
		}
	}

	public @Nullable FeedbackProxy createProxy(@Nonnull IProxy target) throws InterruptedException {
		if (target == null) {
			throw new IllegalArgumentException("target proxy must not be null");
		}

		Integer curPort = stack.take();

		if (curPort == null) {
			return null;
		}

		return new FeedbackProxy(this, curPort, target);
	}

	public void releasePort(FeedbackProxy proxy) throws InterruptedException {
		stack.put(proxy.getPort());
	}
}
