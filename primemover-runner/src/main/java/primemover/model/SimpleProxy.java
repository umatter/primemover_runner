package primemover.model;

public class SimpleProxy implements IProxy {

	private String url;
	private String username;
	private String password;
	
	@Override
	public String getUrl() {
		return this.url;
	}

	@Override
	public String getUsername() {
		return this.username;
	}

	@Override
	public String getPassword() {
		return this.password;
	}

}
