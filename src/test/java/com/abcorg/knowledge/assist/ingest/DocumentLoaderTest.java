package com.abcorg.knowledge.assist.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentLoaderTest {

    @TempDir
    Path tmp;

    private static final ProductMeta SPRING = new ProductMeta(
            "spring-boot", "Apache-2.0",
            "https://docs.spring.io/spring-boot/reference/{page}.html",
            "abc123def456", false);

    @Test
    void buildsSourceDocumentWithMetadataAndUrl() throws Exception {
        Path page = tmp.resolve("spring-boot/actuator/endpoints.adoc");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "= Endpoints Page\n\n== Health\n\nHealth information.\n");

        SourceDocument doc = new DocumentLoader(tmp, Map.of("spring-boot", SPRING))
                .load("spring-boot/actuator/endpoints.adoc");

        assertThat(doc.docId()).isEqualTo("spring-boot:actuator/endpoints.adoc");
        assertThat(doc.product()).isEqualTo("spring-boot");
        assertThat(doc.title()).isEqualTo("Endpoints Page");
        assertThat(doc.sourceUrl())
                .isEqualTo("https://docs.spring.io/spring-boot/reference/actuator/endpoints.html");
        assertThat(doc.license()).isEqualTo("Apache-2.0");
        assertThat(doc.sourceCommit()).isEqualTo("abc123def456");
        assertThat(doc.contentSha256()).hasSize(64);
    }

    @Test
    void sameInputGivesSameHash() throws Exception {
        Path page = tmp.resolve("spring-boot/a.adoc");
        Files.writeString(page, "= A\n");
        DocumentLoader loader = new DocumentLoader(tmp, Map.of("spring-boot", SPRING));

        assertThat(loader.load("spring-boot/a.adoc").contentSha256())
                .isEqualTo(loader.load("spring-boot/a.adoc").contentSha256());
    }

    @Test
    void rejectsPathsThatEscapeTheBaseDirectory() {
        DocumentLoader loader = new DocumentLoader(tmp, Map.of("spring-boot", SPRING));

        assertThatThrownBy(() -> loader.load("spring-boot/../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

