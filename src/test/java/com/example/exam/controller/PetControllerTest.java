package com.example.exam.controller;

import java.time.LocalDateTime;

import com.example.exam.exception.PetNotFoundException;
import com.example.exam.exception.PetStoreUnavailableException;
import com.example.exam.model.PetRegistration;
import com.example.exam.model.request.CreatePetRequest;
import com.example.exam.model.response.CreatePetResponse;
import com.example.exam.model.response.PetResponse;
import com.example.exam.service.PetService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PetController.class)
class PetControllerTest {

	private static final long PET_ID = 10_000_023L;

	private static final String VALID_BODY = """
			{ "id": 10000023, "status": "available", "name": "testingPet1" }
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private PetService petService;

	@Test
	void getPetExposesTheIdNameAndStatus() throws Exception {
		given(this.petService.findPet(PET_ID)).willReturn(new PetResponse(PET_ID, "testingPet1", "available"));

		this.mockMvc.perform(get("/api/pet/{petId}", PET_ID))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(PET_ID))
			.andExpect(jsonPath("$.name").value("testingPet1"))
			.andExpect(jsonPath("$.status").value("available"));
	}

	@Test
	void getPetAnswersNotFoundWhenThePetDoesNotExist() throws Exception {
		willThrow(new PetNotFoundException(PET_ID)).given(this.petService).findPet(PET_ID);

		this.mockMvc.perform(get("/api/pet/{petId}", PET_ID))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Mascota no encontrada"))
			.andExpect(jsonPath("$.petId").value(PET_ID));
	}

	@Test
	void getPetAnswersBadGatewayWhenThePetStoreIsUnreachable() throws Exception {
		willThrow(new PetStoreUnavailableException("upstream down")).given(this.petService).findPet(PET_ID);

		this.mockMvc.perform(get("/api/pet/{petId}", PET_ID))
			.andExpect(status().isBadGateway())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Tienda no disponible"))
			.andExpect(jsonPath("$.detail").value(not(containsString("upstream down"))));
	}

	@Test
	void anUnexpectedFailureIsReportedWithoutLeakingInternalDetails() throws Exception {
		willThrow(new IllegalStateException("connection pool exhausted on node db-07")).given(this.petService)
			.findPet(PET_ID);

		this.mockMvc.perform(get("/api/pet/{petId}", PET_ID))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Error interno"))
			.andExpect(content().string(not(containsString("db-07"))));
	}

	@Test
	void getPetRejectsANonPositiveIdWithoutReachingTheService() throws Exception {
		this.mockMvc.perform(get("/api/pet/{petId}", -1)).andExpect(status().isBadRequest());

		then(this.petService).should(never()).findPet(anyLong());
	}

	@Test
	void getPetRejectsANonNumericIdWithoutReachingTheService() throws Exception {
		this.mockMvc.perform(get("/api/pet/{petId}", "abc")).andExpect(status().isBadRequest());

		then(this.petService).should(never()).findPet(anyLong());
	}

	@Test
	void createPetAnswersCreatedWithTheTransactionAndItsLocation() throws Exception {
		CreatePetResponse response = new CreatePetResponse("60cc5c22-3250-4e07-a519-a6dab99c6713",
				LocalDateTime.parse("2024-06-25T19:22:42.181753"), true, "testingPet1");
		given(this.petService.registerPet(new CreatePetRequest(PET_ID, "available", "testingPet1")))
			.willReturn(new PetRegistration(PET_ID, response));

		this.mockMvc.perform(post("/api/pet").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/pet/" + PET_ID))
			.andExpect(jsonPath("$.transactionId").value("60cc5c22-3250-4e07-a519-a6dab99c6713"))
			.andExpect(jsonPath("$.dateCreated").value("2024-06-25T19:22:42.181753"))
			.andExpect(jsonPath("$.status").value(true))
			.andExpect(jsonPath("$.name").value("testingPet1"));
	}

	@Test
	void createPetPointsToTheIdentifierAssignedByThePetStore() throws Exception {
		CreatePetResponse response = new CreatePetResponse("60cc5c22-3250-4e07-a519-a6dab99c6713",
				LocalDateTime.parse("2024-06-25T19:22:42.181753"), true, "testingPet1");
		given(this.petService.registerPet(new CreatePetRequest(PET_ID, "available", "testingPet1")))
			.willReturn(new PetRegistration(987_654_321L, response));

		this.mockMvc.perform(post("/api/pet").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "http://localhost/api/pet/987654321"));
	}

	@Test
	void createPetRejectsAnIncompleteBodyAndDetailsEveryInvalidField() throws Exception {
		this.mockMvc.perform(post("/api/pet").contentType(MediaType.APPLICATION_JSON).content("{ \"id\": 0 }"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.title").value("Solicitud inválida"))
			.andExpect(jsonPath("$.errors.id").exists())
			.andExpect(jsonPath("$.errors.name").exists())
			.andExpect(jsonPath("$.errors.status").exists());

		then(this.petService).should(never()).registerPet(any());
	}

	@Test
	void everyResponseCarriesACorrelationIdentifier() throws Exception {
		given(this.petService.findPet(PET_ID)).willReturn(new PetResponse(PET_ID, "testingPet1", "available"));

		this.mockMvc.perform(get("/api/pet/{petId}", PET_ID))
			.andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER));
	}

	@Test
	void aCallerSuppliedCorrelationIdentifierIsEchoedBack() throws Exception {
		given(this.petService.findPet(PET_ID)).willReturn(new PetResponse(PET_ID, "testingPet1", "available"));

		this.mockMvc.perform(get("/api/pet/{petId}", PET_ID).header(RequestIdFilter.REQUEST_ID_HEADER, "trace-0001"))
			.andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "trace-0001"));
	}

	@Test
	void anUnsafeCallerSuppliedCorrelationIdentifierIsReplaced() throws Exception {
		given(this.petService.findPet(PET_ID)).willReturn(new PetResponse(PET_ID, "testingPet1", "available"));

		this.mockMvc.perform(get("/api/pet/{petId}", PET_ID).header(RequestIdFilter.REQUEST_ID_HEADER, "bad id\nINFO fake"))
			.andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, not("bad id\nINFO fake")));
	}

}
