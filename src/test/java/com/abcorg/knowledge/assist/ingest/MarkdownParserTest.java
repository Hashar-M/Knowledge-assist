package com.abcorg.knowledge.assist.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownParserTest {

    private static final String FIXTURE = """
            ---
            title: "Pods"
            weight: 20
            ---
            {{< feature-state state="stable" >}}

            # Pods

            A Pod is the smallest deployable unit.

            ## Lifecycle

            {{% note %}}
            Pods are not durable.
            {{% /note %}}
            """;

    @Test
    void usesFrontMatterTitleAndKeepsHeadingLevels() {
        ParsedDocument d = new MarkdownParser().parse(FIXTURE);

        assertThat(d.title()).isEqualTo("Pods");
        assertThat(d.headings()).containsExactly(
                new Heading(1, "Pods"),
                new Heading(2, "Lifecycle"));
    }

    @Test
    void stripsFrontMatterAndShortcodes() {
        ParsedDocument d = new MarkdownParser().parse(FIXTURE);

        assertThat(d.text()).doesNotContain("{{").doesNotContain("weight");
        assertThat(d.text()).contains("smallest deployable unit").contains("Pods are not durable");
    }
}

