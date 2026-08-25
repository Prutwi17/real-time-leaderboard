package com.realtimeleaderboard.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final String field;
    private final Object value;

    public ResourceNotFoundException(String resource, String field, Object value) {
        super("%s with %s '%s' not found".formatted(resource, field, value));
        this.resource = resource;
        this.field = field;
        this.value = value;
    }

    public String getResource() { return resource; }
    public String getField() { return field; }
    public Object getValue() { return value; }
}
