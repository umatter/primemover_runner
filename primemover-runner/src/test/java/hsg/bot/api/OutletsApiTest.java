package hsg.bot.api;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import primemover.model.Outlet;
import primemover.service.OutletService;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OutletsApiTest {

	@Autowired
	OutletService service;
	
	@Test
	void receiveOutletsFromAPI() {
		List<Outlet> outlets = service.getOutlets();
		
		assertNotNull("result is null", outlets);
		assertTrue("Empty result received", outlets.size() > 0);
	}

}
