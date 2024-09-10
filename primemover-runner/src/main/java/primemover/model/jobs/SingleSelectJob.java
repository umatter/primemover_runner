package primemover.model.jobs;

import primemover.model.DecisionType;
import primemover.model.HtmlSelectorType;

public class SingleSelectJob extends SelectorBaseJob {

	private DecisionType decision;

	private String criteriaSelector;
	private HtmlSelectorType criteriaType;
	private String criteriaExtractor; // regular expression
	private Attribute criteriaBase = Attribute.TEXT;

	public DecisionType getDecisionType() {
		return decision;
	}

	public void setDecisionType(DecisionType decision) {
		this.decision = decision;
	}

	public String getCriteriaSelector() {
		return this.criteriaSelector;
	}

	public void setCriteriaSelector(String newVal) {
		this.criteriaSelector = newVal;
	}

	public String getCriteriaExtractor() {
		return this.criteriaExtractor;
	}

	public void setCriteriaExtractor(String newVal) {
		this.criteriaExtractor = newVal;
	}

	public HtmlSelectorType getCriteriaType() {
		return this.criteriaType;
	}

	public void setCriteriaType(HtmlSelectorType type) {
		this.criteriaType = type;
	}

	public SingleSelectJob() {
		super();
	}

	// new job version 2
	public SingleSelectJob(Long id, String name, String description, String task, HtmlSelectorType clickType,
			String clickSelector, DecisionType decision, String criteriaSelector, HtmlSelectorType criteriaType,
			String criteriaExtractor, Attribute criteriaBase) {
		super(id, name, description, task, clickType, clickSelector);
		this.decision = decision;
		this.criteriaSelector = criteriaSelector;
		this.criteriaExtractor = criteriaExtractor;
		this.criteriaType = criteriaType;
		this.criteriaBase = criteriaBase;
	}

	// old job version 1
	public SingleSelectJob(Long id, String name, String description, String task, HtmlSelectorType type, String selector,
			DecisionType decision, Attribute criteriaBase) {
		super(id, name, description, task, type, selector);
		this.decision = decision;
		this.criteriaType = type;
		this.criteriaSelector = selector;
		this.criteriaExtractor = null;
		this.criteriaBase = Attribute.TEXT;
	}

	public Attribute getCriteriaBase() {
		return criteriaBase;
	}

	public void setCriteriaBase(Attribute criteriaBase) {
		this.criteriaBase = criteriaBase;
	}
}
