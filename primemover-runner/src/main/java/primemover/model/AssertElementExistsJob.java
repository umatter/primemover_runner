package primemover.model;

public class AssertElementExistsJob extends SelectorBaseJob {

	public AssertElementExistsJob() {
		super();
	}

	public AssertElementExistsJob(Long id, String reference, String title, SelectorType type, String selector) {
		super(id, reference, title, type, selector);
	}
	
}
