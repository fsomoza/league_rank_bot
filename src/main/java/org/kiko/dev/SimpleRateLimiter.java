package org.kiko.dev;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleRateLimiter {
    private final ConcurrentHashMap<String, Instant> endpointCooldowns;

    public SimpleRateLimiter() {
        this.endpointCooldowns = new ConcurrentHashMap<>();
    }

    /**
     * Checks if the endpoint is currently rate limited
     * @param endpoint The API endpoint to check
     * @return true if the request can proceed, false if it's rate limited
     */
    public boolean canProceed(String endpoint) {
        Instant cooldownUntil = endpointCooldowns.get(endpoint);
        return cooldownUntil == null || Instant.now().isAfter(cooldownUntil);
    }

    /**
     * Updates the rate limit for an endpoint based on the API response
     * @param endpoint The API endpoint
     * @param response The HTTP response from the API
     */
    public void updateRateLimit(String endpoint, HttpResponse<?> response) {
        Optional<String> retryAfter = Optional.ofNullable(response.headers().firstValue("Retry-After").orElse(null));

        if (retryAfter.isPresent()) {
            try {
                int secondsToWait = Integer.parseInt(retryAfter.get());
                Instant cooldownUntil = Instant.now().plusSeconds(secondsToWait);
                endpointCooldowns.put(endpoint, cooldownUntil);
            } catch (NumberFormatException e) {
                // If header parsing fails, implement a default backoff
                Instant cooldownUntil = Instant.now().plusSeconds(5);
                endpointCooldowns.put(endpoint, cooldownUntil);
            }
        }
    }

    /**
     * Waits until the rate limit cooldown expires
     * @param endpoint The API endpoint
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public void awaitRateLimit(String endpoint) throws InterruptedException {
        Instant cooldownUntil = endpointCooldowns.get(endpoint);
        if (cooldownUntil != null && Instant.now().isBefore(cooldownUntil)) {
            Duration waitTime = Duration.between(Instant.now(), cooldownUntil);
            Thread.sleep(waitTime.toMillis());
        }
    }
}