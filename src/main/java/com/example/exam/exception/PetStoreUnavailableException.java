package com.example.exam.exception;

public class PetStoreUnavailableException extends RuntimeException {

	public PetStoreUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

	public PetStoreUnavailableException(String message) {
		super(message);
	}

}
