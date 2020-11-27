package primemover.capture;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.net.SocketAddress;
import java.net.URL;
import java.net.UnknownHostException;

import org.openqa.selenium.Proxy;
import org.openqa.selenium.Proxy.ProxyType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.KeyStoreFileCertificateSource;
import net.lightbody.bmp.mitm.keys.ECKeyGenerator;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;
import net.lightbody.bmp.proxy.auth.AuthType;
import primemover.model.GeoSurfProxy;
import primemover.model.IProxy;
import primemover.service.IpService;


public class RecordingProxy {
	private static Logger logger = LoggerFactory.getLogger(RecordingProxy.class);
	
	@Value("${recorder.certificate.type}")
	private  String certType;
	
	@Value("${recorder.certificate.path}")
	private  String certPath;
	
	@Value("${recorder.certificate.alias}")
	private  String certAlias;
	
	@Value("${recorder.certificate.password}")
	private  String certPassword;
	
	@Value("${recorder.gracefullTeardownSeconds:20}")
	private Integer gracefullTeardownSeconds;
	
	@Value("${recorder.connectTimeoutSeconds:30}")
	private Integer connectTimeoutSeconds;
	
	@Value("${recorder.iddleConnectionTimeoutSeconds:60}")
	private Integer iddleConnectionTimeoutSeconds;
	
	@Value("${recorder.requestTimeoutSeconds:300}")
	private Integer requestTimeoutSeconds;
		
	@Value("${recorder.connectivityRetryCount:3}")
	private Integer connectivityRetryCount;
	
	@Value("${recorder.connectivityDelaySeconds:5}")
	private Integer connectivityDelaySeconds;
	
	@Value("${verifyConnections:true}")
	private Boolean verifyConnections;
	
	@Value("#{'${recorder.connectivityVerificationUrls:https://siaw.primemover.example/,https://app.multiloginapp.com/whatismyip}'.split(',')}") // zero length string default!
	private List<String> connectivityVerificationUrls;

	BrowserMobProxy proxy = null;

	
	public void tearDown(boolean immediate) {
		if (proxy == null) {
			return; // nothing to do
		}
		
		if (immediate) {
			logger.debug("tearing down recorder immediately");
			proxy.abort();
			proxy = null;
		} else {
			logger.debug("tearing down recorder");
			if (!proxy.waitForQuiescence(1, gracefullTeardownSeconds, TimeUnit.SECONDS)) {
				logger.warn("Tearing down recorder with network trafic still running");
			}
			proxy.abort();
			proxy = null;
		}
	}
	
	
	public void tearDown() {
		tearDown(false);
	}
	
	/**
	 * Will change the headers of the proxy
	 * 
	 * @param targetProxy
	 */
	public void reset(IProxy targetProxy) {
		tearDown();
		setup(targetProxy);
	}
	
	private void buildMitmProxy(IProxy targetProxy) {
		logger.debug("buidling recorder");
		
		/*
		// create a RootCertificateGenerator that generates EC Certificate Authorities; you may also load your
	    // own EC certificate and private key using any other CertificateAndKeySource implementation 
	    // (KeyStoreFileCertificateSource, PemFileCertificateSource, etc.).
		RootCertificateGenerator rootCertificateGenerator = RootCertificateGenerator.builder()
	            .keyGenerator(new ECKeyGenerator())
	            .build();

	    // save the newly-generated Root Certificate and Private Key -- the .cer file can be imported 
	    // directly into a browser
	    rootCertificateGenerator.saveRootCertificateAsPemFile(new File("C:/Users/dburkolter/git/primemover_runner/primemover-runner/out/certificate.cer"));
	    rootCertificateGenerator.savePrivateKeyAsPemFile(new File("C:/Users/dburkolter/git/primemover_runner/primemover-runner/out/private-key.pem"), "CHANGEME-password");
	    
	    // or save the certificate and private key as a PKCS12 keystore, for later use
	    rootCertificateGenerator.saveRootCertificateAndKey("PKCS12", new File("C:/Users/dburkolter/git/primemover_runner/primemover-runner/out/keystore.p12"), 
	            "primemover", "CHANGEME-password");

	    CertificateAndKeySource existingCertificateSource = 
	            new KeyStoreFileCertificateSource("PKCS12", new File("C:/Users/dburkolter/git/primemover_runner/primemover-runner/out/keystore.p12"), "primemover", "CHANGEME-password");
	            
	    */

		CertificateAndKeySource existingCertificateSource = 
				new KeyStoreFileCertificateSource(this.certType, new File(this.certPath), this.certAlias, this.certPassword);


		// tell the ImpersonatingMitmManager to generate EC keys and to use the EC RootCertificateGenerator
		ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
				.rootCertificateSource(existingCertificateSource)
				.serverKeyGenerator(new ECKeyGenerator())
				.cacheConcurrencyLevel(16) // 8 is default
				.cacheExpirationInterval(60, TimeUnit.MINUTES) // 5 Minutes is default
				//.trustAllServers(true)
				.build();

		proxy = new BrowserMobProxyServer();
		// proxy.setMitmDisabled(true);
		proxy.setMitmManager(mitmManager);

		// set defaults
		proxy.setConnectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS);
		proxy.setIdleConnectionTimeout(iddleConnectionTimeoutSeconds, TimeUnit.SECONDS);
		proxy.setRequestTimeout(requestTimeoutSeconds, TimeUnit.SECONDS);


		if (targetProxy != null) {
			logger.debug("using chained proxy");
			proxy.chainedProxyAuthorization(targetProxy.getUsername(), targetProxy.getPassword(), AuthType.BASIC);
			proxy.setChainedProxy(new InetSocketAddress(targetProxy.getUrl(), targetProxy.getPort()));

			// create session-identifier and set session-headers
			if (targetProxy instanceof GeoSurfProxy) {
				logger.debug("applying GeoSurf-Headers");
				GeoSurfProxy geoProxy = (GeoSurfProxy) targetProxy;
				geoProxy.setSessionId("primemover-" + UUID.randomUUID().toString());
				
				proxy.addHeader("loc", geoProxy.getLocation());
				proxy.addHeader("X-session", geoProxy.getSessionId());
			}	
		}

		// enable more detailed HAR capture. Recording everything.
		proxy.enableHarCaptureTypes(CaptureType.getRequestCaptureTypes());
		proxy.enableHarCaptureTypes(CaptureType.getResponseCaptureTypes());

		proxy.start();
	}

	private boolean verifyMitmConnectivity() {
		
		if (proxy == null) {
			return false;
		}
		
		if (connectivityVerificationUrls == null || connectivityVerificationUrls.size() == 0) {
			return true;
		}
		
		java.net.Proxy javaProxy = getProxy();
		
		for (String strUrl : connectivityVerificationUrls) {
			try {
				logger.debug("Start to verify {}", strUrl);
		        URL url = new URL(strUrl);
		        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection(javaProxy);
		        urlConn.connect();
		        if (urlConn.getResponseCode() != HttpURLConnection.HTTP_OK) {
		        	urlConn.disconnect();
		        	return false;
		        }
		        urlConn.disconnect();
		        logger.debug("Successfully verified {}", strUrl);
		    } catch (IOException e) {
		        logger.warn("Error verifying HTTP connection with URL {}", strUrl, e);
		        return false;
		    }
		}		
		logger.debug("Success verifying HTTP connection");
		return true;
	}
	
	public void setup(IProxy targetProxy) throws IllegalStateException {
		logger.debug("starting recorder");
		
		// proxy creation without connectivity verification
		if (!verifyConnections) {
			logger.debug("creating proxy without connectivity verification");
			buildMitmProxy(targetProxy);
			return;
		}
		
		// proxy creation with connectivity verification
		int curRound = 1;
		while (curRound <= connectivityRetryCount) {
			logger.debug("creating proxy with connectivity verification ({} of {})", curRound, connectivityRetryCount);
			buildMitmProxy(targetProxy);	
						
			if (verifyMitmConnectivity()) {
				logger.debug("proxy started and connection verified");
				return;
			}
			
			tearDown(true);
			
			try {
				Thread.sleep(connectivityDelaySeconds*1000);
			} catch (InterruptedException e) {
				// nothing to do
			}
			curRound++;
		}
		
		logger.error("proxy was not started properly");
		throw new IllegalStateException("Proxy was not started properly");
	}
	
	public void setup() {
		this.setup(null);
	}
	
	public java.net.Proxy getProxy() {
		InetAddress adr = getInetAddress();
		
		SocketAddress addr = new InetSocketAddress(adr, proxy.getPort());
		return new java.net.Proxy(Type.HTTP, addr);
	}
	
	public void applyProxy(IpService ipService) {
		java.net.Proxy javaProxy = getProxy();
				
		ipService.setProxy(javaProxy);
	}
	
	private InetAddress getInetAddress() {
		InetAddress adr; // proxy.getClientBindAddress();
		try {
			adr = Inet4Address.getLocalHost(); 
		} catch (UnknownHostException e) {
			logger.warn("Could not resolve localhost", e);
			throw new IllegalStateException("could not resolve localhost", e);
		} 
		return adr;
	}
	
	public DesiredCapabilities applyProxy(DesiredCapabilities cap) {
		logger.debug("applying proxy to capabilities");
			
		Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
		
		InetAddress adr = getInetAddress();
		
		String hostIp = adr.getHostAddress();
		seleniumProxy.setProxyType(ProxyType.MANUAL);
		seleniumProxy.setHttpProxy(hostIp + ":" + proxy.getPort());
		seleniumProxy.setSslProxy(hostIp + ":" + proxy.getPort());
	   
		cap.setCapability(CapabilityType.PROXY, seleniumProxy);
		
		logger.info("setting proxy to {}", adr);
		
		return cap;
	}
	
	public String getType() {
		return "HTTP";
	}
	
	public String getUsername() {
		return "";
	}
	
	public String getPassword() {
		return "";
	}
	
	public String getIp() {
		return proxy.getClientBindAddress().getHostAddress();	 
	}
	
	public Integer getPort() {
		return proxy.getPort();
	}
	
	public void newPage() {
		proxy.newHar();
		proxy.newPage();
	}
	
	public void newPage(String ref) {
		proxy.newHar();
		proxy.newPage(ref);
	}
	
	public void newPage(String ref, String title) {
		proxy.newHar();
		proxy.newPage(ref, title);
	}
	
	public Har getHar() {
		return proxy.getHar();
	}
	
	public void startCapturing() {
		proxy.newHar();
	}
	
	public void startCapturing(String ref) {				
		proxy.newHar(ref);
	}	
	
	public void startCapturing(String ref, String title) {
		proxy.newHar(ref, title);
	}
	
	public Har stopCapturing() {
		return proxy.endHar();
	}
}
