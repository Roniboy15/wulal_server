package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource(value = "classpath:lulu.yml", factory = YamlPropertySourceFactory.class)

public class FileStorageServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(FileStorageServiceApplication.class, args);
	}

}