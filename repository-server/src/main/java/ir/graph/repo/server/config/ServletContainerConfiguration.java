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
            // npm sends @scope%2Fname. Spring Security needs a decoded servletPath:
            // passthrough leaves '%' there, which StrictHttpFirewall correctly rejects.
            // getRequestURI() stays raw. RequestAuditFilter validates that original
            // URI with RequestPaths before security routing; do not decode servletPath again.
            connector.setEncodedSolidusHandling("decode");
            connector.setEncodedReverseSolidusHandling("reject");
        });
    }
}
