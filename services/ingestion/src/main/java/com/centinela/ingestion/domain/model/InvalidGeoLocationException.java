package com.centinela.ingestion.domain.model;

public class InvalidGeoLocationException extends RuntimeException {
    public InvalidGeoLocationException(String message) {
        super(message);
    }
}
