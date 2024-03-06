package primemover.runner.joblist;

import java.util.function.Supplier;

/**
 * Marker interface for running joblists
 * 
 * @author domin
 *
 */
public interface IJobListRunner extends Supplier<Boolean> {

	long getJobListId();
}
