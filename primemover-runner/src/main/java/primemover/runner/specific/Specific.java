package primemover.runner.specific;

import primemover.model.ISelectorType;
import primemover.model.Joblist;

import java.util.List;

public class Specific {

	private String selector;
	private ISelectorType type;
	private List<Joblist> executionList;

	public Specific(String selector, ISelectorType type, List<Joblist> executionList) {
		this.selector = selector;
		this.type = type;
		this.executionList = executionList;
	}

	public String getSelector() {
		return selector;
	}

	public void setSelector(String selector) {
		this.selector = selector;
	}

	public ISelectorType getType() {
		return type;
	}

	public void setType(ISelectorType type) {
		this.type = type;
	}

	public List<Joblist> getExecutionList() {
		return executionList;
	}

	public void setExecutionList(List<Joblist> executionList) {
		this.executionList = executionList;
	}

}
