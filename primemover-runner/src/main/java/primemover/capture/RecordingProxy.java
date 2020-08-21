package primemover.capture;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import org.openqa.selenium.Proxy;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

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
import primemover.model.IProxy;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


public class RecordingProxy {
	private static Logger logger = LogManager.getLogger(RecordingProxy.class);
	

	BrowserMobProxy proxy = null;

	public void tearDown() {
		
		logger.debug("tearing down recorder");
		
		if (proxy != null) {
			proxy.stop();
			proxy = null;
		}
	}

	public void setup(IProxy targetProxy) {
		logger.debug("starting recorder");
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
*/
	    CertificateAndKeySource existingCertificateSource = 
	            new KeyStoreFileCertificateSource("PKCS12", new File("C:/Users/dburkolter/git/primemover_runner/primemover-runner/out/keystore.p12"), "primemover", "CHANGEME-password");
	            
	    // tell the ImpersonatingMitmManager to generate EC keys and to use the EC RootCertificateGenerator
	    ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
	            .rootCertificateSource(existingCertificateSource)
	            .serverKeyGenerator(new ECKeyGenerator())
	            //.trustAllServers(true)
	            .build();
	    
		proxy = new BrowserMobProxyServer();
		// proxy.setMitmDisabled(true);
		proxy.setMitmManager(mitmManager);
		
		if (targetProxy != null) {
			logger.debug("using chained proxy");
			proxy.chainedProxyAuthorization(targetProxy.getUsername(), targetProxy.getPassword(), AuthType.BASIC);
			proxy.setChainedProxy(new InetSocketAddress(targetProxy.getUrl(), targetProxy.getPort()));
		}
		
		// enable more detailed HAR capture. Recording everything.
		proxy.enableHarCaptureTypes(CaptureType.getRequestCaptureTypes());
		proxy.enableHarCaptureTypes(CaptureType.getResponseCaptureTypes());

		proxy.start();
		logger.debug("proxy started");
	}
	
	public void setup() {
		this.setup(null);
	}
	
	public DesiredCapabilities applyProxy(DesiredCapabilities cap) {
		logger.debug("applying proxy to capabilities");
			
		Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
		try {
		   String hostIp = Inet4Address.getLocalHost().getHostAddress();
		   seleniumProxy.setHttpProxy(hostIp + ":" + proxy.getPort());
		   seleniumProxy.setSslProxy(hostIp + ":" + proxy.getPort());
		   
		   cap.setCapability(CapabilityType.PROXY, seleniumProxy);
		   
		   return cap;
		} catch (UnknownHostException e) {
			// TODO add logging
			
			return null;
		}
	}
	
	public String getIp() {
		try {
			return Inet4Address.getLocalHost().getHostAddress();
			   
			} catch (UnknownHostException e) {
				// TODO add logging
				
				return "0.0.0.0";
			}
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
