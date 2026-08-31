package primemover.capture;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarLog;
import com.browserup.harreader.model.HarTiming;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A request that never received a response had its {@code wait} computed against
 * an unset response time, so the recorder wrote {@code "time": -1303303604} --
 * roughly minus fifteen days. On job 13451 (2026-08-28) 14 of 53 entries looked
 * like that, and a consumer summing them reported a page total of
 * -18246271267 ms.
 *
 * <p>
 * The numbers below are taken from that capture.
 */
class HarTimingSanitizerTest {

	private static final long MS = 1;

	private static HarEntry entry(Integer time, long blocked, long dns, long connect, long send, long wait,
			long receive) {
		HarEntry e = new HarEntry();
		e.setTime(time);
		HarTiming t = new HarTiming();
		t.setBlocked(blocked, TimeUnit.MILLISECONDS);
		t.setDns(dns, TimeUnit.MILLISECONDS);
		t.setConnect(connect, TimeUnit.MILLISECONDS);
		t.setSend(send, TimeUnit.MILLISECONDS);
		t.setWait(wait, TimeUnit.MILLISECONDS);
		t.setReceive(receive, TimeUnit.MILLISECONDS);
		e.setTimings(t);
		return e;
	}

	private static Har harOf(HarEntry... entries) {
		Har har = new Har();
		HarLog log = new HarLog();
		List<HarEntry> list = new ArrayList<>(Arrays.asList(entries));
		log.setEntries(list);
		har.setLog(log);
		return har;
	}

	/** Verbatim from the capture: no response, absurd wait. */
	private static HarEntry brokenEntry() {
		return entry(-1303303604, -1, -1, 486, 0, -1303304090, 0);
	}

	/** A normal request from the same file. */
	private static HarEntry healthyEntry() {
		return entry(187, -1, -1, -1, 0, 187, 234);
	}

	@Test
	@DisplayName("an impossible duration becomes the format's -1, not a zero")
	void impossibleDurationBecomesNotAvailable() {
		Har har = harOf(brokenEntry());

		assertEquals(1, HarTimingSanitizer.sanitize(har));

		HarEntry e = har.getLog().getEntries().get(0);
		// Zero would be worse than the bug: it reads as an instantaneous request
		// and drags every average down while looking perfectly healthy.
		assertEquals(-1, e.getTime().intValue());
		assertEquals(-1, e.getTimings().getWait(TimeUnit.MILLISECONDS));
	}

	@Test
	@DisplayName("a healthy entry is left completely alone")
	void healthyEntryUntouched() {
		Har har = harOf(healthyEntry());

		assertEquals(0, HarTimingSanitizer.sanitize(har));

		HarEntry e = har.getLog().getEntries().get(0);
		assertEquals(187, e.getTime().intValue());
		assertEquals(187, e.getTimings().getWait(TimeUnit.MILLISECONDS));
		assertEquals(234, e.getTimings().getReceive(TimeUnit.MILLISECONDS));
	}

	@Test
	@DisplayName("a legitimate -1 is not treated as garbage")
	void minusOneIsMeaningful() {
		// -1 is the spec's own "not applicable" and is normal for blocked, dns
		// and ssl on a reused connection. Rewriting it would be noise.
		Har har = harOf(healthyEntry());
		HarTimingSanitizer.sanitize(har);

		HarEntry e = har.getLog().getEntries().get(0);
		assertEquals(-1, e.getTimings().getBlocked(TimeUnit.MILLISECONDS));
		assertEquals(-1, e.getTimings().getDns(TimeUnit.MILLISECONDS));
		assertEquals(187, e.getTime().intValue(), "a healthy total must survive");
	}

	@Test
	@DisplayName("the real 14-of-53 mix is corrected without touching the rest")
	void correctsOnlyTheBrokenOnes() {
		List<HarEntry> entries = new ArrayList<>();
		for (int i = 0; i < 39; i++) {
			entries.add(healthyEntry());
		}
		for (int i = 0; i < 14; i++) {
			entries.add(brokenEntry());
		}
		Har har = harOf(entries.toArray(new HarEntry[0]));

		assertEquals(14, HarTimingSanitizer.sanitize(har));

		long healthy = har.getLog().getEntries().stream().filter(e -> e.getTime() == 187).count();
		assertEquals(39, healthy);

		// The sum a consumer would compute is now a real duration.
		int total = har.getLog().getEntries().stream().mapToInt(e -> Math.max(e.getTime(), 0)).sum();
		assertEquals(39 * 187, total);
	}

	@Test
	@DisplayName("a negative total alone is enough, even with plausible parts")
	void negativeTotalWithPlausibleParts() {
		Har har = harOf(entry(-5, 1, 1, 1, 1, 1, 1));
		assertEquals(1, HarTimingSanitizer.sanitize(har));
		assertEquals(-1, har.getLog().getEntries().get(0).getTime().intValue());
	}

	@Test
	@DisplayName("null and empty archives are tolerated")
	void toleratesNothingToDo() {
		// The recorder can be torn down before anything was captured; throwing
		// here would fail a job over a bookkeeping detail.
		assertEquals(0, HarTimingSanitizer.sanitize(null));
		assertEquals(0, HarTimingSanitizer.sanitize(new Har()));
		assertEquals(0, HarTimingSanitizer.sanitize(harOf()));
	}

	@Test
	@DisplayName("an entry with no timings at all does not blow up")
	void toleratesMissingTimings() {
		HarEntry e = new HarEntry();
		e.setTime(-42);
		Har har = harOf(e);

		assertEquals(1, HarTimingSanitizer.sanitize(har));
		assertEquals(-1, har.getLog().getEntries().get(0).getTime().intValue());
	}
}
