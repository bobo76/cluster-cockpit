package com.cockpit.clustercockpit.web;

import com.cockpit.clustercockpit.kube.ClusterConnectionService;
import com.cockpit.clustercockpit.kube.KubeconfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class ShellModelAdvice {

    private final KubeconfigService kubeconfigService;
    private final ClusterConnectionService clusterConnectionService;

    @ModelAttribute("kubeconfigPath")
    public Object kubeconfigPath() {
        return kubeconfigService.getCurrentPath();
    }

    @ModelAttribute("connections")
    public Object connections() {
        return clusterConnectionService.listConnectionRows();
    }

    @ModelAttribute("selectedConnection")
    public String selectedConnection() {
        return clusterConnectionService.getSelected();
    }
}
