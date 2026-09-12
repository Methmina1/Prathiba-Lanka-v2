package com.example.PrathibaLanka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan("com.example.PrathibaLanka.entity")
public class PrathibaLankaApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrathibaLankaApplication.class, args);
	}

}
