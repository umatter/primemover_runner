package primemover.model;

public class HttpProxy implements IProxy {

	private String url;
	private Integer port;
	private String username;
	private String password;
	private Boolean useHttps = false;
	
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

	public HttpProxy(String url, Integer port, String username, String password) {
		super();
		this.url = url;
		this.port = port;
		this.username = username;
		this.password = password;
	}

	public HttpProxy() {
		super();
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Boolean useHttps() {
		return useHttps;
	}

	public void setUseHttps(Boolean useHttps) {
		this.useHttps = useHttps;
	}
	
	
}
