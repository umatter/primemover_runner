package primemover.service.token;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class TokenRequestInterceptor implements ClientHttpRequestInterceptor {

	@Autowired
	TokenGenerator tokenGenerator;

	public TokenRequestInterceptor() {
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		String curToken = tokenGenerator.getToken();
		request.getHeaders().add("Authorization", "Bearer " + curToken);
		return execution.execute(request, body);
	}
}
