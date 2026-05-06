package com.HackerEarth.Hackathon.ubidbridge;

import com.HackerEarth.Hackathon.ubidbridge.config.UbidBridgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(UbidBridgeProperties.class)
public class UbidBridgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(UbidBridgeApplication.class, args);
	}

}
