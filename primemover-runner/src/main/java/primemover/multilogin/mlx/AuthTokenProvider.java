package primemover.multilogin.mlx;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.DigestUtils;
import primemover.multilogin.mlx.api.ProfileAccessManagementApi;
import primemover.multilogin.mlx.client.ApiClient;
import primemover.multilogin.mlx.model.UserSignIn200Response;
import primemover.multilogin.mlx.model.UserSignInRequest;

public class AuthTokenProvider {
		private String username;
		private String password;
	    private String token = null;
	    private ApiClient client;

	    public AuthTokenProvider(ApiClient client, String username, String password) {
			super();
			this.client = client;
			this.username = username;
			this.password = password;
		}
	    
	    @Scheduled(fixedRate = 30 * 60 * 1000) // 30 minutes in milliseconds
		public void authenticate() {
	        // Assuming your API has a LoginApi class and a LoginRequest model
	    	
	    	ProfileAccessManagementApi profileAccessManagementApi = new ProfileAccessManagementApi(client);
	    	
	    	UserSignInRequest authRequest = UserSignInRequest.builder()
	    			.password(DigestUtils.md5DigestAsHex(password.getBytes()))
	    			.email(username).build();
			
			UserSignIn200Response authResp = profileAccessManagementApi.userSignIn("application/json", authRequest);
			
			this.token = authResp.getData().getToken();
	    }

	    public String getToken() {
	        return token;
	    }
	}
	
