package com.cockpit.clustercockpit.kube;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cockpit.namespaces")
public class NamespaceProperties {
    private List<String> allowed = new ArrayList<>();
    private String defaultNamespace;
}
