package com.cockpit.clustercockpit.web;

import com.cockpit.clustercockpit.kube.HelmReleaseService;
import com.cockpit.clustercockpit.kube.NamespaceSelectionService;
import com.cockpit.clustercockpit.kube.NamespaceService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/helmreleases")
public class HelmReleaseController {

    private final HelmReleaseService helmReleaseService;
    private final NamespaceService namespaceService;
    private final NamespaceSelectionService namespaceSelection;

    @GetMapping
    public String list(@RequestParam(value = "namespace", required = false) String namespace,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                       Model model,
                       HttpServletResponse response) {
        if (hxRequest == null) {
            model.addAttribute("initialPath", "/helmreleases");
            return "index";
        }
        response.setHeader("Cache-Control", "no-cache");
        populate(namespace, model);
        return "fragments/helmrelease-list :: page";
    }

    @PostMapping("/suspend")
    public String suspend(@RequestParam("namespace") String namespace,
                          @RequestParam("name") String name,
                          HttpServletResponse response,
                          Model model) {
        try {
            helmReleaseService.suspend(namespace, name);
            response.setHeader("HX-Trigger-After-Settle", toastTrigger("Successfully suspended reconciliation for " + name));
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        populate(namespaceSelection.getSelected(), model);
        return "fragments/helmrelease-list :: table";
    }

    @PostMapping("/resume")
    public String resume(@RequestParam("namespace") String namespace,
                         @RequestParam("name") String name,
                         HttpServletResponse response,
                         Model model) {
        try {
            helmReleaseService.resume(namespace, name);
            response.setHeader("HX-Trigger-After-Settle", toastTrigger("Successfully resumed reconciliation for " + name));
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        populate(namespaceSelection.getSelected(), model);
        return "fragments/helmrelease-list :: table";
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
            model.addAttribute("releases", helmReleaseService.listHelmReleases(resolved));
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
