package com.cockpit.clustercockpit.kube;

public record HelmReleaseRow(
    String namespace,
    String name,
    String chart,
    String version,
    boolean suspended,
    HelmReleaseStatus status,
    String message,
    String age
) {
}
