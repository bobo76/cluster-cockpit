package com.cockpit.clustercockpit.kube;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KubectlRunner {

    private final KubeconfigService kubeconfigService;

    public String run(String... args) {
        Path kubeconfig = kubeconfigService.getCurrentPath();
        if (kubeconfig == null) {
            throw new KubectlException("no cluster connection selected");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("kubectl");
        cmd.add("--kubeconfig=" + kubeconfig);
        for (String a : args) {
            cmd.add(a);
        }
        return exec(cmd);
    }

    private String exec(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new KubectlException("kubectl timed out: " + String.join(" ", cmd));
            }
            if (p.exitValue() != 0) {
                throw new KubectlException("kubectl failed (" + p.exitValue() + "): " + stderr.trim());
            }
            return stdout;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new KubectlException("kubectl execution error: " + e.getMessage(), e);
        }
    }

    public static class KubectlException extends RuntimeException {
        public KubectlException(String msg) { super(msg); }
        public KubectlException(String msg, Throwable cause) { super(msg, cause); }
    }
}
