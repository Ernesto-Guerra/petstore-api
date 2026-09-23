package com.example.exam.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePetRequest(

		@NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que cero") Long id,

		@NotBlank(message = "es obligatorio") String status,

		@NotBlank(message = "es obligatorio") String name) {
}
