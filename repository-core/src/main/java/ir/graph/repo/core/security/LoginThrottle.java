package ir.graph.repo.core.security;

import ir.graph.repo.core.domain.RepositoryException;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;

/** Failed-login protection only; never a request/day or licensing quota. Zero disables it. */
public final class LoginThrottle {
    private record Attempt(int count, long until) { }
    private final ConcurrentHashMap<String, Attempt> failures = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    public LoginThrottle(int limit, Duration window) { this(limit, window, Clock.systemUTC()); }
    public LoginThrottle(int limit, Duration window, Clock clock) {
        if (limit < 0 || window.isNegative() || window.isZero()) throw new IllegalArgumentException("Invalid login protection configuration");
        this.limit = limit; this.windowMillis = window.toMillis(); this.clock = clock;
    }
    public void check(String key) {
        Attempt attempt = failures.get(key);
        if (limit > 0 && attempt != null && attempt.until() > clock.millis() && attempt.count() >= limit)
            throw new RepositoryException(429, "LOGIN_THROTTLED", "Repeated failed authentication; retry after the configured window");
    }
    public void failed(String key) {
        if (limit == 0) return;
        long now = clock.millis();
        failures.compute(key, (k, a) -> a == null || a.until() <= now
                ? new Attempt(1, now + windowMillis) : new Attempt(Math.min(limit, a.count() + 1), a.until()));
    }
    public void succeeded(String key) { failures.remove(key); }
    public void clean() { long now = clock.millis(); failures.entrySet().removeIf(e -> e.getValue().until() <= now); }
}
