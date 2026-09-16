package com.xt9y.features.xtprofile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class XTProfilePendingOperations {

    static final class Completion {

        final String mediumId;
        final long elapsedNs;

        Completion(String mediumId, long elapsedNs) {
            this.mediumId = mediumId;
            this.elapsedNs = elapsedNs;
        }
    }

    private final List<Operation> operations = new ArrayList<>();

    void add(long cpuId, String mediumId, long startedNs, Map<String, Long> outputs) {
        if (outputs == null || outputs.isEmpty()) return;

        Map<String, Long> remaining = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : outputs.entrySet()) {
            String key = entry.getKey();
            Long amount = entry.getValue();
            if (key == null || key.isEmpty() || amount == null || amount <= 0) continue;
            remaining.put(key, amount);
        }
        if (!remaining.isEmpty()) operations.add(new Operation(cpuId, mediumId, startedNs, remaining));
    }

    List<Completion> accept(long cpuId, String outputKey, long amount, long returnedNs) {
        List<Completion> completed = new ArrayList<>();
        if (outputKey == null || outputKey.isEmpty() || amount <= 0) return completed;

        long remainingAmount = amount;
        Iterator<Operation> iterator = operations.iterator();
        while (iterator.hasNext() && remainingAmount > 0) {
            Operation operation = iterator.next();
            if (operation.cpuId != cpuId) continue;

            Long expected = operation.remaining.get(outputKey);
            if (expected == null || expected <= 0) continue;

            long accepted = Math.min(expected, remainingAmount);
            long left = expected - accepted;
            remainingAmount -= accepted;
            if (left == 0) operation.remaining.remove(outputKey);
            else operation.remaining.put(outputKey, left);

            if (operation.remaining.isEmpty()) {
                iterator.remove();
                completed.add(new Completion(operation.mediumId, Math.max(0, returnedNs - operation.startedNs)));
            }
        }
        return completed;
    }

    void clearCpu(long cpuId) {
        operations.removeIf(operation -> operation.cpuId == cpuId);
    }

    void clear() {
        operations.clear();
    }

    private static final class Operation {

        final long cpuId;
        final String mediumId;
        final long startedNs;
        final Map<String, Long> remaining;

        Operation(long cpuId, String mediumId, long startedNs, Map<String, Long> remaining) {
            this.cpuId = cpuId;
            this.mediumId = mediumId;
            this.startedNs = startedNs;
            this.remaining = remaining;
        }
    }
}
