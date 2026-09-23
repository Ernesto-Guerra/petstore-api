package com.example.exam.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.exam.client.PetStoreClient;
import com.example.exam.exception.PetNotFoundException;
import com.example.exam.model.Pet;
import com.example.exam.model.PetRegistration;
import com.example.exam.model.request.CreatePetRequest;
import com.example.exam.model.response.CreatePetResponse;
import com.example.exam.model.response.PetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

	private static final long PET_ID = 10_000_023L;

	private static final Instant FIXED_INSTANT = Instant.parse("2024-06-25T19:22:42.181753Z");

	private static final Pet STORED_PET = new Pet(PET_ID, "testingPet1", "available");

	@Mock
	private PetStoreClient petStoreClient;

	private PetService petService;

	private ListAppender<ILoggingEvent> logAppender;

	@BeforeEach
	void setUp() {
		this.petService = new PetService(this.petStoreClient, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
		this.logAppender = attachLogAppender();
	}

	@AfterEach
	void tearDown() {
		serviceLogger().detachAppender(this.logAppender);
	}

	@Test
	void findPetExposesTheIdNameAndStatusReportedByThePetStore() {
		given(this.petStoreClient.findById(PET_ID)).willReturn(STORED_PET);

		PetResponse response = this.petService.findPet(PET_ID);

		assertThat(response).isEqualTo(new PetResponse(PET_ID, "testingPet1", "available"));
	}

	@Test
	void findPetPrintsThePetOnTheConsoleBeforeReturningIt() {
		given(this.petStoreClient.findById(PET_ID)).willReturn(STORED_PET);

		this.petService.findPet(PET_ID);

		assertThat(loggedMessages()).anySatisfy(message -> assertThat(message).contains("id=" + PET_ID)
			.contains("name=testingPet1")
			.contains("status=available"));
	}

	@Test
	void findPetPropagatesThePetStoreFailure() {
		willThrow(new PetNotFoundException(PET_ID)).given(this.petStoreClient).findById(PET_ID);

		assertThatExceptionOfType(PetNotFoundException.class).isThrownBy(() -> this.petService.findPet(PET_ID));
	}

	@Test
	void registerPetForwardsTheRequestedPetToThePetStore() {
		given(this.petStoreClient.save(STORED_PET)).willReturn(STORED_PET);

		this.petService.registerPet(new CreatePetRequest(PET_ID, "available", "testingPet1"));

		then(this.petStoreClient).should().save(STORED_PET);
	}

	@Test
	void registerPetBuildsTheTransactionWithAVersion4IdAndTheCurrentSystemDate() {
		given(this.petStoreClient.save(STORED_PET)).willReturn(STORED_PET);

		CreatePetResponse response = this.petService
			.registerPet(new CreatePetRequest(PET_ID, "available", "testingPet1"))
			.receipt();

		assertThat(UUID.fromString(response.transactionId()).version()).isEqualTo(4);
		assertThat(response.dateCreated()).isEqualTo(LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC));
		assertThat(response.status()).isTrue();
		assertThat(response.name()).isEqualTo("testingPet1");
	}

	@Test
	void registerPetReportsTheIdentifierAssignedByThePetStore() {
		given(this.petStoreClient.save(STORED_PET)).willReturn(new Pet(987_654_321L, "testingPet1", "available"));

		PetRegistration registration = this.petService
			.registerPet(new CreatePetRequest(PET_ID, "available", "testingPet1"));

		assertThat(registration.petId()).isEqualTo(987_654_321L);
	}

	@Test
	void registerPetIssuesADistinctTransactionIdOnEveryCall() {
		given(this.petStoreClient.save(STORED_PET)).willReturn(STORED_PET);
		CreatePetRequest request = new CreatePetRequest(PET_ID, "available", "testingPet1");

		String first = this.petService.registerPet(request).receipt().transactionId();
		String second = this.petService.registerPet(request).receipt().transactionId();

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void registerPetPrintsTheTransactionOnTheConsoleBeforeReturningIt() {
		given(this.petStoreClient.save(STORED_PET)).willReturn(STORED_PET);

		CreatePetResponse response = this.petService
			.registerPet(new CreatePetRequest(PET_ID, "available", "testingPet1"))
			.receipt();

		assertThat(loggedMessages())
			.anySatisfy(message -> assertThat(message).contains("transactionId=" + response.transactionId())
				.contains("dateCreated=" + response.dateCreated())
				.contains("status=true")
				.contains("name=testingPet1"));
	}

	private List<String> loggedMessages() {
		return this.logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
	}

	private ListAppender<ILoggingEvent> attachLogAppender() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
		appender.start();
		ch.qos.logback.classic.Logger logger = serviceLogger();
		logger.setLevel(Level.INFO);
		logger.addAppender(appender);
		return appender;
	}

	private static ch.qos.logback.classic.Logger serviceLogger() {
		return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PetService.class);
	}

}
