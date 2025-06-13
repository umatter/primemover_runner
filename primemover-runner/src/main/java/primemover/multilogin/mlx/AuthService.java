package primemover.multilogin.mlx;

import primemover.multilogin.mlx.api.ProfileAccessManagementApi;
import primemover.multilogin.mlx.model.UserSignIn200Response;
import primemover.multilogin.mlx.model.UserSignInRequest;


public class AuthService {
	
	ProfileAccessManagementApi profileAccessManagementApi;
	
	public AuthService(ProfileAccessManagementApi profileAccessManagementApi) {
		super();
		this.profileAccessManagementApi = profileAccessManagementApi;
	}

	public String loginUser(String username, String password) {
		
		UserSignInRequest authRequest = UserSignInRequest.builder().password(password).email(username).build();
		
		UserSignIn200Response authResp = profileAccessManagementApi.userSignIn("application/json", authRequest);
		
		return authResp.getData().getToken();
		
	}

}
