package com.cockpit.clustercockpit.web;

import com.cockpit.clustercockpit.kube.ClusterConnectionService;
import com.cockpit.clustercockpit.kube.KubeconfigService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final KubeconfigService kubeconfigService;
    private final ClusterConnectionService clusterConnectionService;

    @GetMapping("/")
    public String index(Model model) {
        populateModel(model);
        return "index";
    }

    @GetMapping("/cluster-rail")
    public String clusterRail(Model model) {
        populateModel(model);
        return "fragments/cluster-rail :: rail";
    }

    @PostMapping("/connections/select")
    public String selectConnection(@RequestParam("connection") String connection,
                                   Model model,
                                   HttpServletResponse response) {
        clusterConnectionService.select(connection);
        populateModel(model);
        response.setHeader("HX-Trigger", "cluster-changed");
        return "fragments/cluster-rail :: rail";
    }

    private void populateModel(Model model) {
        model.addAttribute("kubeconfigPath", kubeconfigService.getCurrentPath());
        model.addAttribute("connections", clusterConnectionService.listConnectionRows());
        model.addAttribute("selectedConnection", clusterConnectionService.getSelected());
    }
}
