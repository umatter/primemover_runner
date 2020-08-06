package primemover.model;

public class SimpleProxy implements IProxy {

	private String url;
	private Integer port;
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
	
	@Override
	public Integer getPort() {
		return port;
	}

	public SimpleProxy(String url, Integer port, String username, String password) {
		super();
		this.url = url;
		this.port = port;
		this.username = username;
		this.password = password;
	}

	public SimpleProxy() {
		super();
	}	
}
