package multilogin;

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
import primemover.util.MulitLoginBrowserProfile;
import primemover.util.MultiLoginBrowserProfileFactory;

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
		agent = new MultiLoginAgent(10000l, "Testing", "Testing", "", "", "{\n" + 
				"	\"name\": \"OVERWRITTEN_NAME\",\n" + 
				"	\"os\": \"%%OS%%\",\n" + 
				"	\"browser\": \"%%BROWSER%%\",\n" + 
				"	\"network\": {\n" + 
				"		\"proxy\": {\n" + 
				"			\"type\": \"%%PROXYTYPE%%\",\n" + 
				"			\"host\": \"%%PROXYHOST%%\",\n" + 
				"			\"port\": \"%%PROXYPORT%%\",\n" + 
				"			\"username\": \"%%PROXYUSERNAME%%\",\n" + 
				"			\"password\": \"%%PROXYPASSWORD%%\"\n" + 
				"		},\n" + 
				"		\"dns\": []\n" + 
				"	}\n" + 
				"}");
		crawler.setAgent(agent);
		
		MulitLoginBrowserProfile bp = browserProfileFactory.getItem(crawler, recorder);

		Assert.hasText(bp.getProfileId(), "Profile was not created");
		
		bp.deleteProfile();
	}
	
	@AfterEach
    public void tearDown() {
        recorder.tearDown(true);
    }
}
