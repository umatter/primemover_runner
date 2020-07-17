package primemover.model;

public class SingleSelectUrl extends SelectorBaseJob {
	
	private DecisionType decision;

	public DecisionType getDecision() {
		return decision;
	}


	public void setDecision(DecisionType decision) {
		this.decision = decision;
	}

	public SingleSelectUrl() {
		super();
	}


	public SingleSelectUrl(Long id, String reference, String title, SelectorType type, String selector,
			DecisionType decision) {
		super(id, reference, title, type, selector);
		this.decision = decision;
	}
	
}
