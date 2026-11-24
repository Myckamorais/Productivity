package com.projects.productivity;

import org.springframework.boot.SpringApplication;

public class TestProductivityApplication {

	public static void main(String[] args) {
		SpringApplication.from(ProductivityApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
