package com.centinela.ingestion.domain.model;

import java.util.Objects;

public class Coordenada {
    private final double latitud;
    private final double longitud;

    public Coordenada(double latitud, double longitud) {
        if (latitud < -90 || latitud > 90) {
            throw new IllegalArgumentException("Latitud fuera de rango: " + latitud);
        }
        if (longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + longitud);
        }
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }

    public double distanciaKm(Coordenada otra) {
        final double RADIO_TIERRA_KM = 6371.0;
        double dLat = Math.toRadians(otra.latitud - this.latitud);
        double dLon = Math.toRadians(otra.longitud - this.longitud);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(this.latitud)) * Math.cos(Math.toRadians(otra.latitud)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RADIO_TIERRA_KM * c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordenada coordenada = (Coordenada) o;
        return Double.compare(coordenada.latitud, latitud) == 0 &&
               Double.compare(coordenada.longitud, longitud) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitud, longitud);
    }
}
