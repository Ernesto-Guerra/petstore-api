package com.example.exam.model.response;

import java.time.LocalDateTime;

public record CreatePetResponse(String transactionId, LocalDateTime dateCreated, boolean status, String name) {
}
