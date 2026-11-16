package com.abcorg.knowledge.assist.ingest;

import java.util.ArrayList;
import java.util.List;

public final class Batches {
    private Batches() {
    }

    public static <T> List<List<T>> partition(List<T> items, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += size) {
            out.add(items.subList(i, Math.min(i + size, items.size())));
        }
        return out;
    }
}