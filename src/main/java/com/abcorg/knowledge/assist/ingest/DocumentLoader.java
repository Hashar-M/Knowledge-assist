package com.abcorg.knowledge.assist.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Reads files from data/starter and produces SourceDocument records. */
public final class DocumentLoader {

    private final Path baseDir;                        // e.g. data/starter
    private final Map<String, ProductMeta> products;
    private final List<DocumentParser> parsers = List.of(
            new AsciiDocParser(), new MarkdownParser(), new HtmlParser());

    public DocumentLoader(Path baseDir, Map<String, ProductMeta> products) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.products = products;
    }

    /** starterPath looks like "spring-boot/actuator/endpoints.adoc". */
    public SourceDocument load(String starterPath) throws IOException {
        int slash = starterPath.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("expected <product>/<page>: " + starterPath);
        }
        String product = starterPath.substring(0, slash);
        String page = starterPath.substring(slash + 1);

        ProductMeta meta = products.get(product);
        if (meta == null) {
            throw new IllegalArgumentException("unknown product: " + product);
        }

        Path file = baseDir.resolve(starterPath).normalize();
        if (!file.startsWith(baseDir)) {
            throw new IllegalArgumentException("path escapes base dir: " + starterPath);
        }

        String fileName = file.getFileName().toString();
        DocumentParser parser = parsers.stream()
                .filter(p -> p.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no parser for " + fileName));

        String raw = Files.readString(file, StandardCharsets.UTF_8);
        ParsedDocument parsed = parser.parse(raw);

        return new SourceDocument(
                product + ":" + page,
                product,
                parsed.title() != null ? parsed.title() : fileName,
                parsed.text(),
                parsed.headings(),
                buildUrl(meta.urlTemplate(), page),
                meta.license(),
                meta.commit(),
                sha256(raw),
                parsed.warnings());
    }

    public List<SourceDocument> loadAll(Path starterList) throws IOException {
        List<SourceDocument> docs = new ArrayList<>();
        for (String line : Files.readAllLines(starterList, StandardCharsets.UTF_8)) {
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            docs.add(load(s));
        }
        return docs;
    }

    static String buildUrl(String template, String page) {
        String noExt = page.replaceFirst("\\.(adoc|md|html)$", "");
        return template.replace("{path}", page).replace("{page}", noExt);
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
