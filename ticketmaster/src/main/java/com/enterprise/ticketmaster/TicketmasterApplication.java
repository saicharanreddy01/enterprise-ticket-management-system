package com.enterprise.ticketmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing; // 👈 The missing import!

@SpringBootApplication
@EnableJpaAuditing
public class TicketmasterApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketmasterApplication.class, args);
	}
}