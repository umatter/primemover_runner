package hsg.bot.e2e.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import primemover.api.model.Crawler;

public class TestSet {

	@Nonnull
	private List<Crawler> data = new ArrayList<>();

	public TestSet() {
	}

	@Nonnull
	public List<Crawler> getData() {
		return Collections.unmodifiableList(data);
	}

}
