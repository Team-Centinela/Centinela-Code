package com.centinela.corebackend.integration.flociaz;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Floci-AZ Blob wiring test (#167 §0.1.3, Lane A).
 *
 * <p>Verifies a PUT/GET round-trip against the running {@code centinela-floci-az}
 * container using the Azure Blob Storage SDK for Java with the
 * Azurite-compatible SharedKey connection string
 * (<a href="https://floci.io/floci-az/services/blob/">docs</a>).
 *
 * <p>The test reuses the shared {@code docker-compose.yml} stack per
 * ADR-011 §11.1. If the floci-az container is unreachable, the whole
 * class aborts via {@link Assumptions#abort}.
 */
class FlociAzBlobWiringTest {

    private static final String DEFAULT_CONNECTION_STRING =
        "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
            + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMh0==;"
            + "BlobEndpoint=http://localhost:4577/devstoreaccount1;";

    private static BlobServiceClient service;
    private static BlobContainerClient container;
    private static String containerName;

    @BeforeAll
    static void setUp() {
        String connString = System.getProperty("centinela.flociaz.blob.connection-string",
            DEFAULT_CONNECTION_STRING);
        int port = Integer.parseInt(System.getProperty("centinela.flociaz.port", "4577"));
        String endpoint = "http://localhost:" + port;

        try {
            HttpURLConnection probe = (HttpURLConnection)
                new URL(endpoint + "/_floci/health").openConnection();
            probe.setConnectTimeout(2000);
            probe.setReadTimeout(2000);
            if (probe.getResponseCode() != 200) {
                Assumptions.abort("Floci-AZ not healthy on " + endpoint);
            }
        } catch (Exception e) {
            Assumptions.abort("Floci-AZ unreachable at " + endpoint + " (" + e.getMessage() + ")");
        }

        service = new BlobServiceClientBuilder()
            .connectionString(connString)
            .buildClient();
        containerName = "centinela-smoke-" + UUID.randomUUID();
        container = service.getBlobContainerClient(containerName);
        container.create();
    }

    @AfterAll
    static void tearDown() {
        if (container != null && container.exists()) {
            container.delete();
        }
    }

    @Test
    @DisplayName("test document round-trips through Floci-AZ Blob (PUT then GET)")
    void testDocumentRoundTripsThroughFlociAzBlob() {
        String blobName = "smoke-" + UUID.randomUUID() + ".txt";
        String payload = "centinela-floci-az-wiring-smoke-" + System.nanoTime();

        BlobClient blob = container.getBlobClient(blobName);
        blob.upload(BinaryData.fromString(payload), true);
        String downloaded = blob.downloadContent().toString();

        assertThat(downloaded).isEqualTo(payload);
        assertThat(blob.exists()).isTrue();
    }
}
