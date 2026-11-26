package com.myck.productivity.category.dto;

import java.time.Instant;

public record CategoryResponse(Long id, String name, String color, Instant createdAt) {
}
