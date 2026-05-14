package com.cockpit.clustercockpit.kube;

import java.util.List;

public record PodDetail(
    String namespace,
    String name,
    List<ContainerDetail> initContainers,
    List<ContainerDetail> containers
) {
}
