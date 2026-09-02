package primemover.multilogin.mlx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import primemover.capture.BasicAuthorization;
import primemover.capture.RecordingProxy;
import primemover.model.MultiLoginAgent;
import primemover.multilogin.mlx.api.LauncherApi;
import primemover.multilogin.mlx.api.ProfileAccessManagementApi;
import primemover.multilogin.mlx.api.ProfileManagementApi;
import primemover.multilogin.mlx.model.*;
import primemover.multilogin.mlx.utils.TemplateMapper;

import java.util.List;
import java.util.Optional;

public class ProfileService {
	
	// The Multilogin workspace folder is account-specific. It is injected from
	// multilogin.defaultFolderId (the same property TemplateMapper reads); the
	// literal below is only a fallback for the legacy constructors that predate
	// the parameter, so a new account is configured entirely in properties and
	// needs no code change. Was previously a hardcoded constant here.
	private static final String DEFAULT_FOLDER_ID = "2e8c5d68-bade-44dc-a45b-211c5435ebfd";

	// The H.264 codec-override extension is loaded into every profile (see
	// updateProxy). Its path is absolute and host-specific: on ex63 hardware
	// VA-API decode makes it redundant, but on a GPU-less VM it is load-bearing
	// again -- forcing H.264 keeps software decode affordable instead of pinning
	// the renderer main thread on VP9/AV1. A wrong/absent path makes Chrome drop
	// the extension silently, which reintroduces the renderer death with nothing
	// in the logs, so it is injectable (multilogin.h264ExtensionPath) with the
	// ex63 path as the fallback for callers that pass nothing.
	private static final String DEFAULT_H264_EXTENSION_PATH =
			"/home/primemover/primemover_runner/primemover-runner/config/h264-override-extension";

	private final String folderId;
	private final String h264ExtensionPath;

	public ProfileService(LauncherApi launcherApi, ProfileManagementApi profileManagementApi,
			ProfileAccessManagementApi profileAccessManagementApi, TemplateMapper templateMapper) {
		this(launcherApi, profileManagementApi, profileAccessManagementApi, templateMapper, null);
	}

	public ProfileService(LauncherApi launcherApi, ProfileManagementApi profileManagementApi,
			ProfileAccessManagementApi profileAccessManagementApi, TemplateMapper templateMapper,
			ProfileCreateBatcher batcher) {
		this(launcherApi, profileManagementApi, profileAccessManagementApi, templateMapper, batcher,
				DEFAULT_FOLDER_ID);
	}

	public ProfileService(LauncherApi launcherApi, ProfileManagementApi profileManagementApi,
			ProfileAccessManagementApi profileAccessManagementApi, TemplateMapper templateMapper,
			ProfileCreateBatcher batcher, String folderId) {
		this(launcherApi, profileManagementApi, profileAccessManagementApi, templateMapper, batcher, folderId,
				DEFAULT_H264_EXTENSION_PATH);
	}

	public ProfileService(LauncherApi launcherApi, ProfileManagementApi profileManagementApi,
			ProfileAccessManagementApi profileAccessManagementApi, TemplateMapper templateMapper,
			ProfileCreateBatcher batcher, String folderId, String h264ExtensionPath) {
		super();
		this.launcherApi = launcherApi;
		this.profileManagementApi = profileManagementApi;
		this.profileAccessManagementApi = profileAccessManagementApi;
		this.templateMapper = templateMapper;
		this.batcher = batcher;
		// Fall back to the default only if a caller passes nothing, never silently
		// accept an empty configured value.
		this.folderId = (folderId == null || folderId.isBlank()) ? DEFAULT_FOLDER_ID : folderId;
		this.h264ExtensionPath = (h264ExtensionPath == null || h264ExtensionPath.isBlank())
				? DEFAULT_H264_EXTENSION_PATH : h264ExtensionPath;
	}

	/** The workspace folder profiles are created/started in (test seam). */
	String folderId() {
		return folderId;
	}

	/** The H.264 override extension path loaded into every profile (test seam). */
	String h264ExtensionPath() {
		return h264ExtensionPath;
	}

	/**
	 * Groups concurrent creations into one call. Null keeps the old behaviour of
	 * one request per crawler.
	 */
	private ProfileCreateBatcher batcher;

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
		
		StartBrowserProfile200Response startResponse = launcherApi.startBrowserProfile("selenium", "false", folderId, id, "application/json", "true");
		
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

		if (batcher == null) {
			return Optional.ofNullable(createOne(profileJson));
		}

		// Multilogin's own 429 says to do this: one request with "times" rather
		// than one request per bot. See ProfileCreateBatcher for why sharing a
		// template across a batch cannot leak another bot's proxy.
		try {
			return Optional.ofNullable(batcher.acquire(profileJson, this::createMany));
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("failed to create profile", e);
		}
	}

	/** One profile, the way it has always worked. */
	private String createOne(String profileJson) {
		ProfileCreate201Response createResult = profileManagementApi.profileCreate("application/json", "false",
				profileJson);

		if (createResult.getStatus().getHttpCode() == 201) {
			return createResult.getData().getIds().get(0);
		}

		return null;
	}

	/**
	 * Sets {@code times} on a profile template.
	 *
	 * <p>
	 * Parsed rather than rewritten textually, because the field is not reliably
	 * there to rewrite. {@code TemplateMapper} uses the agent's own
	 * {@code multiLoginCreationJson} when it has one and the config default
	 * otherwise, and only the former carries {@code "times"} --
	 * {@code config/DefaultMultiLoginProfile.json} has no such field at all. A
	 * regex would silently no-op on that template, leaving the API to default to
	 * one profile, so a batch of twelve would receive a single id and fail the
	 * other eleven. That is worse than not batching.
	 *
	 * @return the template with {@code times} set, or the original if it cannot
	 *         be parsed -- an unreadable template is the caller's problem to
	 *         report, not something to fail here
	 */
	static String withTimes(String profileJson, int times) {
		if (times <= 1) {
			return profileJson;
		}
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode root = (ObjectNode) mapper.readTree(profileJson);
			root.put("times", times);
			return mapper.writeValueAsString(root);
		} catch (Exception e) {
			return profileJson;
		}
	}

	/** {@code times} identical profiles in a single call. */
	List<String> createMany(String profileJson, int times) {
		ProfileCreate201Response createResult = profileManagementApi.profileCreate("application/json", "false",
				withTimes(profileJson, times));

		if (createResult.getStatus().getHttpCode() == 201) {
			return createResult.getData().getIds();
		}

		return java.util.Collections.emptyList();
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
		strBuilder.append("{\"flag\": \"js-flags\", \"value\": \"--max-old-space-size=2048\"}");
		strBuilder.append(",{\"flag\": \"load-extension\", \"value\": \"").append(h264ExtensionPath).append("\"}");
		strBuilder.append("]}}}");

		strBuilder.append("}");

		String bodyString = strBuilder.toString();

		ProfileUpdate201Response response = profileManagementApi.profilePartialUpdate("application/json", bodyString);

		return response.getStatus().getHttpCode() == 200;
	}

}
