package com.cockpit.clustercockpit.kube;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClusterConnectionService {

    private static final String KUBECONFIG_FILE = "kubeconfig";

    private final ClusterConnectionsProperties properties;
    private String selected;

    @PostConstruct
    void init() {
        if (properties.getDefaultConnection() != null && !properties.getDefaultConnection().isBlank()) {
            selected = properties.getDefaultConnection();
        }
    }

    public List<String> listConnections() {
        Path root = rootPath();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            entries
                .filter(Files::isDirectory)
                .filter(p -> Files.isRegularFile(p.resolve(KUBECONFIG_FILE)))
                .map(p -> p.getFileName().toString())
                .sorted()
                .forEach(names::add);
        } catch (IOException e) {
            throw new IllegalStateException("failed to list cluster connections in " + root, e);
        }
        return filterAllowed(names);
    }

    public List<ConnectionRow> listConnectionRows() {
        List<String> names = listConnections();
        Map<String, String> aliases = properties.getAllowed();
        String prefix = longestCommonDashPrefix(names);
        List<ConnectionRow> rows = new ArrayList<>(names.size());
        for (String name : names) {
            String alias = aliases == null ? null : aliases.get(name);
            String label;
            if (alias != null && !alias.isBlank()) {
                label = alias;
            } else {
                label = prefix.isEmpty() ? name : name.substring(prefix.length());
            }
            rows.add(new ConnectionRow(name, label));
        }
        return rows;
    }

    public String getSelected() {
        return selected;
    }

    public void select(String name) {
        this.selected = name;
    }

    public Path getActiveKubeconfigPath() {
        if (selected == null || selected.isBlank()) {
            return null;
        }
        return rootPath().resolve(selected).resolve(KUBECONFIG_FILE);
    }

    private Path rootPath() {
        return Paths.get(properties.getRoot());
    }

    private List<String> filterAllowed(List<String> names) {
        Map<String, String> allowed = properties.getAllowed();
        if (allowed == null || allowed.isEmpty()) {
            return names;
        }
        Set<String> present = new LinkedHashSet<>(names);
        List<String> result = new ArrayList<>();
        for (String configured : allowed.keySet()) {
            if (present.contains(configured)) {
                result.add(configured);
            }
        }
        return result;
    }

    static String longestCommonDashPrefix(List<String> names) {
        if (names.size() < 2) {
            return "";
        }
        String first = names.getFirst();
        int end = first.length();
        for (int i = 1; i < names.size(); i++) {
            String other = names.get(i);
            int limit = Math.min(end, other.length());
            int j = 0;
            while (j < limit && first.charAt(j) == other.charAt(j)) {
                j++;
            }
            end = j;
            if (end == 0) {
                return "";
            }
        }
        int lastDash = first.lastIndexOf('-', end - 1);
        if (lastDash < 0) {
            return "";
        }
        return first.substring(0, lastDash + 1);
    }
}
