package hsg.bot.e2e.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import primemover.api.model.Crawler;

public class TestSet {

	@Nonnull
	private List<Crawler> crawlers = new ArrayList<>();

	public TestSet() {
	}

	@Nonnull
	public List<Crawler> getCrawlers() {
		return Collections.unmodifiableList(crawlers);
	}

}
