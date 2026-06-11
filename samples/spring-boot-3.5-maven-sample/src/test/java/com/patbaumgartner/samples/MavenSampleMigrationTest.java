package com.patbaumgartner.samples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenSampleMigrationTest {

    @Test
    void migratedFixtureContainsReplacements() throws IOException {
        Path fixture = Path.of("target", "migration-fixture", "application.properties");
        assertThat(fixture).exists();

        String content = Files.readString(fixture);

        assertThat(content).contains("server.max-http-request-header-size=16KB");
        assertThat(content).contains("server.forward-headers-strategy=true");
        assertThat(content).contains("spring.http.codecs.max-in-memory-size=1MB");

        assertThat(content).doesNotContain("server.max-http-header-size=");
        assertThat(content).doesNotContain("server.use-forward-headers=");
        assertThat(content).doesNotContain("spring.codec.max-in-memory-size=");
    }
}
