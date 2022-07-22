package multilogin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.MultiLoginAgent;
import primemover.model.proxy.GeoSurfProxy;
import primemover.model.proxy.HttpProxy;
import primemover.multilogin.MulitLoginBrowserProfile;
import primemover.multilogin.MultiLoginBrowserProfileFactory;

@SpringBootTest
class AutocreationOfMultiloginProfiles {

	private IAgent agent;
	private Crawler crawler;

	@Autowired()
	private RecordingProxyFactory recorderFactory;
	private RecordingProxy recorder;

	@Autowired()
	MultiLoginBrowserProfileFactory browserProfileFactory;

	@BeforeEach
	public void setup() {
		// create a recording proxy
		recorder = recorderFactory.createRecordingProxy();
		recorder.setup();

		crawler = new Crawler();
	}

	@Test
	void createNewProfileWithNoTemplate() throws Exception {

		agent = new MultiLoginAgent(10000l, "Testing", "Testing", "", "", "");
		crawler.setAgent(agent);

		MulitLoginBrowserProfile bp = browserProfileFactory.getItem(crawler, recorder);

		Assert.hasText(bp.getProfileId(), "Profile was not created");

		bp.deleteProfile();
	}

	@Test
	void createNewProfileWithTemplate() throws Exception {
		agent = new MultiLoginAgent(10000l, "Testing", "Testing", "", "",
				"{\n" + "	\"name\": \"OVERWRITTEN_NAME\",\n" + "	\"os\": \"%%OS%%\",\n"
						+ "	\"browser\": \"%%BROWSER%%\",\n" + "	\"network\": {\n" + "		\"proxy\": {\n"
						+ "			\"type\": \"%%PROXYTYPE%%\",\n" + "			\"host\": \"%%PROXYHOST%%\",\n"
						+ "			\"port\": \"%%PROXYPORT%%\",\n" + "			\"username\": \"%%PROXYUSERNAME%%\",\n"
						+ "			\"password\": \"%%PROXYPASSWORD%%\"\n" + "		},\n" + "		\"dns\": []\n"
						+ "	}\n" + "}");
		crawler.setAgent(agent);

		try {
			MulitLoginBrowserProfile bp = browserProfileFactory.getItem(crawler, recorder);
			Assert.hasText(bp.getProfileId(), "Profile was not created");

			bp.deleteProfile();
		} catch (Exception ex) {
			ex.printStackTrace();

			fail("Profile was not created");
		}

	}

	@Test
	void createNewProfileWithTemplateAndGeoSurfProxy() throws Exception {
		agent = new MultiLoginAgent(10000l, "Testing", "Testing", "", "",
				"{\n" + "	\"name\": \"OVERWRITTEN_NAME\",\n" + "	\"os\": \"%%OS%%\",\n"
						+ "	\"browser\": \"%%BROWSER%%\",\n" + "	\"network\": {\n" + "		\"proxy\": {\n"
						+ "			\"type\": \"%%PROXYTYPE%%\",\n" + "			\"host\": \"%%PROXYHOST%%\",\n"
						+ "			\"port\": \"%%PROXYPORT%%\",\n" + "			\"username\": \"%%PROXYUSERNAME%%\",\n"
						+ "			\"password\": \"%%PROXYPASSWORD%%\"\n" + "		},\n" + "		\"dns\": []\n"
						+ "	}\n" + "}");
		crawler.setAgent(agent);

		// Defined proxy for US and city of New York for test!
		HttpProxy proxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password", "US-NY-NEW_YORK");
		crawler.setProxy(proxy);

		MulitLoginBrowserProfile bp = browserProfileFactory.getItem(crawler, recorder);

		Assert.hasText(bp.getProfileId(), "Profile was not created");

		bp.deleteProfile();
	}

	@Test
	void createCurrentWithTemplateAndGeoSurfProxy() throws Exception {
		String mlProfile = "{\"name\":\"%%AGENTID%%\",\"os\":\"win\",\"browser\":\"mimic\",\"network\":{\"proxy\":{\"job_type\":\"%%PROXYTYPE%%\",\"host\":\"%%PROXYHOST%%\",\"port\":\"%%PROXYPORT%%\",\"username\":\"%%PROXYUSERNAME%%\",\"password\":\"%%PROXYPASSWORD%%\"},\"dns\":[]},\"navigator\":{\"language\":\"en-US,en;q=0.5\",\"doNotTrack\":0},\"geolocation\":{\"mode\":\"ALLOW\",\"fillBasedOnExternalIp\":true},\"canvas\":{\"mode\":\"NOISE\"},\"storage\":{\"local\":true}}";
		agent = new MultiLoginAgent(10000l, "Testing", "Testing", "", "", mlProfile);
		crawler.setAgent(agent);

		// Defined proxy for US and city of New York for test!
		HttpProxy proxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password", "US-NY-NEW_YORK");
		crawler.setProxy(proxy);

		try {
			MulitLoginBrowserProfile bp = browserProfileFactory.getItem(crawler, recorder);
			Assert.hasText(bp.getProfileId(), "Profile was not created");

			bp.deleteProfile();
		} catch (Exception ex) {
			ex.printStackTrace();

			fail("Profile was not created");
		}

	}

	@AfterEach
	public void tearDown() {
		recorder.tearDown(true);
	}
}
