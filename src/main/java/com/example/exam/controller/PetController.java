package com.example.exam.controller;

import java.net.URI;

import com.example.exam.model.PetRegistration;
import com.example.exam.model.request.CreatePetRequest;
import com.example.exam.model.response.CreatePetResponse;
import com.example.exam.model.response.PetResponse;
import com.example.exam.service.PetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/pet")
class PetController {

	private final PetService petService;

	PetController(PetService petService) {
		this.petService = petService;
	}

	@GetMapping("/{petId}")
	PetResponse getPet(@PathVariable @Positive long petId) {
		return this.petService.findPet(petId);
	}

	@PostMapping
	ResponseEntity<CreatePetResponse> createPet(@Valid @RequestBody CreatePetRequest request,
			UriComponentsBuilder uriBuilder) {
		PetRegistration registration = this.petService.registerPet(request);
		URI location = uriBuilder.path("/api/pet/{petId}").buildAndExpand(registration.petId()).toUri();
		return ResponseEntity.created(location).body(registration.receipt());
	}

}
