package com.abcorg.knowledge.assist.ingest;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlParserTest {

    private static final String FIXTURE = """
            <!doctype html>
            <html><head><title>Kafka Design</title><script>var x = 1;</script></head>
            <body>
            <nav>Site menu Home Docs</nav>
            <!--#include virtual="/includes/header.html" -->
            <div class="content"><main>
            <h1>Design</h1>
            <p>Kafka stores records in <b>partitions</b>.</p>
            <h2>Replication</h2>
            <p>Each partition has replicas.</p>
            </main></div>
            <footer>Copyright notice</footer>
            </body></html>
            """;

    @Test
    void readsTitleHeadingsAndMainContentOnly() {
        ParsedDocument d = new HtmlParser().parse(FIXTURE);

        assertThat(d.title()).isEqualTo("Kafka Design");
        assertThat(d.headings()).containsExactly(new Heading(1, "Design"), new Heading(2, "Replication"));
        assertThat(d.text()).contains("partitions").contains("replicas");
    }

    @Test
    void removesNavigationScriptsFooterAndIncludeComments() {
        ParsedDocument d = new HtmlParser().parse(FIXTURE);

        assertThat(d.text()).doesNotContain("Site menu").doesNotContain("Copyright").doesNotContain("var x");
        assertThat(d.warnings()).hasSize(1);
    }

    @Test
    void preservesLineBreaksInPreBlocks() {
        String htmlWithPre = """
                <!doctype html>
                <html><head><title>Code Example</title></head>
                <body>
                <main>
                <h1>Example</h1>
                <p>Here is some code:</p>
                <pre>line one
                  indented line two
                line three</pre>
                <p>End of example.</p>
                </main>
                </body></html>
                """;
        ParsedDocument d = new HtmlParser().parse(htmlWithPre);

        assertThat(d.text()).contains("line one\n  indented line two\nline three");
    }
}

