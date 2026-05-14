package com.cockpit.clustercockpit.kube;

public record MountInfo(String path, String source, boolean readOnly) {
}
