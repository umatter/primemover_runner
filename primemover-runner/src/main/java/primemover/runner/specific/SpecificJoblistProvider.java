package primemover.runner.specific;

import primemover.runner.context.IRunnerContext;
import primemover.util.SelectingHelper;

import java.util.List;
import java.util.Optional;

public class SpecificJoblistProvider implements ISpecificJoblistProvider {

	private SpecificJoblistRepository repo = new SpecificJoblistRepository();

	public SpecificJoblistProvider() {
	}

	@Override
	public Optional<Specific> getSpecificJoblist(IRunnerContext ctx) {

		List<Specific> match = repo.getAllSpecific().stream().filter(item -> {

			ctx.getDriver().getPageSource();

			return !SelectingHelper.getElementsByWebdriver(ctx.getDriver(), item.getType(), item.getSelector())
					.isEmpty();
		}).toList();

		return Optional.empty();
	}

}
