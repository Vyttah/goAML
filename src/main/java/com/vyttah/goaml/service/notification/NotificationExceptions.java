package com.vyttah.goaml.service.notification;

/**
 * Notification service exceptions (Phase 10). Mapped to HTTP status in {@code GlobalExceptionHandler}.
 */
public final class NotificationExceptions {

    private NotificationExceptions() {}

    /**
     * A notification does not exist for this recipient — either it never existed, or it belongs to another
     * user (which, for the requester, is indistinguishable from missing → 404).
     */
    public static class NotificationNotFoundException extends RuntimeException {
        public NotificationNotFoundException(String message) {
            super(message);
        }
    }
}
