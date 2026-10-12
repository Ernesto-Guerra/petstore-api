package com.example.exam.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.exam.client.PetStoreClient;
import com.example.exam.model.Pet;
import com.example.exam.model.PetRegistration;
import com.example.exam.model.request.CreatePetRequest;
import com.example.exam.model.response.CreatePetResponse;
import com.example.exam.model.response.PetListResponse;
import com.example.exam.model.response.PetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

@Service
public class PetService {

	private static final Logger logger = LoggerFactory.getLogger(PetService.class);

	private final PetStoreClient petStoreClient;

	private final Clock clock;

	PetService(PetStoreClient petStoreClient, Clock clock) {
		this.petStoreClient = petStoreClient;
		this.clock = clock;
	}

	public PetResponse findPet(long petId) {
		Pet pet = this.petStoreClient.findById(petId);
		PetResponse response = new PetResponse(pet.id(), pet.name(), pet.status());
		logger.info("Mascota consultada: id={}, name={}, status={}", response.id(), response.name(), response.status());
		return response;
	}

	public PetListResponse findMultiplePets(String petIds) {
		String [] ids = petIds.split(",");
		PetListResponse response = this.petStoreClient.findList(ids);

		logger.info("Mascotas consultadas: {} Mascotas encontradas: {}",ids.length, response.responses().size());
		return response;
	}

	public PetRegistration registerPet(CreatePetRequest request) {
		Pet registeredPet = this.petStoreClient.save(new Pet(request.id(), request.name(), request.status()));
		CreatePetResponse receipt = new CreatePetResponse(UUID.randomUUID().toString(), LocalDateTime.now(this.clock),
				true, registeredPet.name());
		logger.info("Mascota registrada: id={}, transactionId={}, dateCreated={}, status={}, name={}", registeredPet.id(),
				receipt.transactionId(), receipt.dateCreated(), receipt.status(), receipt.name());
		return new PetRegistration(registeredPet.id(), receipt);
	}

}
