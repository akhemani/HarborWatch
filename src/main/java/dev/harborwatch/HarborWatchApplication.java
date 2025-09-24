package dev.harborwatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class HarborWatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(HarborWatchApplication.class, args);
	}

}
