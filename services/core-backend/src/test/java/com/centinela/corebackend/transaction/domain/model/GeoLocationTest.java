package com.centinela.corebackend.transaction.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoLocationTest {

    @Test
    void accepts_valid_coordinates() {
        GeoLocation loc = new GeoLocation(40.4168, -3.7038);
        assertEquals(40.4168, loc.latitude());
        assertEquals(-3.7038, loc.longitude());
    }

    @Test
    void accepts_boundary_latitudes() {
        assertDoesNotThrow(() -> new GeoLocation(-90.0, 0.0));
        assertDoesNotThrow(() -> new GeoLocation(90.0, 0.0));
    }

    @Test
    void accepts_boundary_longitudes() {
        assertDoesNotThrow(() -> new GeoLocation(0.0, -180.0));
        assertDoesNotThrow(() -> new GeoLocation(0.0, 180.0));
    }

    @Test
    void rejects_latitude_below_minus_90() {
        assertThrows(IllegalArgumentException.class, () ->
                new GeoLocation(-90.1, 0.0));
    }

    @Test
    void rejects_latitude_above_90() {
        assertThrows(IllegalArgumentException.class, () ->
                new GeoLocation(90.1, 0.0));
    }

    @Test
    void rejects_longitude_below_minus_180() {
        assertThrows(IllegalArgumentException.class, () ->
                new GeoLocation(0.0, -180.1));
    }

    @Test
    void rejects_longitude_above_180() {
        assertThrows(IllegalArgumentException.class, () ->
                new GeoLocation(0.0, 180.1));
    }
}
