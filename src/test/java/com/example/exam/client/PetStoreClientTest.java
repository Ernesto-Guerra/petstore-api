package com.example.exam.client;

import com.example.exam.exception.PetNotFoundException;
import com.example.exam.exception.PetStoreUnavailableException;
import com.example.exam.model.Pet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PetStoreClientTest {

	private static final String BASE_URL = "https://petstore.example.com/v2";

	private static final long PET_ID = 10_000_023L;

	private static final String UPSTREAM_PET = """
			{
			  "id": 10000023,
			  "category": { "id": 1, "name": "dogs" },
			  "name": "testingPet1",
			  "photoUrls": ["https://example.com/pet.png"],
			  "tags": [{ "id": 1, "name": "friendly" }],
			  "status": "available"
			}
			""";

	@Autowired
	private RestClient.Builder restClientBuilder;

	@Autowired
	private MockRestServiceServer server;

	private PetStoreClient petStoreClient;

	/**
	 * El servidor simulado solo puede vincularse a un {@code RestClient}, por lo que el cliente
	 * se arma una sola vez para toda la clase y sus expectativas se reinician en cada prueba.
	 */
	@BeforeAll
	void setUp() {
		this.petStoreClient = new PetStoreClient(this.restClientBuilder.baseUrl(BASE_URL).build());
	}

	@AfterEach
	void tearDown() {
		this.server.reset();
	}

	@Test
	void findByIdMapsTheUpstreamPayloadOntoTheDomainModel() {
		this.server.expect(requestTo(BASE_URL + "/pet/" + PET_ID))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(UPSTREAM_PET, MediaType.APPLICATION_JSON));

		Pet pet = this.petStoreClient.findById(PET_ID);

		assertThat(pet).isEqualTo(new Pet(PET_ID, "testingPet1", "available"));
		this.server.verify();
	}

	@Test
	void findByIdReportsAMissingPetAsADomainNotFound() {
		this.server.expect(requestTo(BASE_URL + "/pet/" + PET_ID)).andRespond(withResourceNotFound());

		assertThatExceptionOfType(PetNotFoundException.class)
			.isThrownBy(() -> this.petStoreClient.findById(PET_ID))
			.satisfies(ex -> assertThat(ex.petId()).isEqualTo(PET_ID));
	}

	@Test
	void findByIdReportsAnUpstreamErrorAsAnAvailabilityFailure() {
		this.server.expect(requestTo(BASE_URL + "/pet/" + PET_ID)).andRespond(withServerError());

		assertThatExceptionOfType(PetStoreUnavailableException.class)
			.isThrownBy(() -> this.petStoreClient.findById(PET_ID));
	}

	@Test
	void findByIdRejectsAnEmptyUpstreamBody() {
		this.server.expect(requestTo(BASE_URL + "/pet/" + PET_ID))
			.andRespond(withStatus(HttpStatus.NO_CONTENT));

		assertThatExceptionOfType(PetStoreUnavailableException.class)
			.isThrownBy(() -> this.petStoreClient.findById(PET_ID));
	}

	@Test
	void findByIdRejectsAPayloadWithoutIdentity() {
		this.server.expect(requestTo(BASE_URL + "/pet/" + PET_ID))
			.andRespond(withSuccess("""
					{ "name": "testingPet1", "status": "available" }
					""", MediaType.APPLICATION_JSON));

		assertThatExceptionOfType(PetStoreUnavailableException.class)
			.isThrownBy(() -> this.petStoreClient.findById(PET_ID));
	}

	@Test
	void findByIdAcceptsAPetThatHasNoStatusYet() {
		this.server.expect(requestTo(BASE_URL + "/pet/" + PET_ID))
			.andRespond(withSuccess("""
					{ "id": 10000023, "name": "testingPet1" }
					""", MediaType.APPLICATION_JSON));

		assertThat(this.petStoreClient.findById(PET_ID)).isEqualTo(new Pet(PET_ID, "testingPet1", null));
	}

	@Test
	void saveRejectsAReceiptWithoutTheStoredName() {
		this.server.expect(requestTo(BASE_URL + "/pet"))
			.andRespond(withSuccess("""
					{ "id": 10000023, "status": "available" }
					""", MediaType.APPLICATION_JSON));

		assertThatExceptionOfType(PetStoreUnavailableException.class)
			.isThrownBy(() -> this.petStoreClient.save(new Pet(PET_ID, "testingPet1", "available")));
	}

	@Test
	void saveSendsTheIdNameAndStatusAndMapsTheStoredPet() {
		this.server.expect(requestTo(BASE_URL + "/pet"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(PET_ID))
			.andExpect(jsonPath("$.name").value("testingPet1"))
			.andExpect(jsonPath("$.status").value("available"))
			.andRespond(withSuccess(UPSTREAM_PET, MediaType.APPLICATION_JSON));

		Pet stored = this.petStoreClient.save(new Pet(PET_ID, "testingPet1", "available"));

		assertThat(stored).isEqualTo(new Pet(PET_ID, "testingPet1", "available"));
		this.server.verify();
	}

	@Test
	void saveReportsAnUpstreamErrorAsAnAvailabilityFailure() {
		this.server.expect(requestTo(BASE_URL + "/pet")).andRespond(withServerError());

		assertThatExceptionOfType(PetStoreUnavailableException.class)
			.isThrownBy(() -> this.petStoreClient.save(new Pet(PET_ID, "testingPet1", "available")));
	}

}
