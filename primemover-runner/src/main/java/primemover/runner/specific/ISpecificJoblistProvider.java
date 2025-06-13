package primemover.runner.specific;

import primemover.runner.context.IRunnerContext;

import java.util.Optional;

public interface ISpecificJoblistProvider {

	Optional<Specific> getSpecificJoblist(IRunnerContext ctx);

}
