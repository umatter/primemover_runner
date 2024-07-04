package primemover.service.datawriter.lambda;

import primemover.model.IJob;
import primemover.service.datawriter.IRecordedData;

public class LambdaInvocationData implements IRecordedData {

	private String inputJson;
	private String outputJson;

	private IJob job;

	public LambdaInvocationData() {

	}

	public void setJob(IJob job) {
		this.job = job;
	}

	@Override
	public IJob getJob() {
		return this.job;
	}

	public String getInputJson() {
		return inputJson;
	}

	public void setInputJson(String inputJson) {
		this.inputJson = inputJson;
	}

	public String getOutputJson() {
		return outputJson;
	}

	public void setOutputJson(String outputJson) {
		this.outputJson = outputJson;
	}

}
