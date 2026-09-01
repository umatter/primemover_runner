package primemover.multilogin.mlx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import primemover.multilogin.mlx.api.LauncherApi;
import primemover.multilogin.mlx.api.ProfileAccessManagementApi;
import primemover.multilogin.mlx.api.ProfileManagementApi;
import primemover.multilogin.mlx.client.ApiClient;
import primemover.multilogin.mlx.utils.TemplateMapper;
import primemover.service.AgentService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class MlxConfiguration {
	
	@Value("${multilogin.apiUser}")
	private String apiUser;
	@Value("${multilogin.apiPassword}")
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
			@Autowired(required = false) ProfileCreateBatcher profileCreateBatcher) {
    	return new ProfileService(launcherApi, profileManagementApi, profileAccessManagementApi, templateMapper,
    			profileCreateBatcher);
    }
    
    @Bean(name = "MlxContextBuilder")
    public ContextBuilder mlxContextBuilder(ProfileService profileService, AgentService agentService) {
    	return new ContextBuilder(profileService, agentService);
    }
}
