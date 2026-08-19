package primemover.runner.job.handler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LambdaSelectJobResult {

	private String xpath;
	private Selected selected;

	public LambdaSelectJobResult() {
	}

	public String getXpath() {
		return xpath;
	}

	public void setXpath(String xpath) {
		this.xpath = xpath;
	}

	public Selected getSelected() {
		return selected;
	}

	public void setSelected(Selected selected) {
		this.selected = selected;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Selected {
		private String id;
		private String title;

		public Selected() {
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}
	}

}
