package com.cockpit.clustercockpit.kube;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NamespaceSelectionService {

    private final NamespaceProperties properties;
    private String selected;

    @PostConstruct
    void init() {
        String configured = properties.getDefaultNamespace();
        if (configured != null && !configured.isBlank()) {
            selected = configured;
        }
    }

    public String getSelected() {
        return selected;
    }

    public void select(String namespace) {
        if (namespace != null && !namespace.isBlank()) {
            this.selected = namespace;
        }
    }
}
