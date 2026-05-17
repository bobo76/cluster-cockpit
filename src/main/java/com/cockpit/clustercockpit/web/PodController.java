package com.cockpit.clustercockpit.web;

import com.cockpit.clustercockpit.kube.NamespaceSelectionService;
import com.cockpit.clustercockpit.kube.NamespaceService;
import com.cockpit.clustercockpit.kube.PodService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PodController {

    private final PodService podService;
    private final NamespaceService namespaceService;
    private final NamespaceSelectionService namespaceSelection;

    @GetMapping("/pods")
    public String pods(@RequestParam(value = "namespace", required = false) String namespace,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                       Model model,
                       HttpServletResponse response) {
        if (hxRequest == null) {
            model.addAttribute("initialPath", "/pods");
            return "index";
        }
        response.setHeader("Cache-Control", "no-cache");
        List<String> namespaces = List.of();
        try {
            namespaces = namespaceService.listNamespaces();
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("namespaces", namespaces);

        String resolved = namespaceSelection.selectAndResolve(namespace, namespaces);
        model.addAttribute("namespace", resolved);

        try {
            model.addAttribute("pods", podService.listPods(resolved));
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "fragments/pod-list :: page";
    }

    @GetMapping("/pods/{namespace}/{name}/detail")
    public String podDetail(@PathVariable String namespace,
                            @PathVariable String name,
                            Model model) {
        try {
            model.addAttribute("pod", podService.getPodDetail(namespace, name));
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("podNamespace", namespace);
            model.addAttribute("podName", name);
        }
        return "fragments/pod-detail :: panel";
    }

}
