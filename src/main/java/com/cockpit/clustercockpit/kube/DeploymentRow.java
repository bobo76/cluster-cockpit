package com.cockpit.clustercockpit.kube;

import java.util.List;

public record DeploymentRow(
    String namespace,
    String name,
    String ready,
    int upToDate,
    int available,
    String age,
    List<ContainerInfo> containers
) {
    public String statusClass() {
        String[] parts = ready.split("/");
        if (parts.length != 2) {
            return "unknown";
        }
        try {
            int readyCount = Integer.parseInt(parts[0].trim());
            int desired = Integer.parseInt(parts[1].trim());
            if (desired == 0) {
                return "neutral";
            }
            if (readyCount >= desired) {
                return "ok";
            }
            if (readyCount == 0) {
                return "bad";
            }
            return "warn";
        } catch (NumberFormatException e) {
            return "unknown";
        }
    }
}
