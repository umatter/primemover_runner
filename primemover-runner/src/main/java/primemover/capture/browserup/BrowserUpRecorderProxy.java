package primemover.capture.browserup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.mitm.CertificateAndKeySource;
import com.browserup.bup.mitm.KeyStoreFileCertificateSource;
import com.browserup.bup.mitm.keys.RSAKeyGenerator;
import com.browserup.bup.mitm.manager.ImpersonatingMitmManager;
import com.browserup.bup.proxy.CaptureType;
import com.browserup.bup.proxy.auth.AuthType;
import com.browserup.bup.proxy.dns.NativeResolver;
import com.browserup.harreader.model.Har;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import primemover.capture.IRecorderProxy;
import primemover.capture.ProxyDecoratorFactory;
import primemover.capture.SessionDecorator;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.HttpProxy;
import primemover.model.proxy.IProxy;
import primemover.runner.context.IRunnerContext;
import software.amazon.awssdk.utils.StringUtils;

public class BrowserUpRecorderProxy implements IRecorderProxy {
	private static final Logger log = LoggerFactory.getLogger(BrowserUpRecorderProxy.class);

	@Value("${recorder.enabled:true}")
	private Boolean recordingEnabled;

	@Value("${recorder.certificate.type}")
	private String certType;

	@Value("${recorder.certificate.path}")
	private String certPath;

	@Value("${recorder.certificate.alias}")
	private String certAlias;

	@Value("${recorder.certificate.password}")
	private String certPassword;

	@Value("${recorder.gracefullTeardownSeconds:20}")
	private Integer gracefullTeardownSeconds;

	@Value("${recorder.connectTimeoutSeconds:45}")
	private Integer connectTimeoutSeconds;

	@Value("${recorder.iddleConnectionTimeoutSeconds:60}")
	private Integer iddleConnectionTimeoutSeconds;

	@Value("${recorder.requestTimeoutSeconds:300}")
	private Integer requestTimeoutSeconds;

	@Value("${recorder.connectivityRetryCount:3}")
	private Integer connectivityRetryCount;

	@Value("${recorder.connectivityDelaySeconds:30}")
	private Integer connectivityDelaySeconds;

	@Value("${proxy.verifyConnections:true}")
	private Boolean verifyConnections;

	@Value("${proxy.pingConnections:true}")
	private Boolean pingConnections;

	@Autowired
	private ProxyDecoratorFactory sessionDecoratorFactory;

	private BrowserUpProxyServer proxy = null;

	@Override
	public boolean initProxy(IProxy targetProxy) {

		log.debug("buidling recorder");

		/*
		 * // create a RootCertificateGenerator that generates EC Certificate
		 * Authorities; you may also load your // own EC certificate and private key
		 * using any other CertificateAndKeySource implementation //
		 * (KeyStoreFileCertificateSource, PemFileCertificateSource, etc.).
		 * RootCertificateGenerator rootCertificateGenerator =
		 * RootCertificateGenerator.builder() .keyGenerator(new ECKeyGenerator())
		 * .build();
		 * 
		 * // save the newly-generated Root Certificate and Private Key -- the .cer file
		 * can be imported // directly into a browser
		 * rootCertificateGenerator.saveRootCertificateAsPemFile(new
		 * File("out/certificate.cer"));
		 * rootCertificateGenerator.savePrivateKeyAsPemFile(new
		 * File("out/private-key.pem"), "CHANGEME-password");
		 * 
		 * // or save the certificate and private key as a PKCS12 keystore, for later
		 * use rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new
		 * File("out/keystore.p12"), "primemover", "CHANGEME-password");
		 */

		proxy = new BrowserUpProxyServer();

		if (recordingEnabled) {
			log.info("enabling recording: {}", recordingEnabled);
			CertificateAndKeySource existingCertificateSource = new KeyStoreFileCertificateSource(this.certType,
					new File(this.certPath), this.certAlias, this.certPassword);

			// tell the ImpersonatingMitmManager to generate EC keys and to use the EC
			// RootCertificateGenerator
			ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
					.rootCertificateSource(existingCertificateSource)//
					.serverKeyGenerator(new RSAKeyGenerator())//
					.cacheConcurrencyLevel(16) // 8 is default
					.cacheExpirationInterval(120, TimeUnit.MINUTES) // 5 Minutes is default
					.build();

			proxy.setMitmDisabled(false);
			proxy.setMitmManager(mitmManager);

			// enable more detailed HAR capture. Recording everything.
			proxy.enableHarCaptureTypes(CaptureType.getRequestCaptureTypes());
			proxy.enableHarCaptureTypes(CaptureType.getResponseCaptureTypes());
		} else {
			proxy.setMitmDisabled(true);
		}

		// set DNS resolver
		NativeResolver resolver = new NativeResolver();
		resolver.getHostRemappings();
		log.debug("resolver: {}", resolver);
		proxy.setHostNameResolver(resolver);

		// set defaults
		proxy.setConnectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS);
		proxy.setIdleConnectionTimeout(iddleConnectionTimeoutSeconds, TimeUnit.SECONDS);
		proxy.setRequestTimeout(requestTimeoutSeconds, TimeUnit.SECONDS);

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

		if (targetProxy != null) {
			log.debug("using chained proxy {}:{}", targetProxy.getUrl(), targetProxy.getPort());

			final SessionDecorator decoratedProxy = sessionDecoratorFactory.decorateProxy(targetProxy);
			log.debug("generated session-id: {}", decoratedProxy.getSessionId());

			// Whitelisting check -> Username and Password not set
			String username = decoratedProxy.getUsername();
			String password = decoratedProxy.getPassword();
			if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
				proxy.chainedProxyAuthorization(username, password, AuthType.BASIC);
			}
			proxy.setChainedProxy(new InetSocketAddress(decoratedProxy.getUrl(), decoratedProxy.getPort()));
			// proxy.setChainedProxyNonProxyHosts(Arrays.asList("app.multiloginapp.com"));

			if (targetProxy instanceof HttpProxy) {
				HttpProxy typedTargetProxy = (HttpProxy) targetProxy;
				proxy.setChainedProxyHTTPS(typedTargetProxy.useHttps());
			}

			// create session-identifier and set session-headers
			if (targetProxy instanceof GeoSurfProxy) {
				log.debug("applying GeoSurf-Headers");

				// add custom auth headers ->
				// https://github.com/browserup/browserup-proxy/blob/88914049391bd3ec5d3db1e4e92618fc7d197810/browserup-proxy-core/src/main/java/com/browserup/bup/BrowserUpProxyServer.java
				// little dangerous as we don't watch missing auth credentials as in above code
				proxy.addLastHttpFilterFactory(new HttpFiltersSourceAdapter() {
					private final SessionDecorator proxy = decoratedProxy;
					private final GeoSurfProxy geoProxy = (GeoSurfProxy) targetProxy;

					@Override
					public HttpFilters filterRequest(HttpRequest originalRequest) {
						return new HttpFiltersAdapter(originalRequest) {

							@Override
							public HttpResponse proxyToServerRequest(HttpObject httpObject) {
								if (httpObject instanceof HttpRequest) {
									if (ProxyUtils.isCONNECT(httpObject)
											|| !((HttpRequest) httpObject).uri().startsWith("/")) {
										HttpHeaders headers = ((HttpRequest) httpObject).headers();
										headers.add("loc", geoProxy.getLocation());
										headers.add("X-session", proxy.getSessionId());
									}
								}
								return null;
							}
						};
					}
				});
			}
		}

		proxy.start();

		log.debug("Proxy started on port {}", proxy.getPort());

		return true;
	}

	@Override
	public void closeProxy(boolean immediate) {
		// shuting down proxy
		if (proxy == null) {
			return; // nothing to do
		}

		if (immediate) {
			log.debug("tearing down recorder immediately");
			proxy.abort();
			proxy = null;
		} else {
			log.debug("tearing down recorder");
			if (!proxy.waitForQuiescence(1, gracefullTeardownSeconds, TimeUnit.SECONDS)) {
				log.warn("Tearing down recorder with network trafic still running");
			}
			proxy.abort();
			proxy = null;
		}
	}

	@Override
	public InetAddress getAddress() {
		return proxy.getClientBindAddress();
	}

	@Override
	public int getHttpPort() {
		return proxy.getPort();
	}

	@Override
	public Optional<Integer> getHttpsPort() {
		return Optional.of(proxy.getPort());
	}

	@Override
	public void startCapturing() {
		proxy.newHar();
	}

	@Override
	public void startCapturing(String ref) {
		if (!recordingEnabled) {
			return;
		}

		proxy.newHar(ref);
	}

	private static String harToString(Har har) {
		try {
			ByteArrayOutputStream tmpOs = new ByteArrayOutputStream(4048);
			har.writeTo(tmpOs);

			return tmpOs.toString();
		} catch (IOException ex) {
			log.warn("Error grabing HAR", ex);
			return null;
		}
	}

	@Override
	public @Nullable String newPage() {
		if (!recordingEnabled) {
			return null;
		}

		return harToString(proxy.newPage());
	}

	@Override
	public @Nullable String newPage(String ref) {
		if (!recordingEnabled) {
			return null;
		}

		return harToString(proxy.newPage(ref));
	}

	@Override
	public @Nullable String stopCapturing() {
		if (!recordingEnabled) {
			return null;
		}

		return harToString(proxy.endHar());
	}

	@Override
	public boolean applyContext(IRunnerContext<?> context) {
		return true;
	}
}
