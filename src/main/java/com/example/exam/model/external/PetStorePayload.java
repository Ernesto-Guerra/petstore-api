package com.example.exam.model.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Los atributos desconocidos se ignoran para que un cambio en el contrato de la tienda no pueda
 * romper esta aplicación.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PetStorePayload(Long id, String name, String status) {
}
