package com.xt9y.features.xtprofile;

import java.util.LinkedHashMap;
import java.util.Map;

final class XTProfileData {

    static final class MediumRecord {

        String id;
        String name;
        String type;
        String machine;
        String item;
        long dispatches;
        long busyNs;
        long tickCostNs;
        long activeTicks;
        long lastDispatchNs;
        boolean hasLocation;
        int dimension;
        int x;
        int y;
        int z;
        final Map<Long, Long> dispatchesByCpu = new LinkedHashMap<>();
        final Map<Long, Long> busyNsByCpu = new LinkedHashMap<>();
        final Map<Long, Long> tickCostNsByCpu = new LinkedHashMap<>();
        final Map<Long, Long> activeTicksByCpu = new LinkedHashMap<>();
    }

    private XTProfileData() {}
}
