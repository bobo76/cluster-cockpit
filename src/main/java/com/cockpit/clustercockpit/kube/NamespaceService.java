package com.cockpit.clustercockpit.kube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NamespaceService {

    private final KubectlRunner kubectl;
    private final NamespaceProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<String> listNamespaces() {
        List<String> all = fetchAll();
        List<String> allowed = properties.getAllowed();
        if (allowed == null || allowed.isEmpty()) {
            return all;
        }
        Set<String> allowedSet = new LinkedHashSet<>(allowed);
        List<String> filtered = new ArrayList<>();
        for (String ns : all) {
            if (allowedSet.contains(ns)) {
                filtered.add(ns);
            }
        }
        return filtered;
    }

    private List<String> fetchAll() {
        String json = kubectl.run("get", "namespaces", "-o", "json");
        List<String> names = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            for (JsonNode item : root.path("items")) {
                String name = item.path("metadata").path("name").asText("");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse kubectl namespace output", e);
        }
        names.sort(String::compareTo);
        return names;
    }
}
