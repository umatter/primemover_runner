package primemover.anticaptcha;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmptyPageController {

	@GetMapping("anticaptcha/")
	public String index() {
		return "Plugin setup page!";
	}

}
