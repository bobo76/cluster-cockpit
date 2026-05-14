package com.cockpit.clustercockpit.kube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HelmReleaseService {

    private static final String KIND = "helmreleases.helm.toolkit.fluxcd.io";
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long MINUTES_PER_HOUR = 60;
    private static final long HOURS_PER_DAY = 24;
    private static final long HOURS_THRESHOLD_FOR_DAYS = 48;

    private final KubectlRunner kubectl;
    private final ObjectMapper mapper;

    public List<HelmReleaseRow> listHelmReleases(String namespace) {
        String json = kubectl.run(buildListArgs(namespace));
        return parse(json);
    }

    public void suspend(String namespace, String name) {
        patchSuspend(namespace, name, true);
    }

    public void resume(String namespace, String name) {
        patchSuspend(namespace, name, false);
    }

    private void patchSuspend(String namespace, String name, boolean suspended) {
        String payload = "{\"spec\":{\"suspend\":" + suspended + "}}";
        log.info("HelmRelease patch request: ns={} name={} suspend={}", namespace, name, suspended);
        Path patchFile = null;
        try {
            patchFile = Files.createTempFile("helmrelease-patch-", ".json");
            Files.writeString(patchFile, payload);
            String result = kubectl.run(
                "patch", KIND + "/" + name,
                "-n", namespace,
                "--type=merge",
                "--patch-file=" + patchFile.toAbsolutePath()
            );
            if (result != null && !result.isBlank()) {
                log.info("HelmRelease patch response: {}", result.trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to write patch file", e);
        } finally {
            if (patchFile != null) {
                try {
                    Files.deleteIfExists(patchFile);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private String[] buildListArgs(String namespace) {
        if (namespace == null || namespace.isBlank() || "*".equals(namespace.trim())) {
            return new String[] {"get", KIND, "--all-namespaces", "-o", "json"};
        }
        return new String[] {"get", KIND, "-n", namespace.trim(), "-o", "json"};
    }

    private List<HelmReleaseRow> parse(String json) {
        List<HelmReleaseRow> rows = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            for (JsonNode item : root.path("items")) {
                rows.add(toRow(item));
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse kubectl helmrelease output", e);
        }
        return rows;
    }

    private HelmReleaseRow toRow(JsonNode item) {
        JsonNode meta = item.path("metadata");
        JsonNode spec = item.path("spec");
        boolean suspended = spec.path("suspend").asBoolean(false);
        Chart chart = parseChart(spec);
        Ready ready = parseReady(item.path("status"));
        HelmReleaseStatus status = deriveStatus(suspended, ready.state());
        return new HelmReleaseRow(meta.path("namespace").asText(""), meta.path("name").asText(""),
            chart.name(), chart.version(), suspended, status, ready.message(),
            formatAge(meta.path("creationTimestamp").asText(null)));
    }

    private Chart parseChart(JsonNode spec) {
        JsonNode chartSpec = spec.path("chart").path("spec");
        String name = chartSpec.path("chart").asText("");
        String version = chartSpec.path("version").asText("");
        if (name.isEmpty()) {
            name = spec.path("chartRef").path("name").asText("");
        }
        return new Chart(name, version);
    }

    private Ready parseReady(JsonNode status) {
        JsonNode cond = readyCondition(status);
        return new Ready(cond.path("status").asText(""), cond.path("message").asText(""));
    }

    private record Chart(String name, String version) { }

    private record Ready(String state, String message) { }

    private HelmReleaseStatus deriveStatus(boolean suspended, String ready) {
        if (suspended) {
            return HelmReleaseStatus.SUSPENDED;
        }
        if ("True".equals(ready)) {
            return HelmReleaseStatus.READY;
        }
        return HelmReleaseStatus.FAILED;
    }

    private JsonNode readyCondition(JsonNode status) {
        for (JsonNode c : status.path("conditions")) {
            if ("Ready".equals(c.path("type").asText(""))) {
                return c;
            }
        }
        return mapper.createObjectNode();
    }

    private String formatAge(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "";
        }
        try {
            Duration d = Duration.between(Instant.parse(timestamp), Instant.now());
            long secs = d.getSeconds();
            if (secs < SECONDS_PER_MINUTE) {
                return secs + "s";
            }
            long mins = secs / SECONDS_PER_MINUTE;
            if (mins < MINUTES_PER_HOUR) {
                return mins + "m";
            }
            long hours = mins / MINUTES_PER_HOUR;
            if (hours < HOURS_THRESHOLD_FOR_DAYS) {
                return hours + "h";
            }
            return (hours / HOURS_PER_DAY) + "d";
        } catch (DateTimeParseException e) {
            return "";
        }
    }
}
