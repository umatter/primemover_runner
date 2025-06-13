package primemover.multilogin.mlx;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import primemover.anticaptcha.ICaptchaSolver;
import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.MultiLoginAgent;
import primemover.model.MultiLoginVersion;
import primemover.runner.IContextBuilder;
import primemover.runner.context.MultiloginContext;
import primemover.service.AgentService;

import java.net.MalformedURLException;
import java.net.URL;


public class ContextBuilder implements IContextBuilder{
	
	private ProfileService profileService;
	
	private AgentService agentService;
	
	public ContextBuilder(ProfileService profileService, AgentService agentService) {
		super();
		this.profileService = profileService;
		this.agentService = agentService;
	}

	public MultiloginContext buildContext(Crawler crawler, DesiredCapabilities capabilities, RecordingProxy recorder,
			ICaptchaSolver captchaSolver) {
		
		if (!(crawler.getAgent() instanceof MultiLoginAgent)) {
			throw new IllegalArgumentException("invalid agent received");
		}
		
		MultiLoginAgent typedAgent = (MultiLoginAgent) crawler.getAgent();
		
		if (typedAgent.getVersion() != MultiLoginVersion.MLX) {
			throw new IllegalArgumentException("This startup routine is for MultiloginX only!");
		}		
		
		String mlId = typedAgent.getMultiLoginId();
		
		// create profile
		if (StringUtils.isEmpty(mlId)) {
			mlId = profileService.createProfile(typedAgent, recorder).orElseThrow(() -> new RuntimeException("failed to create profile"));
			agentService.updateMultiloginProfile(typedAgent, mlId);
			typedAgent.setMultiLoginId(mlId);
		}
		
		MultiloginContext mlContext = new MultiloginContext();
		mlContext.setCrawler(crawler);
		
		// update proxy
		if (!profileService.updateProxy(typedAgent, recorder)) {
			throw new RuntimeException("failed to update proxy");
		}
		mlContext.setRecordingProxy(recorder);
		

		// setup anticaptcha
		captchaSolver.setOptions(crawler, capabilities, recorder);
				
		// starting profile
		StartProfileResult startResult = profileService.startProfile(typedAgent, recorder);
		
		if (!startResult.isSuccess()) {
			throw new RuntimeException("failed to start profile");
		}
		
		URL remoteUrl;
			try {
				remoteUrl = new URL("http://" + startResult.getServer() + ":" + startResult.getPort());
			} catch (MalformedURLException e) {
				throw new RuntimeException("failed to build URL", e);
			}
		
		RemoteWebDriver driver = new RemoteWebDriver(remoteUrl, capabilities);
		mlContext.setDriver(driver);
		
		return mlContext;
	}

	@Override
	public void destroyContext(MultiloginContext context) {
		if (!(context.getCrawler().getAgent() instanceof MultiLoginAgent)) {
			throw new IllegalArgumentException("invalid agent received");
		}
		
		MultiLoginAgent typedAgent = (MultiLoginAgent) context.getCrawler().getAgent();
		
		profileService.stopProfile(typedAgent);
	}

}
