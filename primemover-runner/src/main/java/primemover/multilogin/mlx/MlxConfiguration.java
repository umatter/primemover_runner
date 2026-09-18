package primemover.multilogin.mlx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import primemover.multilogin.mlx.api.LauncherApi;
import primemover.multilogin.mlx.api.ProfileAccessManagementApi;
import primemover.multilogin.mlx.api.ProfileManagementApi;
import primemover.multilogin.mlx.client.ApiClient;
import primemover.multilogin.mlx.utils.TemplateMapper;
import primemover.service.AgentService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Every bean the Multilogin X path needs.
 *
 * Two things about how this configuration is switched on, both of which exist
 * so that a developer with no Multilogin account can still boot the runner:
 *
 * <ul>
 * <li><b>{@code multilogin.enabled=false} removes the beans entirely.</b> Nothing
 * here is created, no sign-in is ever attempted, and a crawler whose agent is
 * MULTILOGIN fails its own run with
 * {@link primemover.runner.TaskOperator#MULTILOGIN_DISABLED_MESSAGE} rather than
 * taking the JVM down. Default is {@code true}: production is unaffected.</li>
 * <li><b>The beans are {@code @Lazy}.</b> {@link #authTokenProvider()} signs in to
 * Multilogin's cloud inside the factory method, so while these beans were eager a
 * blank {@code multilogin.apiPassword} (or an unreachable account) failed Spring's
 * context refresh and killed the process at boot -- whatever browser the crawlers
 * on that box actually used. Lazily, the sign-in happens on the first MLX profile
 * request instead, so the failure lands on the run that needs Multilogin.</li>
 * </ul>
 *
 * Pinned by {@code MlxConfigurationBootTest}.
 */
@Configuration
@Lazy
@ConditionalOnProperty(name = "multilogin.enabled", havingValue = "true", matchIfMissing = true)
public class MlxConfiguration {
	
	// Defaulted so that a context without the runner's full property set (a test,
	// or a box that never configured Multilogin) can still read this class. The
	// credentials are only ever USED by the lazy authTokenProvider bean.
	@Value("${multilogin.apiUser:}")
	private String apiUser;
	@Value("${multilogin.apiPassword:}")
	private String apiPassword;
	
	private static String API_BASEPATH_SERVER = "https://api.multilogin.com";	
	
	private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@Bean(initMethod = "init")
	public TemplateMapper templateLoader() {
		return new TemplateMapper();
	}
	
	@Bean(name = "MlxAuthTokenProvider")
	public AuthTokenProvider authTokenProvider() {
        ApiClient client = new ApiClient();
        client.setBasePath(API_BASEPATH_SERVER);
        
        AuthTokenProvider authTokenProvider = new AuthTokenProvider(client, apiUser, apiPassword);
        authTokenProvider.authenticate();
        
        return authTokenProvider;
	}
	
	@Bean(name = "MlxMultiloginApi")
    public ApiClient multiloginApiClient(AuthTokenProvider authTokenProvider) {        
        ApiClient client = new ApiClient();
        client.setBasePath(API_BASEPATH_SERVER);
        client.setBearerToken(() -> authTokenProvider.getToken());
        
        // Schedule the authenticate method to run every 30 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                authTokenProvider.authenticate();
            } catch (Exception e) {
            	throw new IllegalStateException("could not authenticate API", e);
            }
        }, 30, 30, TimeUnit.MINUTES);
        
        return client;
    }
	
	@Bean(name = "MlxLocalApi")
    public ApiClient localApiClient(AuthTokenProvider authTokenProvider) {        
        // LocalApi
        ApiClient client = new ApiClient();
        client.setBasePath("https://launcher.mlx.yt:45001/api");
        client.setBearerToken(() -> authTokenProvider.getToken());
             
        return client;
    }

    @Bean
    public ProfileAccessManagementApi profileAccessManagementApi(@Qualifier("MlxMultiloginApi") ApiClient apiClient) {
        return new ProfileAccessManagementApi(apiClient);
    }
    
    @Bean
    public LauncherApi launcherApi(@Qualifier("MlxLocalApi") ApiClient apiClient) {
        return new LauncherApi(apiClient);
    }
    
    @Bean
    public ProfileManagementApi profileManagementApi(@Qualifier("MlxMultiloginApi") ApiClient apiClient) {
        return new ProfileManagementApi(apiClient);
    }
    
    @Bean
    public AuthService authService(ProfileAccessManagementApi profileAccessManagementApi) {
    	return new AuthService(profileAccessManagementApi);    	
    }
    
    /**
     * Batches concurrent profile creations into one API call.
     *
     * Multilogin allows 50 operations per minute and asks for this explicitly in
     * the body of its own 429: "Try sending a single request with \"times\"
     * parameter instead". Twelve bots launching together previously made twelve
     * separate calls in the same second, which cost one bot its run on
     * 2026-08-31 and four on 2026-09-01.
     *
     * Size 0 disables batching and restores one request per crawler.
     */
    @Bean
    public ProfileCreateBatcher profileCreateBatcher(
            @Value("${multilogin.ProfileCreateBatchSize:12}") int batchSize,
            @Value("${multilogin.ProfileCreateBatchWindowMs:2500}") long windowMs,
            @Value("${multilogin.ProfileCreateWaitTimeoutMs:120000}") long waitTimeoutMs) {
        return batchSize <= 1 ? null : new ProfileCreateBatcher(batchSize, windowMs, waitTimeoutMs);
    }

    @Bean
    public ProfileService profileService(LauncherApi launcherApi, ProfileManagementApi profileManagementApi,
			ProfileAccessManagementApi profileAccessManagementApi, TemplateMapper templateMapper,
			@Autowired(required = false) ProfileCreateBatcher profileCreateBatcher,
			@Value("${multilogin.defaultFolderId}") String defaultFolderId,
			@Value("${multilogin.h264ExtensionPath:}") String h264ExtensionPath) {
    	return new ProfileService(launcherApi, profileManagementApi, profileAccessManagementApi, templateMapper,
    			profileCreateBatcher, defaultFolderId, h264ExtensionPath);
    }
    
    @Bean(name = "MlxContextBuilder")
    public ContextBuilder mlxContextBuilder(ProfileService profileService, AgentService agentService) {
    	return new ContextBuilder(profileService, agentService);
    }
}
