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
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
		PetStorePayload payload;
		try {
			payload = this.restClient.get()
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
		int totalTasks = petIds.length;
		int maxThreads = 3;

		ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
		List<Future<PetResponse>> tasks = new ArrayList<>(totalTasks);

		for (int i = 0; i < totalTasks; i++) {
			final int taskId = i;
			tasks.add(executor.submit(() -> {
				// va a buscar la mascota por id
				Pet p = this.findById(Long.parseLong(petIds[taskId]));
				return new PetResponse(p.id(), p.name(), p.status());
			}));
		}

		executor.shutdown();

		// Las respuestas se recogen en el hilo llamante y en el orden solicitado: así ninguna
		// lista se comparte entre hilos y el resultado no depende de quién termine primero.
		List<PetResponse> responses = new ArrayList<>(totalTasks);
		for (Future<PetResponse> task : tasks) {
			responses.add(resultadoDe(task));
		}

		return new PetListResponse(responses);
	}

	/**
	 * El fallo de una consulta en paralelo llega envuelto en {@code ExecutionException}: se
	 * desenvuelve el original para que el manejador de errores decida el código de respuesta a
	 * partir del fallo real, en lugar de devolver una lista incompleta con un 200.
	 */
	private static PetResponse resultadoDe(Future<PetResponse> task) {
		try {
			return task.get();
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof RuntimeException causa) {
				throw causa;
			}
			throw new IllegalStateException("Falló una consulta en paralelo", ex.getCause());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Se interrumpió la consulta de mascotas en paralelo", ex);
		}
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
