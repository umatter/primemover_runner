package MinimumWorkingExample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.Proxy.ProxyType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.mitm.RootCertificateGenerator;
import com.browserup.bup.mitm.keys.ECKeyGenerator;
import com.browserup.bup.mitm.keys.RSAKeyGenerator;
import com.browserup.bup.mitm.manager.ImpersonatingMitmManager;
import com.browserup.bup.proxy.dns.NativeResolver;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

public class MinimumWorkingExample {

	private static String profileId = "9405f040-6e2e-4f4d-b013-399262270aff";

	public MinimumWorkingExample() {
		// TODO Auto-generated constructor stub
	}

	public void run() throws IOException, JSONException, InterruptedException {

		// create a RootCertificateGenerator that generates EC Certificate Authorities;
		// you may also load your
		// own EC certificate and private key using any other CertificateAndKeySource
		// implementation
		// (KeyStoreFileCertificateSource, PemFileCertificateSource, etc.).
		RootCertificateGenerator rootCertificateGenerator = RootCertificateGenerator.builder()
				.keyGenerator(new ECKeyGenerator()).build();

		// save the newly-generated Root Certificate and Private Key -- the .cer file
		// can be imported
		// directly into a browser
		// rootCertificateGenerator.saveRootCertificateAsPemFile(new
		// File("out/certificate.cer"));
		// rootCertificateGenerator.savePrivateKeyAsPemFile(new
		// File("out/private-key.pem"), "CHANGEME-password");

		// or save the certificate and private key as a PKCS12 keystore, for later use
		// rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new
		// File("out/keystore.p12"), "primemover", "CHANGEME-password");

		// CertificateAndKeySource existingCertificateSource = new
		// KeyStoreFileCertificateSource(this.certType,
		// new File(this.certPath), this.certAlias, this.certPassword);

		// tell the ImpersonatingMitmManager to generate EC keys and to use the EC
		// RootCertificateGenerator
		ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
				.rootCertificateSource(rootCertificateGenerator)//
				.serverKeyGenerator(new RSAKeyGenerator())//
				.cacheConcurrencyLevel(16) // 8 is default
				.cacheExpirationInterval(120, TimeUnit.MINUTES) // 5 Minutes is default
				// .trustAllServers(true) //
				.build();

		BrowserUpProxyServer proxy = new BrowserUpProxyServer();

		// set DNS resolver
		NativeResolver resolver = new NativeResolver();
		proxy.setHostNameResolver(resolver);

		proxy.setMitmDisabled(true);
		// proxy.setMitmManager(mitmManager);

		// allow some time
		proxy.setConnectTimeout(60, TimeUnit.SECONDS);
		proxy.setIdleConnectionTimeout(60, TimeUnit.SECONDS);
		proxy.setRequestTimeout(60, TimeUnit.SECONDS);

		// remove VIA-Header
		proxy.addLastHttpFilterFactory(new HttpFiltersSourceAdapter() {
			@Override
			public HttpFilters filterRequest(HttpRequest originalRequest) {
				return new HttpFiltersAdapter(originalRequest) {
					@Override
					public HttpResponse proxyToServerRequest(HttpObject httpObject) {
						if (httpObject instanceof HttpRequest) {
							((HttpRequest) httpObject).headers().remove(HttpHeaderNames.VIA);
						}
						return null;
					}
				};
			}
		});

		proxy.start(48000);

		Proxy seleniumProxy = new Proxy();
		seleniumProxy.setProxyType(ProxyType.MANUAL);
		seleniumProxy.setHttpProxy("localhost:" + proxy.getPort());
		seleniumProxy.setSslProxy("localhost:" + proxy.getPort());

		DesiredCapabilities capabilities = new DesiredCapabilities();
		capabilities.setAcceptInsecureCerts(true);
		capabilities.setCapability(CapabilityType.PROXY, seleniumProxy);

		String url = "http://127.0.0.1:35000/api/v1/profile/start?profileId=" + profileId + "&automation=true";
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");

		Integer responseCode = con.getResponseCode();

		if (responseCode != HttpURLConnection.HTTP_OK) {
			throw new Error("starting profile failed");
		}
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();

		// Get JSON text from the response and return the value by key "value"
		JSONObject jsonResponse = new JSONObject(response.toString());

		// success
		String mlConnectionUrl = jsonResponse.getString("value");
		URL remoteUrl = new URL(mlConnectionUrl);
		RemoteWebDriver driver = new RemoteWebDriver(remoteUrl, capabilities);

		driver.navigate().to("https://www.google.com");

		Thread.sleep(60000);

		driver.close();

	}

	public static void main(String[] args) throws IOException, JSONException, InterruptedException {
		new MinimumWorkingExample().run();
	}

}
