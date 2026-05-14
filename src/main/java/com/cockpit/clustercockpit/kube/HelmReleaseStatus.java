package com.cockpit.clustercockpit.kube;

public enum HelmReleaseStatus {
    READY("Ready", "status-pill ok"),
    SUSPENDED("Suspended", "status-pill warn"),
    FAILED("Failed", "status-pill bad");

    private final String label;
    private final String cssClass;

    HelmReleaseStatus(String label, String cssClass) {
        this.label = label;
        this.cssClass = cssClass;
    }

    public String label() {
        return label;
    }

    public String cssClass() {
        return cssClass;
    }
}
