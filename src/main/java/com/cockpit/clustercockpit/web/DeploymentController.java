package com.cockpit.clustercockpit.web;

import com.cockpit.clustercockpit.kube.DeploymentService;
import com.cockpit.clustercockpit.kube.NamespaceSelectionService;
import com.cockpit.clustercockpit.kube.NamespaceService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final NamespaceService namespaceService;
    private final NamespaceSelectionService namespaceSelection;

    @GetMapping
    public String list(@RequestParam(value = "namespace", required = false) String namespace,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                       Model model) {
        if (hxRequest == null) {
            model.addAttribute("initialPath", "/deployments");
            return "index";
        }
        populate(namespace, model);
        return "fragments/deployment-list :: page";
    }

    @GetMapping("/{namespace}/{name}/set-image-form")
    public String setImageForm(@PathVariable String namespace,
                               @PathVariable String name,
                               Model model) {
        model.addAttribute("namespace", namespace);
        model.addAttribute("deploymentName", name);
        try {
            var containers = deploymentService.getContainers(namespace, name);
            model.addAttribute("containers", containers);
            model.addAttribute("multiContainer", containers.size() > 1);
            if (!containers.isEmpty()) {
                model.addAttribute("defaultBase", containers.getFirst().imageBase());
                model.addAttribute("defaultTag", containers.getFirst().imageTag());
            }
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "fragments/set-image-form :: form";
    }

    @PostMapping("/set-image")
    public String setImage(@RequestParam("namespace") String namespace,
                           @RequestParam("name") String name,
                           @RequestParam("container") String container,
                           @RequestParam("imageBase") String imageBase,
                           @RequestParam("tag") String tag,
                           HttpServletResponse response,
                           Model model) {
        try {
            deploymentService.setImage(namespace, name, container, imageBase + ":" + tag);
            response.setHeader("HX-Trigger-After-Settle",
                toastTrigger("Image updated for " + name + " / " + container));
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        populate(namespaceSelection.getSelected(), model);
        return "fragments/deployment-list :: table";
    }

    private String toastTrigger(String message) {
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"showToast\":{\"message\":\"" + escaped + "\",\"kind\":\"success\"}}";
    }

    private void populate(String namespace, Model model) {
        List<String> namespaces = List.of();
        try {
            namespaces = namespaceService.listNamespaces();
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("namespaces", namespaces);

        if (namespace != null && !namespace.isBlank()) {
            namespaceSelection.select(namespace);
        }
        String resolved = resolveNamespace(namespaceSelection.getSelected(), namespaces);
        model.addAttribute("namespace", resolved);

        try {
            model.addAttribute("deployments", deploymentService.listDeployments(resolved));
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
    }

    private String resolveNamespace(String requested, List<String> namespaces) {
        if (requested != null && !requested.isBlank()
            && ("*".equals(requested) || namespaces.contains(requested))) {
            return requested;
        }
        if (namespaces.contains("default")) {
            return "default";
        }
        return namespaces.isEmpty() ? "*" : namespaces.getFirst();
    }
}
