package software.leonov.io;

/**
 * Mock {@code AutoCloseable} for testing.
 */
class MockCloseable implements AutoCloseable {
    private boolean               closed = false;
    private final CheckedRunnable action;

    public MockCloseable() {
        this(() -> {
        });
    }

    private MockCloseable(final CheckedRunnable onClose) {
        this.action = onClose;
    }

    public static MockCloseable onClose(final CheckedRunnable action) {
        return new MockCloseable(action);
    }

    @Override
    public void close() throws Exception {
        closed = true;
        action.run();
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * A {@code Runnable} analog which can throw a checked Exception.
     */
    @FunctionalInterface
    static interface CheckedRunnable {
        void run() throws Exception;
    }
}
