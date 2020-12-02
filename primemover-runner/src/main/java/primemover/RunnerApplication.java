package primemover;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RunnerApplication {

	public static void main(String[] args) {	
		SpringApplication app = new SpringApplication(RunnerApplication.class);
	    app.run(args);
	}

}
