package com.cockpit.clustercockpit.kube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PodService {

    private final KubectlRunner kubectl;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<PodRow> listPods(String namespace) {
        String json = kubectl.run(buildArgs(namespace));
        return parse(json);
    }

    public PodDetail getPodDetail(String namespace, String name) {
        String json = kubectl.run("get", "pod", name, "-n", namespace, "-o", "json");
        try {
            JsonNode root = mapper.readTree(json);
            return toDetail(root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse kubectl pod detail output", e);
        }
    }

    private PodDetail toDetail(JsonNode pod) {
        JsonNode meta = pod.path("metadata");
        JsonNode spec = pod.path("spec");
        JsonNode status = pod.path("status");

        Map<String, JsonNode> initStatuses = indexByName(status.path("initContainerStatuses"));
        Map<String, JsonNode> mainStatuses = indexByName(status.path("containerStatuses"));

        List<ContainerDetail> initContainers = new ArrayList<>();
        for (JsonNode c : spec.path("initContainers")) {
            initContainers.add(toContainerDetail(c, initStatuses.get(c.path("name").asText(""))));
        }
        List<ContainerDetail> containers = new ArrayList<>();
        for (JsonNode c : spec.path("containers")) {
            containers.add(toContainerDetail(c, mainStatuses.get(c.path("name").asText(""))));
        }
        return new PodDetail(
            meta.path("namespace").asText(""),
            meta.path("name").asText(""),
            initContainers,
            containers
        );
    }

    private Map<String, JsonNode> indexByName(JsonNode array) {
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode n : array) {
            map.put(n.path("name").asText(""), n);
        }
        return map;
    }

    private ContainerDetail toContainerDetail(JsonNode container, JsonNode containerStatus) {
        String name = container.path("name").asText("");
        String image = container.path("image").asText("");
        String statusStr = formatContainerStatus(containerStatus);

        List<EnvVar> env = new ArrayList<>();
        for (JsonNode e : container.path("env")) {
            env.add(toEnvVar(e));
        }
        env.sort(Comparator.comparing(EnvVar::name, String.CASE_INSENSITIVE_ORDER));
        List<MountInfo> mounts = new ArrayList<>();
        for (JsonNode m : container.path("volumeMounts")) {
            mounts.add(new MountInfo(
                m.path("mountPath").asText(""),
                m.path("name").asText(""),
                m.path("readOnly").asBoolean(false)
            ));
        }
        mounts.sort(Comparator.comparing(MountInfo::path, String.CASE_INSENSITIVE_ORDER));
        return new ContainerDetail(name, image, statusStr, env, mounts);
    }

    private EnvVar toEnvVar(JsonNode e) {
        String name = e.path("name").asText("");
        if (e.has("value")) {
            return new EnvVar(name, e.path("value").asText(""), null);
        }
        JsonNode from = e.path("valueFrom");
        if (from.isMissingNode() || from.isNull()) {
            return new EnvVar(name, "", null);
        }
        if (from.has("secretKeyRef")) {
            return new EnvVar(name, null, "from secret "
                + from.path("secretKeyRef").path("name").asText("") + "/"
                + from.path("secretKeyRef").path("key").asText(""));
        }
        if (from.has("configMapKeyRef")) {
            return new EnvVar(name, null, "from configmap "
                + from.path("configMapKeyRef").path("name").asText("") + "/"
                + from.path("configMapKeyRef").path("key").asText(""));
        }
        if (from.has("fieldRef")) {
            return new EnvVar(name, null, "from field "
                + from.path("fieldRef").path("fieldPath").asText(""));
        }
        if (from.has("resourceFieldRef")) {
            return new EnvVar(name, null, "from resource "
                + from.path("resourceFieldRef").path("resource").asText(""));
        }
        Iterator<String> keys = from.fieldNames();
        return new EnvVar(name, null, keys.hasNext() ? "from " + keys.next() : "");
    }

    private String formatContainerStatus(JsonNode cs) {
        if (cs == null || cs.isMissingNode() || cs.isNull()) {
            return "unknown";
        }
        boolean ready = cs.path("ready").asBoolean(false);
        String readyPart = ready ? "ready" : "not ready";
        JsonNode state = cs.path("state");
        if (state.has("running")) {
            return "running, " + readyPart;
        }
        if (state.has("terminated")) {
            JsonNode t = state.path("terminated");
            String reason = t.path("reason").asText("");
            int code = t.path("exitCode").asInt(0);
            String reasonPart = reason.isBlank() ? "" : " - " + reason;
            return "terminated, " + readyPart + reasonPart + " (exit code: " + code + ")";
        }
        if (state.has("waiting")) {
            String reason = state.path("waiting").path("reason").asText("");
            return "waiting" + (reason.isBlank() ? "" : " - " + reason);
        }
        return readyPart;
    }

    private String[] buildArgs(String namespace) {
        if (namespace == null || namespace.isBlank() || "*".equals(namespace.trim())) {
            return new String[] {"get", "pods", "--all-namespaces", "-o", "json"};
        }
        return new String[] {"get", "pods", "-n", namespace.trim(), "-o", "json"};
    }

    private List<PodRow> parse(String json) {
        List<PodRow> rows = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode items = root.path("items");
            for (JsonNode item : items) {
                rows.add(toRow(item));
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse kubectl pod output", e);
        }
        return rows;
    }

    private PodRow toRow(JsonNode item) {
        JsonNode meta = item.path("metadata");
        JsonNode spec = item.path("spec");
        JsonNode status = item.path("status");

        String namespace = meta.path("namespace").asText("");
        String name = meta.path("name").asText("");
        String phase = status.path("phase").asText("");
        String node = spec.path("nodeName").asText("");

        int total = 0;
        int ready = 0;
        int restarts = 0;
        for (JsonNode cs : status.path("containerStatuses")) {
            total++;
            if (cs.path("ready").asBoolean(false)) {
                ready++;
            }
            restarts += cs.path("restartCount").asInt(0);
        }
        String readyStr = ready + "/" + total;
        String age = formatAge(meta.path("creationTimestamp").asText(null));
        return new PodRow(namespace, name, readyStr, phase, restarts, age, node);
    }

    private String formatAge(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "";
        }
        try {
            Duration d = Duration.between(Instant.parse(timestamp), Instant.now());
            long secs = d.getSeconds();
            if (secs < 60) {
                return secs + "s";
            }
            long mins = secs / 60;
            if (mins < 60) {
                return mins + "m";
            }
            long hours = mins / 60;
            if (hours < 48) {
                return hours + "h";
            }
            return (hours / 24) + "d";
        } catch (DateTimeParseException e) {
            return "";
        }
    }
}
