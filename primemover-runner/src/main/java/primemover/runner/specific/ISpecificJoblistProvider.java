package primemover.runner.specific;

import java.util.Optional;

import primemover.runner.context.IRunnerContext;

public interface ISpecificJoblistProvider {

	Optional<Specific> getSpecificJoblist(IRunnerContext ctx);

}
