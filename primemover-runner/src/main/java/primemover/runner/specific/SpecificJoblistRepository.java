package primemover.runner.specific;

import java.util.ArrayList;
import java.util.List;

public class SpecificJoblistRepository {

	private static String path = "config/specifics/";

	List<Specific> data = null;

	public SpecificJoblistRepository() {
		this.init();
	}

	private List<Specific> readFolder() {
		ArrayList<Specific> out = new ArrayList();

		return out;
	}

	private void init() {
		if (this.data == null) {
			this.data = readFolder();
		}
	}

	public List<Specific> getAllSpecific() {
		return this.data;
	}

}
