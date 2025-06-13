package primemover.multilogin.mlx;

public class StartProfileResult {
	private String port;
	private String server;
	private boolean success;
	
	
	public boolean isSuccess() {
		return success;
	}

	public StartProfileResult(boolean success, String port, String server) {
		super();
		this.success = success;
		this.port = port;
		this.server = server;
	}
	
	public StartProfileResult(String port) {
		super();
		this.port = port;
		this.server = "localhost";
	}
	
	public String getPort() {
		return port;
	}
	public String getServer() {
		return server;
	}}
