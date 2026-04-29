package com.example.cicd.customer;

import java.time.LocalDateTime;

public record Customer(Long id, String fullName, String email, LocalDateTime createdAt) {
}
