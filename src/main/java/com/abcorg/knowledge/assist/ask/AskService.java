package com.abcorg.knowledge.assist.ask;

import com.abcorg.knowledge.assist.config.AskProperties;
import com.abcorg.knowledge.assist.config.SearchProperties;
import com.abcorg.knowledge.assist.exception.UpstreamUnavailableException;
import com.abcorg.knowledge.assist.retrieval.ChunkRetriever;
import com.abcorg.knowledge.assist.retrieval.QueryEmbedder;
import com.abcorg.knowledge.assist.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    private record Retrieval(List<RetrievedChunk> chunks, long embedMs, long searchMs) {
    }

    private final QueryEmbedder embedder;
    private final ChunkRetriever retriever;
    private final ChatClient chat;
    private final AskProperties props;
    private final SearchProperties search;
    private final String configuredChatModel;

    public AskService(QueryEmbedder embedder, ChunkRetriever retriever, ChatClient.Builder chatBuilder,
            AskProperties props, SearchProperties search,
            @Value("${spring.ai.openai.chat.model:unknown}") String configuredChatModel) {
        this.embedder = embedder;
        this.retriever = retriever;
        this.chat = chatBuilder.build();
        this.props = props;
        this.search = search;
        this.configuredChatModel = configuredChatModel;
    }

    /** Retrieval only. No model call, no generation cost. The tool for debugging retrieval (and Chapter 10). */
    public SearchResponse search(String question) {
        Retrieval r = retrieve(question);
        return new SearchResponse(sources(r.chunks()), r.embedMs(), r.searchMs());
    }

    public AskResponse ask(String question) {
        long start = System.nanoTime();
        Retrieval r = retrieve(question);

        ContextBuilder.Built context = ContextBuilder.build(r.chunks(), props.maxContextTokens());
        if (context.dropped() > 0) {
            log.info("Context budget of {} tokens: {} of {} chunks fit", props.maxContextTokens(),
                    context.used().size(), r.chunks().size());
        }
        if (context.used().isEmpty()) {
            log.warn("Nothing to send to the model (retrieved={}). Returning the refusal without a model call.",
                    r.chunks().size());
            return new AskResponse(PromptFactory.REFUSAL, List.of(), AskResponse.Usage.NONE,
                    new AskResponse.Timings(r.embedMs(), r.searchMs(), 0, elapsedMs(start)),
                    new AskResponse.Models(configuredChatModel, search.embeddingModel()));
        }

        long generateStart = System.nanoTime();
        ChatResponse response = generate(question, context.text());
        long generateMs = elapsedMs(generateStart);

        String answer = response.getResult().getOutput().getText();
        if (answer == null || answer.isBlank()) {
            throw new UpstreamUnavailableException("The model returned an empty answer", null);
        }
        String modelUsed = response.getMetadata() == null ? null : response.getMetadata().getModel();
        return new AskResponse(answer.trim(), sources(context.used()), usage(response),
                new AskResponse.Timings(r.embedMs(), r.searchMs(), generateMs, elapsedMs(start)),
                new AskResponse.Models(modelUsed == null || modelUsed.isBlank() ? configuredChatModel : modelUsed,
                        search.embeddingModel()));
    }

    private Retrieval retrieve(String question) {
        long embedStart = System.nanoTime();
        QueryEmbedder.QueryEmbedding q = embedder.embed(question);
        long embedMs = elapsedMs(embedStart);

        long searchStart = System.nanoTime();
        List<RetrievedChunk> chunks = retriever.search(q.vector(), props.topK());
        long searchMs = elapsedMs(searchStart);

        // The top score is the number Chapters 10 and 13 will need. Log it from day one.
        log.info("retrieved {} chunks, top score {}, embed {} ms, search {} ms", chunks.size(),
                chunks.isEmpty() ? "n/a" : String.format(Locale.ROOT, "%.3f", chunks.get(0).score()),
                embedMs, searchMs);
        return new Retrieval(chunks, embedMs, searchMs);
    }

    /**
     * Messages are passed as objects on purpose. ChatClient's user(String) and system(String) treat the text as a
     * template with {placeholders}, and documentation is full of braces. See section 3.7.
     */
    private ChatResponse generate(String question, String context) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PromptFactory.system()),
                new UserMessage(PromptFactory.user(question, context))));
        try {
            ChatResponse response = chat.prompt(prompt).call().chatResponse();
            if (response == null || response.getResult() == null) {
                throw new UpstreamUnavailableException("The model returned no result", null);
            }
            return response;
        } catch (UpstreamUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UpstreamUnavailableException("Model call failed", e);
        }
    }

    private List<AskResponse.Source> sources(List<RetrievedChunk> chunks) {
        List<AskResponse.Source> out = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            out.add(AskResponse.Source.of(i + 1, chunks.get(i), props.snippetChars()));
        }
        return out;
    }

    private static AskResponse.Usage usage(ChatResponse response) {
        var u = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (u == null) {
            return AskResponse.Usage.NONE;
        }
        return new AskResponse.Usage(n(u.getPromptTokens()), n(u.getCompletionTokens()), n(u.getTotalTokens()));
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
