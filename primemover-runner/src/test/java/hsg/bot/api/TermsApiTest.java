package hsg.bot.api;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import primemover.model.Term;
import primemover.service.TermsService;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TermsApiTest {

	@Autowired
	TermsService service;
	
	@Test
	void receiveOutletsFromAPI() {
		List<Term> terms = service.getTerms();
		
		assertNotNull("result is null", terms);
		assertTrue("Empty result received", terms.size() > 0);
	}

}
