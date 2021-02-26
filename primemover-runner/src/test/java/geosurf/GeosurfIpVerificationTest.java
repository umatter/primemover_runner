package geosurf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
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
	private List<String> cities = java.util.Arrays.asList(
			"US-CO-COLORADO_SPRINGS",
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
	void verifyIpIOverXRunsForCity(String city, Integer numRuns, Integer waitMillis) {		
		String failIP = "(fail)";
		GeoSurfProxy targetProxy = new GeoSurfProxy("state.geosurf.io", 8000, "000000", "CHANGEME-password", city);		
		RecordingProxy rec = recFac.createRecordingProxy();
		rec.setup(targetProxy);
		
		IpService ipService = ipServiceFactory.getInstance(rec);
		

		Set<String> ipMap = new HashSet<>();
		List<String> ipList = new ArrayList<String>(20);
		
		for(int n = 1; n <= numRuns; n++) {
			logger.debug("run {} of {}", n, numRuns);
			LocationInfo locInfo = ipService.getLocationInfo();
			
			if (locInfo == null) {
				ipMap.add(failIP);
				ipList.add(failIP);
			} else {
				ipMap.add(locInfo.getIp());
				ipList.add(locInfo.getIp());
			}
			
			
			// add some delay in between calls
			try {
				Thread.sleep(waitMillis);
			} catch (InterruptedException e) {
				// nothing to do
			}
		}
		

		rec.tearDown(true);
		
		// log results
		StringBuilder builder = new StringBuilder();
		builder.append("Num IPs found: ");
		builder.append(ipMap.size());
		builder.append(StringUtils.CR);
		ipMap.forEach((String) -> {
			builder.append("IP ");
			builder.append(String);
			builder.append(StringUtils.CR);
        });
		String msg = builder.toString();
		logger.debug(msg);
		
		// assert Num results
		assertEquals(1, ipMap.size(), msg);
		
		// assert failIP
		for (String actual : ipList) {
			assertNotEquals(failIP, actual, "IP was not retrieved");
		}
	}
	
	private Stream<DynamicTest> createTests(Integer numRuns, Integer waitMillisInBetween) {
		return cities.stream()
	      .map(city -> DynamicTest.dynamicTest("verifiying IP over " + numRuns + " runs with " + (waitMillisInBetween / 1000) + " seconds wait for " + city + "", 
	        () -> {
	        	verifyIpIOverXRunsForCity(city, numRuns, waitMillisInBetween);
	    }));  
	}
	
	@TestFactory
	@Execution(ExecutionMode.CONCURRENT)
	Stream<DynamicTest> dynamicGeosurfTestsOver10RunsWith90SecondsWait() {
	    return createTests (20, 90000); 
	}
	
	@TestFactory
	@Execution(ExecutionMode.CONCURRENT)
	Stream<DynamicTest> dynamicGeosurfTestsOver10RunsWith60SecondsWait() {
		return createTests (35, 60000);   
	}
	
	
	@TestFactory
	@Execution(ExecutionMode.CONCURRENT)
	Stream<DynamicTest> dynamicGeosurfTestsOver10RunsWith30SecondsWait() {
		return createTests (70, 30000);     
	}
	
	@TestFactory
	@Execution(ExecutionMode.CONCURRENT)
	Stream<DynamicTest> dynamicGeosurfTestsOver10RunsWith10SecondsWait() {
		return createTests (140, 10000);
	}
	
}
