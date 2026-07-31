package com.centinela.corebackend.integration.flociaz;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Floci-AZ Key Vault wiring test (#167 §0.1.3, Lane A).
 *
 * <p>Verifies a PUT/GET round-trip against the running {@code centinela-floci-az}
 * container using the Azure SDK for Java with the Floci-AZ-required
 * {@link ForceHttpPolicy} + {@link FakeCredential} wiring
 * (<a href="https://floci.io/floci-az/services/key-vault/">docs</a>).
 *
 * <p>The test reuses the shared {@code docker-compose.yml} stack per
 * ADR-011 §11.1 (no per-test container). If the floci-az container is
 * unreachable, the whole class aborts via {@link Assumptions#abort} so
 * the build does not fail on a CI run without the compose stack.
 */
class FlociAzKeyVaultWiringTest {

    private static final String ACCOUNT = "devstoreaccount1";
    private static final int DEFAULT_PORT = 4577;

    private static SecretClient client;
    private static String endpoint;

    @BeforeAll
    static void setUp() {
        int port = Integer.parseInt(
            System.getProperty("centinela.flociaz.port", String.valueOf(DEFAULT_PORT)));
        endpoint = "http://localhost:" + port;

        try {
            HttpURLConnection probe = (HttpURLConnection)
                new URL(endpoint + "/_floci/health").openConnection();
            probe.setConnectTimeout(2000);
            probe.setReadTimeout(2000);
            int code = probe.getResponseCode();
            if (code != 200) {
                Assumptions.abort("Floci-AZ not healthy (HTTP " + code + " on " + endpoint + "/_floci/health)");
            }
        } catch (Exception e) {
            Assumptions.abort("Floci-AZ unreachable at " + endpoint + " (" + e.getMessage() + ")");
        }

        String vaultUrl = "https://localhost:" + port + "/" + ACCOUNT + "-keyvault";
        client = new SecretClientBuilder()
            .vaultUrl(vaultUrl)
            .credential(new FakeCredential())
            .addPolicy(new ForceHttpPolicy())
            .disableChallengeResourceVerification()
            .buildClient();
    }

    @Test
    @DisplayName("fake servicebus-connection-string round-trips through Floci-AZ Key Vault")
    void fakeServicebusConnectionStringRoundTripsThroughFlociAzKeyVault() {
        String secretName = "centinela-fake-sb-conn-" + UUID.randomUUID();
        String fakeConnString = "Endpoint=sb://localhost;SharedAccessKeyName=RootManageSharedAccessKey;"
            + "SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;";

        try {
            KeyVaultSecret written = client.setSecret(secretName, fakeConnString);
            KeyVaultSecret read = client.getSecret(secretName);

            assertThat(written.getValue()).isEqualTo(fakeConnString);
            assertThat(read.getValue()).isEqualTo(fakeConnString);
            assertThat(read.getProperties().getVersion()).isNotBlank();
        } finally {
            client.beginDeleteSecret(secretName);
        }
    }

    static final class FakeCredential implements TokenCredential {
        @Override
        public Mono<AccessToken> getToken(TokenRequestContext request) {
            return Mono.just(new AccessToken("fake-token", OffsetDateTime.now().plusHours(1)));
        }
    }

    static final class ForceHttpPolicy implements HttpPipelinePolicy {
        @Override
        public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
            try {
                String current = context.getHttpRequest().getUrl().toString();
                URL url = URI.create(current).toURL();
                int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
                context.getHttpRequest().setUrl(
                    new URL("http", url.getHost(), port, url.getFile()).toString());
            } catch (Exception ignored) {
                // best-effort rewrite; SDK falls back to original URL
            }
            return next.process();
        }
    }
}
