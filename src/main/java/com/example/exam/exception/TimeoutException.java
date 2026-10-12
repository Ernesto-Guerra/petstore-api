package com.example.exam.exception;

public class TimeoutException extends RuntimeException {


	public TimeoutException() {
		super("El servicio tardó demasiado en responder");
	}

}
