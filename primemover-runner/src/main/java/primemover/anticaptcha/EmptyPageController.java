package primemover.anticaptcha;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simple endpoint with a blank page. Anticaptcha will open this page for
 * initialisation
 * 
 * @author Dominik Burkolter
 *
 */
@RestController
public class EmptyPageController {

	@GetMapping("anticaptcha/")
	public String index() {
		return "Plugin setup page!";
	}

}
