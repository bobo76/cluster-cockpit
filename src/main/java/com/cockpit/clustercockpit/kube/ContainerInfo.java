package com.cockpit.clustercockpit.kube;

public record ContainerInfo(String name, String image) {

    public String imageBase() {
        int idx = image.lastIndexOf(':');
        return idx >= 0 ? image.substring(0, idx) : image;
    }

    public String imageTag() {
        int idx = image.lastIndexOf(':');
        return idx >= 0 ? image.substring(idx + 1) : "";
    }
}
