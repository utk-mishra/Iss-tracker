package com.example.iss.resilience;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * De-duplicates concurrent calls for the same key: the first caller ("leader") runs
 * {@code work}; any callers that arrive for the same key while the leader is still
 * running ("followers") block and receive the leader's result or failure instead of
 * invoking {@code work} themselves. Once the call resolves, the in-flight entry for
 * that key is always removed (success or failure) so the next sequential call starts
 * a fresh execution and a failure can never leave a stuck entry blocking future calls.
 *
 * <p>This is request de-duplication, not a cache: nothing is retained after resolution.
 *
 * @param <T> the type produced by the de-duplicated work
 */
public final class SingleFlight<T> {

    private final ConcurrentHashMap<String, CompletableFuture<T>> inFlight = new ConcurrentHashMap<>();

    /**
     * Runs {@code work} for {@code key}, or waits for an already in-flight call for the
     * same key to resolve and returns/rethrows its outcome.
     *
     * @throws Exception whatever {@code work} throws (leader), or an {@link ExecutionException}
     *                    wrapping the leader's failure (followers)
     */
    public T execute(String key, Callable<T> work) throws Exception {
        // Use AtomicBoolean to track if this thread is the leader
        AtomicBoolean isLeader = new AtomicBoolean(false);
        
        // Atomically get or create the future for this key
        CompletableFuture<T> future = inFlight.computeIfAbsent(key, k -> {
            isLeader.set(true);
            return new CompletableFuture<>();
        });
        
        // If we created the future, we're the leader
        if (isLeader.get()) {
            return runAsLeader(key, future, work);
        } else {
            // We're a follower, wait for the leader's result
            return awaitFollowerResult(future);
        }
    }

    private T runAsLeader(String key, CompletableFuture<T> future, Callable<T> work) throws Exception {
        try {
            T result = work.call();
            future.complete(result);
            return result;
        } catch (Exception e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            // Always remove, success or failure, so a failure can never leave a stuck entry.
            inFlight.remove(key, future);
        }
    }

    private T awaitFollowerResult(CompletableFuture<T> future) throws Exception {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
