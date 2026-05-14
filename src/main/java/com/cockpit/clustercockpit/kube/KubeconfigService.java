package com.cockpit.clustercockpit.kube;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KubeconfigService {

    private final ClusterConnectionService clusterConnectionService;

    public Path getCurrentPath() {
        return clusterConnectionService.getActiveKubeconfigPath();
    }
}
