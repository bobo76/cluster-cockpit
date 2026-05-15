package com.cockpit.clustercockpit.kube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
public class DeploymentService {

    private static final long SECONDS_PER_MINUTE = 60;
    private static final long MINUTES_PER_HOUR = 60;
    private static final long HOURS_PER_DAY = 24;
    private static final long HOURS_THRESHOLD_FOR_DAYS = 48;

    private final KubectlRunner kubectl;
    private final ObjectMapper mapper;

    public List<DeploymentRow> listDeployments(String namespace) {
        String json = kubectl.run(buildListArgs(namespace));
        return parse(json);
    }

    public List<ContainerInfo> getContainers(String namespace, String name) {
        String json = kubectl.run("get", "deployment", name, "-n", namespace, "-o", "json");
        try {
            JsonNode root = mapper.readTree(json);
            return parseContainers(root.path("spec").path("template").path("spec"));
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse deployment JSON", e);
        }
    }

    public void setImage(String namespace, String name, String container, String image) {
        log.info("setImage: deployment={}/{} container={} image={}", namespace, name, container, image);
        kubectl.run("set", "image", "deployment/" + name, container + "=" + image, "-n", namespace);
    }

    private String[] buildListArgs(String namespace) {
        if (namespace == null || namespace.isBlank() || "*".equals(namespace.trim())) {
            return new String[]{"get", "deployments", "--all-namespaces", "-o", "json"};
        }
        return new String[]{"get", "deployments", "-n", namespace.trim(), "-o", "json"};
    }

    private List<DeploymentRow> parse(String json) {
        List<DeploymentRow> rows = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            for (JsonNode item : root.path("items")) {
                rows.add(toRow(item));
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse kubectl deployments output", e);
        }
        return rows;
    }

    private DeploymentRow toRow(JsonNode item) {
        JsonNode meta = item.path("metadata");
        JsonNode spec = item.path("spec");
        JsonNode status = item.path("status");

        int desired = spec.path("replicas").asInt(0);
        int readyReplicas = status.path("readyReplicas").asInt(0);
        int upToDate = status.path("updatedReplicas").asInt(0);
        int available = status.path("availableReplicas").asInt(0);

        return new DeploymentRow(
            meta.path("namespace").asText(""),
            meta.path("name").asText(""),
            readyReplicas + "/" + desired,
            upToDate,
            available,
            formatAge(meta.path("creationTimestamp").asText(null)),
            parseContainers(spec.path("template").path("spec"))
        );
    }

    private List<ContainerInfo> parseContainers(JsonNode podSpec) {
        List<ContainerInfo> list = new ArrayList<>();
        for (JsonNode c : podSpec.path("containers")) {
            list.add(new ContainerInfo(c.path("name").asText(""), c.path("image").asText("")));
        }
        return list;
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
