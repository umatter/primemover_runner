package primemover.multilogin.mlx;

import org.apache.commons.lang3.StringUtils;
import primemover.capture.BasicAuthorization;
import primemover.capture.RecordingProxy;
import primemover.model.MultiLoginAgent;
import primemover.multilogin.mlx.api.LauncherApi;
import primemover.multilogin.mlx.api.ProfileAccessManagementApi;
import primemover.multilogin.mlx.api.ProfileManagementApi;
import primemover.multilogin.mlx.model.*;
import primemover.multilogin.mlx.utils.TemplateMapper;

import java.util.Optional;

public class ProfileService {
	
	private static String FOLDER_ID = "2e8c5d68-bade-44dc-a45b-211c5435ebfd"; // Fixed to default folder
	
	public ProfileService(LauncherApi launcherApi, ProfileManagementApi profileManagementApi,
			ProfileAccessManagementApi profileAccessManagementApi, TemplateMapper templateMapper) {
		super();
		this.launcherApi = launcherApi;
		this.profileManagementApi = profileManagementApi;
		this.profileAccessManagementApi = profileAccessManagementApi;
		this.templateMapper = templateMapper;
	}

	TemplateMapper templateMapper;
	
	LauncherApi launcherApi;
	
	ProfileManagementApi profileManagementApi;
	
	ProfileAccessManagementApi profileAccessManagementApi;
	
	public boolean hasProfile(MultiLoginAgent agent) {
		String id = agent.getMultiLoginId();
		
		if (StringUtils.isEmpty(id)) {
			return false;
		}
		
		GetProfileStatus200Response resp = launcherApi.getProfileStatus(id, "application/json");
		return resp.getStatus().getHttpCode() == 200;
	}
	
	public StartProfileResult startProfile(MultiLoginAgent agent, RecordingProxy proxy) {
		String id = agent.getMultiLoginId();
		
		if (StringUtils.isEmpty(id)) {
			throw new IllegalArgumentException("Profile id is empty");
		}
		
		StartBrowserProfile200Response startResponse = launcherApi.startBrowserProfile("selenium", "false", FOLDER_ID, id, "application/json", "true");
		
		return new StartProfileResult(
				startResponse.getStatus().getHttpCode() == 200,
				startResponse.getData().getPort(),
				"localhost");
	}
	
	public void stopProfile(MultiLoginAgent agent) {
		String id = agent.getMultiLoginId();
		
		if (StringUtils.isEmpty(id)) {
			throw new IllegalArgumentException("Profile id is empty");
		}
		
		StopBrowserProfile200Response stopResponse = launcherApi.stopBrowserProfile(id, "application/json");
	}
	
	public Optional<String> createProfile(MultiLoginAgent agent, RecordingProxy proxy) {
		String id = agent.getMultiLoginId();
		
		if (!StringUtils.isEmpty(id)) {
			throw new IllegalArgumentException("Profile id is not empty");
		}
		
		String profileJson = templateMapper.getReplacedTemplate(agent, proxy);
		ProfileCreate201Response createResult = profileManagementApi.profileCreate("application/json", "false", profileJson);
		
		if (createResult.getStatus().getHttpCode() == 201) {
			return Optional.of(createResult.getData().getIds().get(0));
		}
		
		return Optional.empty();
	}
	
	
	public boolean updateProxy(MultiLoginAgent agent, RecordingProxy proxy) {
		String id = agent.getMultiLoginId();

		if (StringUtils.isEmpty(id)) {
			throw new IllegalArgumentException("Profile id is empty");
		}

		Optional<BasicAuthorization> proxyAuth = proxy.getAuthorization();
		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append("{");
		strBuilder.append("\"profile_id\": ").append("\"").append(id).append("\"");
		strBuilder.append(", \"proxy\": {");
		strBuilder.append("\"host\": ").append("\"").append(proxy.getIp()).append("\"");

		// if ( proxy.getHttpsPort().isPresent()) {
		//	strBuilder.append(",\"type\": ").append("\"https\"");
		//	strBuilder.append(",\"port\": ").append(proxy.getHttpsPort().get());
		//} else {
			strBuilder.append(",\"type\": ").append("\"http\"");
			strBuilder.append(",\"port\": ").append(proxy.getHttpPort());
		//}

		proxyAuth.ifPresent((auth) -> {
			strBuilder.append(",\"username\": ").append("\"").append(auth.getUsername()).append("\"");
			strBuilder.append(",\"password\": ").append("\"").append(auth.getPassword()).append("\"");
		});
		strBuilder.append("}");

		// Cap V8 heap to prevent runaway memory growth on headless VMs.
		// Without this, YouTube's SPA accumulates JS heap up to 4GB+, causing
		// GC thrashing that makes the renderer unresponsive to WebDriver commands.
		strBuilder.append(", \"parameters\": {\"fingerprint\": {\"cmd_params\": {\"params\": [");
		strBuilder.append("{\"flag\": \"js-flags\", \"value\": \"--max-old-space-size=512\"}");
		strBuilder.append(",{\"flag\": \"load-extension\", \"value\": \"/home/primemover/primemover_runner/primemover-runner/config/h264-override-extension\"}");
		strBuilder.append("]}}}");

		strBuilder.append("}");

		String bodyString = strBuilder.toString();

		ProfileUpdate201Response response = profileManagementApi.profilePartialUpdate("application/json", bodyString);

		return response.getStatus().getHttpCode() == 200;
	}

}
