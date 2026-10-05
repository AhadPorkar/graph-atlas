package ir.graph.repo.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletContainerConfiguration {
    @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> packagePathSupport() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            // npm sends @scope%2fname. We decode once in RequestPaths, not in the connector.
            connector.setEncodedSolidusHandling("passthrough");
            connector.setEncodedReverseSolidusHandling("reject");
        });
    }
}
