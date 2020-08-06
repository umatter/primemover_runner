package primemover.capture;

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
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import net.lightbody.bmp.proxy.CaptureType;
import net.lightbody.bmp.proxy.auth.AuthType;
import primemover.model.IProxy;

public class RecordingProxy {

	BrowserMobProxy proxy = null;

	public void tearDown() {
		if (proxy != null) {
			proxy.stop();
			proxy = null;
		}
	}

	public void setup(IProxy targetProxy) {
		proxy = new BrowserMobProxyServer();
		proxy.setMitmManager(ImpersonatingMitmManager.builder().trustAllServers(true).build());
		if (targetProxy != null) {
			
			proxy.chainedProxyAuthorization(targetProxy.getUsername(), targetProxy.getPassword(), AuthType.BASIC);
			proxy.setChainedProxy(new InetSocketAddress(targetProxy.getUrl(), targetProxy.getPort()));
		}
		
		// enable more detailed HAR capture. Recording everything.
		proxy.enableHarCaptureTypes(CaptureType.getRequestCaptureTypes());
		proxy.enableHarCaptureTypes(CaptureType.getResponseCaptureTypes());

		proxy.start();
	}
	
	public void setup() {
		this.setup(null);
	}
	
	public DesiredCapabilities applyProxy(DesiredCapabilities cap) {
			
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
		Har har = proxy.newHar(ref);
	}	
	
	public void startCapturing(String ref, String title) {
		proxy.newHar(ref, title);
	}
	
	public Har stopCapturing() {
		return proxy.endHar();
	}
}
