package com.example.exam.client;

import com.example.exam.exception.PetNotFoundException;
import com.example.exam.exception.PetStoreUnavailableException;
import com.example.exam.exception.TimeoutException;
import com.example.exam.model.Pet;
import com.example.exam.model.external.PetStorePayload;

import com.example.exam.model.response.PetListResponse;
import com.example.exam.model.response.PetResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Único punto del sistema que conoce el contrato de la tienda: hacia afuera entrega objetos de
 * dominio y excepciones propias, nunca detalles de transporte.
 */
@Component
public class PetStoreClient {

	private static final String PET_BY_ID = "/pet/{petId}";

	private static final String PETS = "/pet";

	private final RestClient restClient;

	PetStoreClient(RestClient petStoreRestClient) {
		this.restClient = petStoreRestClient;
	}

	public Pet findById(long petId) {
		ClientHttpRequestInterceptor delayInterceptor = (request, body, execution) -> {
			try {
				// Sleep for 3 seconds (3000ms) to make the request take longer
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return execution.execute(request, body);
		};

		RestClient rc = RestClient.builder()
			.baseUrl("https://petstore.swagger.io/v2")
			.requestInterceptor(delayInterceptor) // <--- Injects the response delay
			.build();

		PetStorePayload payload;
		try {
			payload = rc.get()
				.uri(PET_BY_ID, petId)
				.retrieve()
				.onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
					throw new PetNotFoundException(petId);
				})
				.body(PetStorePayload.class);
		}
		catch (ResourceAccessException rae){
			throw new TimeoutException();
		}
		catch (RestClientException ex) {
			throw new PetStoreUnavailableException("Falló la consulta de la mascota %d".formatted(petId), ex);
		}

		return toDomain(payload, "consultar la mascota %d".formatted(petId));
	}

	public Pet save(Pet pet) {
		PetStorePayload payload;
		try {
			payload = this.restClient.post()
				.uri(PETS)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new PetStorePayload(pet.id(), pet.name(), pet.status()))
				.retrieve()
				.body(PetStorePayload.class);
		}
		catch (RestClientException ex) {
			throw new PetStoreUnavailableException("Falló el registro de la mascota %d".formatted(pet.id()), ex);
		}
		return toDomain(payload, "registrar la mascota %d".formatted(pet.id()));
	}

	public PetListResponse findList(String[] petIds) {
		List<PetResponse> responses = new ArrayList<>();
		int totalTasks = petIds.length;
		int maxThreads = 3;

		ExecutorService executor = Executors.newFixedThreadPool(maxThreads);

		for (int i = 0; i < totalTasks; i++) {
			final int taskId = i;
			executor.submit(() -> {
				System.out.println("Processing task " + taskId + " on thread " + Thread.currentThread().getName());
				// va a buscar la mascota por id
				Pet p = this.findById(Long.parseLong(petIds[taskId]));
				responses.add(new PetResponse(p.id(),p.name(),p.status()));
			});
		}

		executor.shutdown();
		try {
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
		}

		return new PetListResponse(responses);
	}

	/**
	 * La tienda declara {@code id} y {@code name} en toda mascota almacenada, así que una
	 * respuesta sin alguno de los dos se trata como intercambio fallido en lugar de propagar una
	 * mascota sin identidad. {@code status} sí es opcional aguas arriba y se refleja tal cual.
	 */
	private static Pet toDomain(PetStorePayload payload, String intercambio) {
		if (payload == null) {
			throw new PetStoreUnavailableException("La tienda devolvió una respuesta vacía al " + intercambio);
		}
		if (payload.id() == null || payload.name() == null) {
			throw new PetStoreUnavailableException("La tienda devolvió una respuesta incompleta al " + intercambio);
		}
		return new Pet(payload.id(), payload.name(), payload.status());
	}

}
