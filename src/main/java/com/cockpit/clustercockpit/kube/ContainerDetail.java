package com.cockpit.clustercockpit.kube;

import java.util.List;

public record ContainerDetail(
    String name,
    String image,
    String status,
    List<EnvVar> env,
    List<MountInfo> mounts
) {
    public String statusClass() {
        if (status == null) {
            return "unknown";
        }
        if (status.startsWith("running")) {
            return "ok";
        }
        if (status.startsWith("terminated")) {
            return status.contains("exit code: 0") ? "neutral" : "bad";
        }
        if (status.startsWith("waiting")) {
            return "warn";
        }
        return "unknown";
    }
}
