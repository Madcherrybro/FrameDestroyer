package org.framebreaker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class MapFrameBreakerConfig {
    public boolean enableMapIdFilter = false;
    public FilterMode mapIdFilterMode = FilterMode.ALLOWLIST;
    public List<Integer> mapIds = new ArrayList<>();

    public boolean enableNameKeywordFilter = true;
    public FilterMode nameKeywordFilterMode = FilterMode.ALLOWLIST;
    public List<String> nameKeywords = new ArrayList<>(List.of(
            "discord.gg",
            "discord.com",
            "kit shop",
            "store"
    ));
    public boolean caseInsensitiveNameMatching = true;
    public boolean partialNameMatching = true;

    public void resetToDefaults() {
        enableMapIdFilter = false;
        mapIdFilterMode = FilterMode.ALLOWLIST;
        mapIds = new ArrayList<>();

        enableNameKeywordFilter = true;
        nameKeywordFilterMode = FilterMode.ALLOWLIST;
        nameKeywords = new ArrayList<>(List.of(
                "discord.gg",
                "discord.com",
                "kit shop",
                "store"
        ));
        caseInsensitiveNameMatching = true;
        partialNameMatching = true;
    }

    public void normalize() {
        if (mapIdFilterMode == null) {
            mapIdFilterMode = FilterMode.ALLOWLIST;
        }
        if (nameKeywordFilterMode == null) {
            nameKeywordFilterMode = FilterMode.ALLOWLIST;
        }

        mapIds = mapIds == null
                ? new ArrayList<>()
                : mapIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        var normalizedKeywords = nameKeywords == null ? List.<String>of() : nameKeywords;
        var seen = new LinkedHashSet<String>();
        var cleaned = new ArrayList<String>();
        for (String keyword : normalizedKeywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            String dedupeKey = trimmed.toLowerCase(Locale.ROOT);
            if (seen.add(dedupeKey)) {
                cleaned.add(trimmed);
            }
        }

        cleaned.sort(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)));
        nameKeywords = cleaned;
    }

    public enum FilterMode {
        OFF,
        ALLOWLIST,
        BLOCKLIST
    }
}
