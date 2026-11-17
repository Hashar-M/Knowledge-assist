package com.abcorg.knowledge.assist.ingest;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.abcorg.knowledge.assist.chunking.Section;
import org.junit.jupiter.api.Test;

class SectionSplitterTest {

    private static SourceDocument doc(String title, String text, List<Heading> headings) {
        return new SourceDocument("p:page.adoc", "p", title, text, headings,
                "https://example.invalid", "Apache-2.0", "abc", "hash", List.of());
    }

    @Test
    void buildsHeadingPathsFromLevels() {
        String text = "Page\n\nEndpoints\n\nUse the actuator.\n\nDetails\n\nSee the example.\n\nSecurity\n\nLock it down.";
        var d = doc("Page", text, List.of(new Heading(1, "Page"), new Heading(2, "Endpoints"),
                new Heading(3, "Details"), new Heading(2, "Security")));

        var r = SectionSplitter.split(d);

        assertThat(r.unmatchedHeadings()).isZero();
        assertThat(r.sections()).extracting(Section::headingPath).containsExactly(
                List.of("Page", "Endpoints"),
                List.of("Page", "Endpoints", "Details"),
                List.of("Page", "Security"));
    }

    @Test
    void sameTitleTwiceKeepsEachBody() {
        String text = "Page\n\nOverview\n\nfirst body\n\nOverview\n\nsecond body";
        var d = doc("Page", text, List.of(new Heading(1, "Page"), new Heading(2, "Overview"), new Heading(2, "Overview")));

        var r = SectionSplitter.split(d);

        assertThat(r.sections()).hasSize(2);
        assertThat(r.sections().get(0).body()).contains("first body").doesNotContain("second");
        assertThat(r.sections().get(1).body()).contains("second body");
    }

    @Test
    void worksOnFlatHtmlStyleText() {
        String text = "Kafka stores records in partitions. Replication Each partition has replicas. Design notes Design is hard.";
        var d = doc("Kafka Design", text, List.of(new Heading(2, "Replication"), new Heading(2, "Design notes")));

        var r = SectionSplitter.split(d);

        assertThat(r.sections()).extracting(s -> s.headingPath().get(s.headingPath().size() - 1))
                .containsExactly("Kafka Design", "Replication", "Design notes");
    }

    @Test
    void countsHeadingsItCannotFindAndKeepsTheirText() {
        var d = doc("T", "T\n\nbody one\n\nOther\n\nbody two",
                List.of(new Heading(1, "T"), new Heading(2, "Gone"), new Heading(2, "Other")));

        var r = SectionSplitter.split(d);

        assertThat(r.unmatchedHeadings()).isEqualTo(1);
        assertThat(r.sections()).hasSize(2);
    }

    @Test
    void documentWithoutHeadingsBecomesOneSection() {
        var r = SectionSplitter.split(doc("Pods", "Just text here.", List.of()));

        assertThat(r.sections()).hasSize(1);
        assertThat(r.sections().get(0).headingPath()).containsExactly("Pods");
    }
}

