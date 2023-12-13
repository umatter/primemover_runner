package primemover.capture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UrlVerificator {

	private static final Logger LOGGER = LoggerFactory.getLogger(UrlVerificator.class);

	private List<String> verificationUrls;
	private int timeoutMillis;
	private Proxy proxy;

	public UrlVerificator(Proxy proxy, List<String> verificationUrls, int timeoutMillis) {
		this.proxy = proxy;
		this.timeoutMillis = timeoutMillis;
		this.verificationUrls = verificationUrls;
	}

	private void readData(HttpURLConnection connection) throws IOException {

		StringBuilder builder = new StringBuilder();
		builder.append(connection.getResponseCode()).append(" ").append(connection.getResponseMessage()).append("\n");

		Map<String, List<String>> map = connection.getHeaderFields();
		for (Map.Entry<String, List<String>> entry : map.entrySet()) {
			if (entry.getKey() == null)
				continue;
			builder.append(entry.getKey()).append(": ");

			List<String> headerValues = entry.getValue();
			Iterator<String> it = headerValues.iterator();
			if (it.hasNext()) {
				builder.append(it.next());

				while (it.hasNext()) {
					builder.append(", ").append(it.next());
				}
			}

			builder.append("\n");
		}

		builder.append("content:\n");
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		String line = null;
		while ((line = reader.readLine()) != null) {
			builder.append(line);
		}

		LOGGER.debug("verification response:\n{}", builder);
	}

	private boolean verifyUrl(String strUrl) {
		LOGGER.debug("Start to verify {}", strUrl);

		try {
			URL url = new URL(strUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(timeoutMillis);
			connection.connect();
			if (LOGGER.isDebugEnabled()) {
				this.readData(connection);
			}
			connection.disconnect();

			LOGGER.debug("Successfully verified {}", strUrl);
			return true;
		} catch (MalformedURLException e) {
			LOGGER.warn("Error verifying HTTP connection with invalid URL {}", strUrl, e);
			return false;
		} catch (SocketTimeoutException e) {
			LOGGER.warn("timeout connecting to {}", strUrl, e);
			return false;
		} catch (IOException e) {
			LOGGER.warn("Error verifying HTTP connection with URL {}", strUrl, e);
			return false;
		}
	}

	public boolean runCheck() {
		if (proxy == null) {
			LOGGER.warn("no proxy defined");
			return false;
		}

		if (verificationUrls == null || verificationUrls.size() == 0) {
			LOGGER.warn("no verification urls defined");
			return true;
		}

		LOGGER.debug("verifying urls");

		// check all Urls in parallel
		boolean combinedResult = verificationUrls.parallelStream().map((url) -> {
			return verifyUrl(url);
		}).reduce(true, (left, right) -> {
			return left && right;
		});

		LOGGER.debug("verification result: {} (true: success, false: failure)", combinedResult);
		return combinedResult;
	}
}
