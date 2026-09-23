package com.example.exam.model;

import com.example.exam.model.response.CreatePetResponse;

/**
 * Resultado de registrar una mascota. El identificador es el que asignó la tienda, que no
 * necesariamente coincide con el solicitado.
 */
public record PetRegistration(Long petId, CreatePetResponse receipt) {
}
