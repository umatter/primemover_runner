package proxy.oxylabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.model.LocationInfo;
import primemover.model.proxy.HttpProxy;
import primemover.model.proxy.OxylabsProxy;
import primemover.service.IpService;
import primemover.service.IpServiceFactory;

@SpringBootTest
class FixedIPTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(FixedIPTest.class);

	@Autowired
	private RecordingProxyFactory recFac;

	@Autowired
	IpServiceFactory ipServiceFactory;

	/*
	 * example locations cc-IT-city-rome cc-TH-city-bangkok cc-JP-city-tokyo
	 * cc-AU-city-sydney
	 */

	/*
	 * "proxy": {": "Created via GUI upload", "hostname": "pr.oxylabs.io", "port":
	 * 7777, "username": "primemover_runner", "password": "name":
	 * "Proxy upload TEST", "description: "Primemover12345", "active": true, "type":
	 * "oxylabs"
	 */

	@Test
	void canConnectToFixedIpUsBySessionId() {

		OxylabsProxy targetProxy = new OxylabsProxy("pr.oxylabs.io", 7777, "primemover_runner", "Primemover12345",
				"cc-US-city-chicago");

		RecordingProxy rec = recFac.createRecordingProxy(targetProxy);

		IpService ipService = ipServiceFactory.getInstance(rec);

		LocationInfo locInfo = ipService.getLocationInfo();
		LOGGER.debug("location-info received: {}", locInfo);

		assertNotNull(locInfo);
		assertEquals("US", locInfo.getCountryCode());
	}

	@Test
	void canConnectToFixedIpAusBySessionID() {

		// Australia
		// Host: ctr-1-30m.geosurf.io
		// Port: 8000
		// Login: Account_ID+STATIC-AURS+Account_ID+Session_ID
		// I.e.: 000000+STATIC-AURS+000000-123123
		// IP: 154.81.61.1-224

		HttpProxy targetProxy = new HttpProxy("ctr-1-30m.geosurf.io", 8000, "000000+STATIC-AURS+000000+%%SESSIONID%%",
				"$SyntheticUsersLimbo2022");

		RecordingProxy rec = recFac.createRecordingProxy(targetProxy);

		IpService ipService = ipServiceFactory.getInstance(rec);

		LocationInfo locInfo = ipService.getLocationInfo();
		LOGGER.debug("location-info received: {}", locInfo);

		assertNotNull(locInfo);
		assertEquals("AUS", locInfo.getCountryCode());
	}

	@Test
	void canConnectToFixedIpFraBySessionID() {

		// Australia
		// Host: ctr-1-30m.geosurf.io
		// Port: 8000
		// Login: Account_ID+STATIC-AURS+Account_ID+Session_ID
		// I.e.: 000000+STATIC-DERS+000000-356891
		// IP: 154.81.61.1-224

		HttpProxy targetProxy = new HttpProxy("ctr-1-30m.geosurf.io", 8000, "000000+STATIC-FRRS+000000+%%SESSIONID%%",
				"$SyntheticUsersLimbo2022");

		RecordingProxy rec = recFac.createRecordingProxy(targetProxy);

		IpService ipService = ipServiceFactory.getInstance(rec);

		LocationInfo locInfo = ipService.getLocationInfo();
		LOGGER.debug("location-info received: {}", locInfo);

		assertNotNull(locInfo);
		assertEquals("FR", locInfo.getCountryCode());
	}

	@Test
	void canConnectToFixedIpAusByIpSelection() {

		// Australia
		// Host: ctr-1-30m.geosurf.io
		// Port: 8000
		// Login: Account_ID+STATIC-AURS+Account_ID+Session_ID
		// I.e.: 000000+STATIC-AURS+000000-123123
		// IP: 154.81.61.1-224

		HttpProxy targetProxy = new HttpProxy("ctr-1-30m.geosurf.io", 8000, "000000+STATIC-AURS+154.81.61.30",
				"$SyntheticUsersLimbo2022");

		RecordingProxy rec = recFac.createRecordingProxy(targetProxy);

		IpService ipService = ipServiceFactory.getInstance(rec);

		LocationInfo locInfo = ipService.getLocationInfo();

		LOGGER.debug("location-info received: {}", locInfo);

		assertNotNull(locInfo);
		assertEquals("AUS", locInfo.getCountryCode());
	}
}
