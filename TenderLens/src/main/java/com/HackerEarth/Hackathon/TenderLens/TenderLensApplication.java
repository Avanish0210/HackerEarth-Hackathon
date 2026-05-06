package com.HackerEarth.Hackathon.TenderLens;

import com.HackerEarth.Hackathon.TenderLens.config.TenderLensProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(TenderLensProperties.class)
public class TenderLensApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenderLensApplication.class, args);
	}

}
