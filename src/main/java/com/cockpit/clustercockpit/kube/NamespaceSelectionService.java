package com.cockpit.clustercockpit.kube;

import jakarta.annotation.PostConstruct;
import java.util.List;
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

    public String selectAndResolve(String requested, List<String> available) {
        select(requested);
        if (selected != null && !selected.isBlank()
                && ("*".equals(selected) || available.contains(selected))) {
            return selected;
        }
        if (available.contains("default")) {
            return "default";
        }
        return available.isEmpty() ? "*" : available.getFirst();
    }
}
