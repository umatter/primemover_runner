package primemover.model;

/**
 * Proxy interface to prepare for special treatment of certain global proxy providers to be implemented in specialiced classes
 * 
 * @author dburkolter
 *
 */
public interface IProxy {	
	public String getUrl();
	public String getUsername();
	public String getPassword();
}
