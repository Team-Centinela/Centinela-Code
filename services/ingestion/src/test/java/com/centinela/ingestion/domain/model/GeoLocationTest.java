package com.centinela.ingestion.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoLocationTest {

    @Test
    void should_reject_lat_below_minus_90() {
        assertThrows(InvalidGeoLocationException.class,
                () -> new GeoLocation(-91.0, 0.0));
    }

    @Test
    void should_reject_lat_above_90() {
        assertThrows(InvalidGeoLocationException.class,
                () -> new GeoLocation(91.0, 0.0));
    }

    @Test
    void should_reject_lon_below_minus_180() {
        assertThrows(InvalidGeoLocationException.class,
                () -> new GeoLocation(0.0, -181.0));
    }

    @Test
    void should_reject_lon_above_180() {
        assertThrows(InvalidGeoLocationException.class,
                () -> new GeoLocation(0.0, 181.0));
    }

    @Test
    void should_accept_boundary_lat_minus_90() {
        GeoLocation loc = new GeoLocation(-90.0, 0.0);
        assertEquals(-90.0, loc.latitude());
        assertEquals(0.0, loc.longitude());
    }

    @Test
    void should_accept_boundary_lat_90() {
        GeoLocation loc = new GeoLocation(90.0, 0.0);
        assertEquals(90.0, loc.latitude());
    }

    @Test
    void should_accept_boundary_lon_minus_180() {
        GeoLocation loc = new GeoLocation(0.0, -180.0);
        assertEquals(-180.0, loc.longitude());
    }

    @Test
    void should_accept_boundary_lon_180() {
        GeoLocation loc = new GeoLocation(0.0, 180.0);
        assertEquals(180.0, loc.longitude());
    }

    @Test
    void should_accept_valid_location() {
        GeoLocation loc = new GeoLocation(40.4168, -3.7038);
        assertEquals(40.4168, loc.latitude());
        assertEquals(-3.7038, loc.longitude());
    }

    @Test
    void should_be_equal_by_value() {
        GeoLocation loc1 = new GeoLocation(40.4168, -3.7038);
        GeoLocation loc2 = new GeoLocation(40.4168, -3.7038);
        assertEquals(loc1, loc2);
        assertEquals(loc1.hashCode(), loc2.hashCode());
    }
}
