package com.cockpit.clustercockpit.kube;

public record PodRow(
    String namespace,
    String name,
    String ready,
    String status,
    int restarts,
    String age,
    String node
) {
    public String statusClass() {
        if (status == null || status.isBlank()) {
            return "unknown";
        }
        return switch (status) {
            case "Running" -> "ok";
            case "Succeeded", "Completed" -> "neutral";
            case "Pending", "ContainerCreating", "PodInitializing" -> "warn";
            case "Failed", "Error", "CrashLoopBackOff", "ImagePullBackOff", "ErrImagePull" -> "bad";
            default -> "unknown";
        };
    }
}
