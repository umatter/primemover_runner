package primemover.model.jobs;

import primemover.model.DecisionType;
import primemover.model.SelectorType;

public class SingleSelectUrlJob extends SelectorBaseJob {
	
	private DecisionType decision;
	
	private String criterionSelector; // regular expression

	public DecisionType getDecisionType() {
		return decision;
	}

	public void setDecisionType(DecisionType decision) {
		this.decision = decision;
	}

	public String getCriterionSelector() {
		return this.criterionSelector;
	}
	
	public void setCriterionSelector(String newVal) {
		this.criterionSelector = newVal;
	}
	
	public SingleSelectUrlJob() {
		super();
	}

	public SingleSelectUrlJob(Long id, String name, String description, String task, SelectorType type, String selector,
			DecisionType decision) {
		super(id, name, description, task, type, selector);
		this.decision = decision;
	}
	
}
