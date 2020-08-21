package primemover.model;

public class SingleSelectUrlJob extends SelectorBaseJob {
	
	private DecisionType decision;

	public DecisionType getDecisionType() {
		return decision;
	}


	public void setDecisionType(DecisionType decision) {
		this.decision = decision;
	}

	public SingleSelectUrlJob() {
		super();
	}


	public SingleSelectUrlJob(Long id, String name, String description, SelectorType type, String selector,
			DecisionType decision) {
		super(id, name, description, type, selector);
		this.decision = decision;
	}
	
}
