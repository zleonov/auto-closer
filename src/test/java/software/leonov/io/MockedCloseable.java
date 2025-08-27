package software.leonov.io;

final class MockCloseable implements AutoCloseable {

    private boolean closed = false;

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

    @FunctionalInterface
    static interface CheckedRunnable {
        void run() throws Exception;
    }
}
