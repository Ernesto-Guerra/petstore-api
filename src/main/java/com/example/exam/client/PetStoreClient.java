package com.example.exam.client;

import com.example.exam.exception.PetNotFoundException;
import com.example.exam.exception.PetStoreUnavailableException;
import com.example.exam.model.Pet;
import com.example.exam.model.external.PetStorePayload;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
