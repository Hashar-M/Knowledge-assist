package com.abcorg.knowledge.assist.runner;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
class HelloRagRunner implements ApplicationRunner {

    private static final String ANSWERABLE_QUESTION =
            "How long are failed payment events kept in the dead-letter topic, "
                    + "and who has to approve a replay?";

    private static final String UNANSWERABLE_QUESTION =
            "What is the retention period of the refunds topic?";

    private static final String PLAIN_SYSTEM = "You are a helpful assistant.";

    // The "augmentation" rules. Chapter 13 turns this into a proper grounded prompt.
    private static final String GROUNDED_SYSTEM = """
            You answer questions using ONLY the text between <context> tags.
            If the context does not contain the answer, reply exactly:
            "I don't know based on the provided documents."
            Do not use outside knowledge.
            """;

    private final ChatClient chat;
    private final String runbook;

    HelloRagRunner(ChatClient.Builder builder,
            @Value("classpath:acme-runbook.md") Resource runbookFile) throws IOException {
        this.chat = builder.build();
        this.runbook = runbookFile.getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public void run(ApplicationArguments args) {
        section("1. NO CONTEXT (closed-book)");
        ask(PLAIN_SYSTEM, ANSWERABLE_QUESTION);

        section("2. WITH PASTED CONTEXT (open-book)");
        ask(GROUNDED_SYSTEM, withContext(ANSWERABLE_QUESTION));

        section("3. CONTEXT DOES NOT CONTAIN THE ANSWER");
        ask(GROUNDED_SYSTEM, withContext(UNANSWERABLE_QUESTION));

        section("4. HOW BIG IS THE CONTEXT?");
        System.out.printf("Runbook: %d characters, roughly %d tokens (chars / 4 rule of thumb).%n",
                runbook.length(), runbook.length() / 4);
        System.out.println("Compare with the real 'usage' numbers printed above.");
    }

    private String withContext(String question) {
        return """
                <context>
                %s
                </context>

                Question: %s
                """.formatted(runbook, question);
    }

    private void ask(String system, String user) {
        long start = System.nanoTime();
        ChatResponse response = chat.prompt()
                .system(system)
                .user(user)
                .call()
                .chatResponse();
        long ms = (System.nanoTime() - start) / 1_000_000;

        System.out.println(response.getResult().getOutput().getText());
        System.out.println("[latency=" + ms + " ms, usage=" + response.getMetadata().getUsage() + "]");
    }

    private static void section(String title) {
        System.out.println("\n=== " + title + " ===");
    }
}
