package com.enterprise.ticketmaster;

import com.enterprise.ticketmaster.model.Priority;
import com.enterprise.ticketmaster.model.Status;
import com.enterprise.ticketmaster.model.Ticket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TicketmasterApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	// Test 1: App context loads without errors
	@Test
	void contextLoads() {
	}

	// Test 2: Authenticated developer can create a ticket and gets 201 back
	@Test
	@WithMockUser(username = "dev_user", roles = "DEVELOPER")
	void createTicket_returns201() throws Exception {
		Ticket ticket = new Ticket();
		ticket.setTitle("Test Ticket");
		ticket.setDescription("This is a test ticket description.");
		ticket.setPriority(Priority.HIGH);

		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ticket)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Test Ticket"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.priority").value("HIGH"));
	}

	// Test 3: Authenticated user can fetch all tickets
	@Test
	@WithMockUser(username = "dev_user", roles = "DEVELOPER")
	void getAllTickets_returnsOkStatus() throws Exception {
		mockMvc.perform(get("/api/tickets"))
				.andExpect(status().isOk());
	}

	// Test 4: Blank title is rejected with 400 Bad Request — validation is working
	@Test
	@WithMockUser(username = "dev_user", roles = "DEVELOPER")
	void createTicket_withBlankTitle_returns400() throws Exception {
		Ticket ticket = new Ticket();
		ticket.setTitle("");  // Blank title should fail @NotBlank validation
		ticket.setDescription("Some description");
		ticket.setPriority(Priority.LOW);

		mockMvc.perform(post("/api/tickets")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ticket)))
				.andExpect(status().isBadRequest());
	}

	// Test 5: Developer cannot delete a ticket — RBAC is enforced
	@Test
	@WithMockUser(username = "dev_user", roles = "DEVELOPER")
	void deleteTicket_asDeveloper_returns403() throws Exception {
		mockMvc.perform(delete("/api/tickets/1"))
				.andExpect(status().isForbidden());
	}

	// Test 6: Filtering tickets by status returns 200
	@Test
	@WithMockUser(username = "dev_user", roles = "DEVELOPER")
	void getTicketsByStatus_returnsOkStatus() throws Exception {
		mockMvc.perform(get("/api/tickets/status/OPEN"))
				.andExpect(status().isOk());
	}
}