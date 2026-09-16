package com.xt9y.features.xtprofile;

import java.util.Locale;

final class XTProfileSearch {

    static boolean matches(String name, String machine, String query) {
        String needle = normalize(query);
        if (needle.isEmpty()) return true;
        return normalize(name).contains(needle) || normalize(machine).contains(needle);
    }

    private static String normalize(String value) {
        return value == null ? ""
            : value.toLowerCase(Locale.ROOT)
                .trim();
    }

    private XTProfileSearch() {}
}
