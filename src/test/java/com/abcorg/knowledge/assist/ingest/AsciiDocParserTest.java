package com.abcorg.knowledge.assist.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AsciiDocParserTest {

    private static final String FIXTURE = """
            = Spring Boot Test Page
            :page-section-summary-toc: 1

            include::partial$intro.adoc[]

            == Endpoints

            Use xref:actuator/index.adoc[the actuator] to see endpoints.

            [source,java]
            ----
            == not a heading inside code
            ----

            === Details
            See https://example.com/docs[the example] for more.
            """;

    @Test
    void extractsTitleHeadingsAndLevels() {
        ParsedDocument d = new AsciiDocParser().parse(FIXTURE);

        assertThat(d.title()).isEqualTo("Spring Boot Test Page");
        assertThat(d.headings()).containsExactly(
                new Heading(1, "Spring Boot Test Page"),
                new Heading(2, "Endpoints"),
                new Heading(3, "Details"));
    }

    @Test
    void keepsCodeTextButNeverTreatsItAsHeading() {
        ParsedDocument d = new AsciiDocParser().parse(FIXTURE);

        assertThat(d.text()).contains("== not a heading inside code");
        assertThat(d.headings()).noneMatch(h -> h.title().contains("inside code"));
    }

    @Test
    void replacesMacrosAndRecordsSkippedIncludes() {
        ParsedDocument d = new AsciiDocParser().parse(FIXTURE);

        assertThat(d.text()).contains("the actuator").doesNotContain("xref:");
        assertThat(d.text()).contains("the example").doesNotContain("https://example.com/docs[");
        assertThat(d.text()).doesNotContain("include::").doesNotContain("[source,java]");
        assertThat(d.warnings()).hasSize(1);
    }
}

