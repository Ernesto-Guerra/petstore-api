package com.example.exam.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.exam.exception.PetNotFoundException;
import com.example.exam.exception.PetStoreUnavailableException;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String SOLICITUD_INVALIDA = "Solicitud inválida";

	@ExceptionHandler(PetNotFoundException.class)
	ProblemDetail handlePetNotFound(PetNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Mascota no encontrada");
		problem.setProperty("petId", ex.petId());
		return problem;
	}

	@ExceptionHandler(PetStoreUnavailableException.class)
	ProblemDetail handlePetStoreUnavailable(PetStoreUnavailableException ex) {
		this.logger.error("Falló la comunicación con la tienda", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
				"La tienda no completó la solicitud.");
		problem.setTitle("Tienda no disponible");
		return problem;
	}

	/**
	 * Último recurso ante un fallo no previsto: se registra completo en la bitácora y hacia
	 * afuera solo viaja un mensaje genérico, para no filtrar detalles internos del sistema.
	 */
	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpectedFailure(Exception ex) {
		this.logger.error("Fallo inesperado al atender la solicitud", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"No fue posible procesar la solicitud.");
		problem.setTitle("Error interno");
		return problem;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setTitle(SOLICITUD_INVALIDA);
		problem.setDetail("La solicitud contiene campos inválidos.");
		problem.setProperty("errors", fieldErrorsOf(ex));
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ex.getBody();
		problem.setTitle(SOLICITUD_INVALIDA);
		problem.setDetail("Los parámetros de la solicitud no son válidos.");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
				"El parámetro de la solicitud no tiene un valor válido.");
		problem.setTitle(SOLICITUD_INVALIDA);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	private static Map<String, String> fieldErrorsOf(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return errors;
	}

}
