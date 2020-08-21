package primemover.model;

public class AssertElementExistsJob extends SelectorBaseJob {

	public AssertElementExistsJob() {
		super();
	}

	public AssertElementExistsJob(Long id, String name, String description, SelectorType type, String selector) {
		super(id, name, description, type, selector);
	}
	
}
