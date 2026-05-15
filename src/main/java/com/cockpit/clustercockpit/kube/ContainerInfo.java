package com.cockpit.clustercockpit.kube;

public record ContainerInfo(String name, String image) {

    public String imageBase() {
        int idx = colonIndex();
        return idx >= 0 ? image.substring(0, idx) : image;
    }

    public String imageTag() {
        int idx = colonIndex();
        return idx >= 0 ? image.substring(idx + 1) : "";
    }

    private int colonIndex() {
        return image.lastIndexOf(':');
    }
}
