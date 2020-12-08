package geosurf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import primemover.capture.RecordingProxy;
import primemover.capture.RecordingProxyFactory;
import primemover.model.GeoSurfProxy;
import primemover.model.LocationInfo;
import primemover.service.IpService;
import primemover.service.IpServiceFactory;

@SpringBootTest
class GeosurfIpVerificationTest {
	
	private static Logger logger = LoggerFactory.getLogger(GeosurfIpVerificationTest.class);
	
	
	
	@Autowired
	private RecordingProxyFactory recFac;	
	private List<String> cities = java.util.Arrays.asList("US-CO-COLORADO_SPRINGS",
			"US-OK-OKLAHOMA_CITY",
			"US-KS-WICHITA",
			"US-CA-BAKERSFIELD",
			"US-FL-JACKSONVILLE",
			"US-NE-OMAHA",
			"US-OH-DAYTON",
			"US-WA-SPOKANE",
			"US-AZ-PHOENIX",
			"US-PA-HARRISBURG",
			"US-IL-CHICAGO",
			"US-CO-DENVER",
			"US-MI-DETROIT",
			"US-TX-HOUSTON",
			"US-NY-NEW_YORK",
			"US-CA-SAN_DIEGO",
			"US-CA-SAN_FRANCISCO",
			"US-WI-MADISON",
			"US-CA-SAN_JOSE",
			"US-DC-WASHINGTON",
			"US-TX-EL_PASO",
			"US-WA-SEATTLE",
			"US-OR-PORTLAND",
			"US-CA-STOCKTON",
			"US-MA-BOSTON");
	
	@Autowired
	IpServiceFactory ipServiceFactory;
	
	/**
	 * 
	 * @param city: target according to GeoSurf API e.g. "US-TX-HOUSTON"
	 */
	void verifyIpIOver20RunsForCity(String city) {
		int numRuns = 20;
		
		String failIP = "(fail)";
		GeoSurfProxy targetProxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password", city);		
		RecordingProxy rec = recFac.createRecordingProxy();
		rec.setup(targetProxy);
		
		IpService ipService = ipServiceFactory.getInstance(rec);
		
		List<String> ipInfo = new ArrayList<String>(20);
		
		for(int n = 1; n <= numRuns; n++) {
			logger.debug("run {} of {}", n, numRuns);
			LocationInfo locInfo = ipService.getLocationInfo();
			
			if (locInfo == null) {
				ipInfo.add(failIP);
			} else {
				ipInfo.add(locInfo.getIp());
			}
			
			
			// add some delay in between calls
			try {
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				// nothing to do
			}
		}
		

		rec.tearDown(true);
		
		// log results
		StringBuilder builder = new StringBuilder();
		builder.append("IPs for ");
		builder.append(city);
		builder.append(": ");
		for (String ip : ipInfo) {
			builder.append(ip);
			builder.append(" ");
		}
		logger.debug(builder.toString());
		
		
		// assert results
		String expected = ipInfo.get(0);
		for (String actual : ipInfo) {
			assertEquals(expected, actual, "IPs not equal " + builder.toString());
			assertNotEquals(failIP, actual, "IP was not retrieved");
		}
	}
	
	
	@TestFactory
	@Execution(ExecutionMode.CONCURRENT)
	Stream<DynamicTest> dynamicTestsFromStreamInJava8() {
	    return cities.stream()
	      .map(city -> DynamicTest.dynamicTest("verifiying IP over 20 run for " + city + "", 
	        () -> {
	        	verifyIpIOver20RunsForCity(city);
	    }));       
	}
	
}
