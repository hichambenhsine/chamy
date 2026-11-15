package io.github.hichambenhsine.chamy.domain.model;

import java.util.Objects;

public record TechStack(String id) {

    public TechStack {
        Objects.requireNonNull(id, "TechStack ID cannot be null.");
        if (id.isBlank()) {
            throw new IllegalArgumentException("TechStack ID cannot be blank.");
        }
        id = id.trim().toUpperCase();
    }

    public static TechStack of(String id) {
        return new TechStack(id);
    }

    @Override
    public String toString() {
        return id;
    }
}