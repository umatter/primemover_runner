package primemover.util;

import java.util.List;

public class Selection {
	
	private List<SelectionItem> items;
	private double choice;

	public Selection() {
	}

	public double getChoice() {
		return choice;
	}

	public void setChoice(double choice) {
		this.choice = choice;
	}

	public List<SelectionItem> getItems() {
		return items;
	}

	public void setItems(List<SelectionItem> items) {
		this.items = items;
	}

}
