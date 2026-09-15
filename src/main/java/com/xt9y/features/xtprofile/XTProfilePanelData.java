package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.List;

final class XTProfilePanelData {

    static final class Entry {

        String id;
        String name;
        String type;
        String machine;
        String item;
        long dispatches;
        double sharePercent;
        double craftTimeMillis;
        double tpsUsagePercent;
        boolean hasLocation;
        int dimension;
        int x;
        int y;
        int z;
    }

    static final class View {

        long cpuId;
        long totalDispatches;
        final List<Entry> entries = new ArrayList<>();
    }

    private XTProfilePanelData() {}
}
