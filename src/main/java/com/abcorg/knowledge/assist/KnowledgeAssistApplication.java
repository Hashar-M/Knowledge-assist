package com.abcorg.knowledge.assist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KnowledgeAssistApplication {

	public static void main(String[] args) {
		SpringApplication.run(KnowledgeAssistApplication.class, args);
	}

}
