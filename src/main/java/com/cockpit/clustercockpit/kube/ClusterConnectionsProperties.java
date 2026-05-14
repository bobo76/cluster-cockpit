package com.cockpit.clustercockpit.kube;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cockpit.cluster-connections")
public class ClusterConnectionsProperties {
    private String root;
    private String defaultConnection;
    /**
     * Allowed cluster connections. Keys are the directory names (filter);
     * values are the optional display alias (blank = derive from name).
     */
    private Map<String, String> allowed = new LinkedHashMap<>();
}
