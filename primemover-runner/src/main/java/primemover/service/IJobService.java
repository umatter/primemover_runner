package primemover.service;

import java.time.ZonedDateTime;

import primemover.model.IJob;
import primemover.model.JobState;
import primemover.model.Joblist;
import primemover.model.JoblistState;

public interface IJobService {

	Boolean updateJoblistState(Joblist jobs, JoblistState state, ZonedDateTime startTime, ZonedDateTime endTime,
			Boolean processed, Boolean processing);

	Boolean updateCaptchaStateOnJob(IJob job, Boolean captchaFound, Boolean captchaSolved);

	Boolean updateJobState(IJob job, JobState state, Boolean captchaFound, Boolean captchaSolved,
			ZonedDateTime startTime, ZonedDateTime endTime);

}