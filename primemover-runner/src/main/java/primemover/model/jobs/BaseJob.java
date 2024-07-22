package primemover.model.jobs;

import primemover.anticaptcha.CaptchaSolvingType;
import primemover.model.IJob;

public abstract class BaseJob implements IJob {

	private Long id;
	private String description;
	private String name;
	private String task;
	private CaptchaSolvingType captchaSolving = CaptchaSolvingType.NEVER;

	private boolean runFailsave = FAILSAVE_DEFAULT;
	private boolean resetPreviousFailure = RESET_PREVOUS_FAILURE_DEFAULT;

	public BaseJob() {
		super();
	}

	@Override
	public String getTask() {
		return task;
	}

	public void setTask(String task) {
		this.task = task;
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public void setDescription(String reference) {
		this.description = reference;
	}

	public void setName(String title) {
		this.name = title;
	}

	public void setCaptchaSolving(CaptchaSolvingType type) {
		this.captchaSolving = type;
	}

	@Override
	public CaptchaSolvingType getCaptchaSolving() {
		return this.captchaSolving;
	}

	public void setIsFailsave(Boolean value) {
		this.runFailsave = value;
	}

	@Override
	public boolean isFailsave() {
		return this.runFailsave;
	}

	@Override
	public boolean resetPreviousFailure() {
		return resetPreviousFailure;
	}

	public void setResetPreviousFailure(boolean resetPreviousFailure) {
		this.resetPreviousFailure = resetPreviousFailure;
	}

	public BaseJob(Long id, String name, String description) {
		this(id, name, description, null, FAILSAVE_DEFAULT);
	}

	public BaseJob(Long id, String name, String description, String task) {
		this(id, name, description, task, FAILSAVE_DEFAULT);
	}

	public BaseJob(Long id, String name, String description, String task, Boolean isFailsave) {
		this(id, name, description, task, isFailsave, BaseJob.RESET_PREVOUS_FAILURE_DEFAULT);
	}

	public BaseJob(Long id, String name, String description, String task, boolean isFailsave,
			boolean resetPreviousFailure) {
		super();
		this.id = id;
		this.name = name;
		this.description = description;
		this.task = task;
		this.runFailsave = isFailsave;
		this.resetPreviousFailure = resetPreviousFailure;
	}

}