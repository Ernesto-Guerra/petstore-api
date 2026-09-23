package com.example.exam.exception;

public class PetNotFoundException extends RuntimeException {

	private final long petId;

	public PetNotFoundException(long petId) {
		super("La mascota %d no existe en la tienda".formatted(petId));
		this.petId = petId;
	}

	public long petId() {
		return this.petId;
	}

}
