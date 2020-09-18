package multilogin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import primemover.capture.RecordingProxy;
import primemover.model.Crawler;
import primemover.model.IAgent;
import primemover.model.MultiLoginAgent;
import primemover.util.MulitLoginBrowserProfile;

@SpringBootTest
class AutocreationOfMultiloginProfiles {
		
	private IAgent agent;
	private Crawler crawler;
	private RecordingProxy recorder;
	
	@BeforeEach
	public void setup() {
		// create a recording proxy
		recorder = new RecordingProxy();
		recorder.setup();
		agent = new MultiLoginAgent(10000l, "Testing", "Testing", "", "");
		
		crawler = new Crawler();
		crawler.setAgent(agent);
	}

	@Test
	void createNewProfile() throws Exception {
		MulitLoginBrowserProfile bp = new MulitLoginBrowserProfile(crawler, recorder);

		Assert.hasText(bp.getProfileId(), "Profile was not created");
		
		bp.deleteProfile();
	}
	
	@AfterEach
    public void tearDown() {
        
    }
}
