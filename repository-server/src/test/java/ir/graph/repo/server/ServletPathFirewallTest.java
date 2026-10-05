package ir.graph.repo.server;

import ir.graph.repo.core.protocol.RequestPaths;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import static org.junit.jupiter.api.Assertions.*;

/** Firewall contract with explicit servlet views; TomcatWireTest covers real HTTP. */
class ServletPathFirewallTest {
    private StrictHttpFirewall firewall() {
        var firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }
    private MockHttpServletRequest request(String raw, String mapped) {
        var request = new MockHttpServletRequest("PUT", raw);
        request.setServletPath(mapped);
        return request;
    }
    @Test void passthroughMappingRemainsRejectedDespiteAllowedEncodedSlash() {
        String raw = "/repository/npm-hosted/@wire%2Fdemo";
        var request = request(raw, raw);
        assertThrows(RequestRejectedException.class, () -> firewall().getFirewalledRequest(request));
    }
    @Test void decodedMappingRetainsOriginalRawUriAndPassesFirewall() {
        String raw = "/repository/npm-hosted/@wire%2Fdemo";
        String mapped = "/repository/npm-hosted/@wire/demo";
        var wrapped = firewall().getFirewalledRequest(request(raw, mapped));
        assertEquals(raw, wrapped.getRequestURI());
        assertEquals(mapped, wrapped.getServletPath());
        assertEquals(mapped, RequestPaths.decode(wrapped.getRequestURI()));
    }
    @Test void percentAndOtherStrictProtectionsAreNotRelaxed() {
        var firewall = firewall();
        assertTrue(firewall.getDecodedUrlBlocklist().contains("%"));
        assertTrue(firewall.getEncodedUrlBlocklist().contains("%25"));
        assertThrows(RequestRejectedException.class, () -> firewall.getFirewalledRequest(
                request("/repository/raw-hosted/a%252fb", "/repository/raw-hosted/a%2fb")));
        assertThrows(RequestRejectedException.class, () -> firewall.getFirewalledRequest(
                request("/repository/raw-hosted/a%2f%2fb", "/repository/raw-hosted/a//b")));
        assertThrows(RequestRejectedException.class, () -> firewall.getFirewalledRequest(
                request("/repository/raw-hosted/a%5cb", "/repository/raw-hosted/a\\b")));
    }
}
