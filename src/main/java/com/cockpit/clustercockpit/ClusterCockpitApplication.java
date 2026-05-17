package com.cockpit.clustercockpit;

import com.cockpit.clustercockpit.kube.ClusterConnectionsProperties;
import com.cockpit.clustercockpit.kube.NamespaceProperties;
import java.awt.Desktop;
import java.net.URI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@SpringBootApplication
@EnableConfigurationProperties({ClusterConnectionsProperties.class, NamespaceProperties.class})
public class ClusterCockpitApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClusterCockpitApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        var reg = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        reg.addUrlPatterns("/pods", "/pods/*", "/helmreleases", "/helmreleases/*",
            "/deployments", "/deployments/*");
        return reg;
    }

    @Component
    static class BrowserOpener {
        private final Environment env;

        BrowserOpener(Environment env) {
            this.env = env;
        }

        @EventListener(ApplicationReadyEvent.class)
        void openBrowser() {
            String port = env.getProperty("server.port", "8842");
            String url = "http://localhost:" + port;
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                }
            } catch (Exception ignored) {
                // user can open the URL manually
            }
        }
    }
}
