package com.finledger;

import org.springframework.boot.SpringApplication;

public class TestFinledgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinledgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
