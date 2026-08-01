package com.centinela.scoring.domain.model;

public class Coordenada {
    private final double latitud;
    private final double longitud;

    public Coordenada(double latitud, double longitud) {
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
}
