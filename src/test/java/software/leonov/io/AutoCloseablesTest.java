package software.leonov.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

public final class AutoCloseablesTest {

    @Test
    public void test_closeQuietly_null_does_not_invoke_consumer() {
        final AtomicInteger       count    = new AtomicInteger(0);
        final Consumer<Throwable> consumer = throwable -> count.incrementAndGet();

        AutoCloseables.closeQuietly(null, consumer);

        assertEquals(0, count.get(), "Consumer should not be called for null resource");
    }

    @Test
    public void test_closeQuietly_no_error_does_not_invoke_consumer() {
        final MockCloseable resource = new MockCloseable();

        final AtomicInteger       count    = new AtomicInteger(0);
        final Consumer<Throwable> consumer = throwable -> count.incrementAndGet();

        AutoCloseables.closeQuietly(resource, consumer);

        assertTrue(resource.isClosed(), "Resource should be closed");
        assertEquals(0, count.get(), "Consumer should not be called for successful close");
    }

    @Test
    public void test_closeQuietly_Exception_invokes_consumer() {
        final Exception     exception = new Exception("Test Exception during close");
        final MockCloseable resource  = MockCloseable.onClose(() -> {
                                          throw exception;
                                      });

        final List<Throwable>     errors   = new ArrayList<>();
        final Consumer<Throwable> consumer = errors::add;

        AutoCloseables.closeQuietly(resource, consumer);

        assertTrue(resource.isClosed(), "Resource should be marked as closed even with exception");
        assertSame(exception, errors.get(0), "Consumer should receive the thrown exception");
    }

    @Test
    public void test_closeQuietly_RuntimeException_invokes_consumer() {
        final RuntimeException runtimeException = new RuntimeException("Test RuntimeException during close");
        final MockCloseable    resource         = MockCloseable.onClose(() -> {
                                                    throw runtimeException;
                                                });

        final List<Throwable>     errors   = new ArrayList<>();
        final Consumer<Throwable> consumer = errors::add;

        AutoCloseables.closeQuietly(resource, consumer);

        assertTrue(resource.isClosed(), "Resource should be marked as closed even with exception");
        assertSame(runtimeException, errors.get(0), "Consumer should receive the thrown exception");
    }

    @Test
    public void test_closeQuietly_Error_invokes_consumer() {
        final RuntimeException error    = new RuntimeException("Test error during close");
        final MockCloseable    resource = MockCloseable.onClose(() -> {
                                            throw error;
                                        });

        final List<Throwable>     errors   = new ArrayList<>();
        final Consumer<Throwable> consumer = errors::add;

        AutoCloseables.closeQuietly(resource, consumer);

        assertTrue(resource.isClosed(), "Resource should be marked as closed even with exception");
        assertSame(error, errors.get(0), "Consumer should receive the thrown exception");
    }

    @Test
    public void test_closeQuietly_multiple_resources_collect_exceptions() {
        final List<Throwable>     errors   = new ArrayList<>();
        final Consumer<Throwable> consumer = errors::add;

        // Test multiple resources with different exception types
        final IOException      err1 = new IOException("IOException");
        final RuntimeException err2 = new RuntimeException("RuntimeException");
        final Error            err3 = new Error("Error");

        final MockCloseable resource1 = MockCloseable.onClose(() -> {
            throw err1;
        });

        final MockCloseable resource2 = MockCloseable.onClose(() -> {
            throw err2;
        });

        final MockCloseable resource3 = MockCloseable.onClose(() -> {
            throw err3;
        });

        AutoCloseables.closeQuietly(resource1, consumer);
        AutoCloseables.closeQuietly(resource2, consumer);
        AutoCloseables.closeQuietly(resource3, consumer);

        assertEquals(3, errors.size(), "Should have collected 3 exceptions");
        assertSame(err1, errors.get(0));
        assertSame(err2, errors.get(1));
        assertSame(err3, errors.get(2));

        assertTrue(resource1.isClosed(), "Resource1 should be closed");
        assertTrue(resource2.isClosed(), "Resource2 should be closed");
        assertTrue(resource3.isClosed(), "Resource3 should be closed");
    }

}