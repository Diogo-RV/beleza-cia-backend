package br.com.belezaecia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BelezaCiaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BelezaCiaBackendApplication.class, args);
	}

}
