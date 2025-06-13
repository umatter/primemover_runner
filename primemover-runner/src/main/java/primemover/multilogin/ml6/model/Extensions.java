package primemover.multilogin.ml6.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import primemover.util.json.deserializer.CsvToListDeserializer;

import java.util.List;

/**
 * Extension-type of MultiLogin - Profile
 * 
 * @author dburkolter
 *
 */
public class Extensions {

	boolean enable;

	@JsonDeserialize(using = CsvToListDeserializer.class)
	List<String> names;

	public Extensions() {
		super();
		enable = true;
		names = null;
	}

	public Extensions(boolean enable, List<String> names) {
		super();
		this.enable = enable;
		this.names = names;
	}

	public boolean isEnables() {
		return enable;
	}

	public void setEnabled(boolean enable) {
		this.enable = enable;
	}

	public List<String> getNames() {
		return names;
	}

	public void setNames(List<String> names) {
		this.names = names;
	}

}
