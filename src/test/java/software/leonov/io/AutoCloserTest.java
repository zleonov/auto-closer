package software.leonov.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public final class AutoCloserTest {

    @Test
    public void test_register_returns_same_resource() throws Exception {
        final AutoCloser    closer   = new AutoCloser();
        final MockCloseable resource = new MockCloseable();

        final MockCloseable result = closer.register(resource);

        assertSame(resource, result, "register() should return the same resource instance");
        closer.close();
    }

    @Test
    public void test_register_null_returns_null() throws Exception {
        final AutoCloser closer = new AutoCloser();

        final MockCloseable result = closer.register(null);

        assertNull(result, "register(null) should return null");
        closer.close();
    }

    @Test
    public void test_register_null_does_not_affect_closing() throws Exception {
        final AutoCloser    closer    = new AutoCloser();
        final MockCloseable resource1 = new MockCloseable();
        final MockCloseable resource2 = new MockCloseable();

        closer.register(resource1);
        closer.register(null);
        closer.register(resource2);

        closer.close();

        assertTrue(resource1.isClosed(), "First resource should be closed");
        assertTrue(resource2.isClosed(), "Second resource should be closed");
    }

    @Test
    public void test_close_without_resources_does_not_throw() throws Exception {
        final AutoCloser closer = new AutoCloser();

        // Should not throw any exception
        closer.close();
    }

    @Test
    public void test_close_single_resource() throws Exception {
        final AutoCloser    closer   = new AutoCloser();
        final MockCloseable resource = new MockCloseable();

        closer.register(resource);
        closer.close();

        assertTrue(resource.isClosed(), "Resource should be closed");
    }

    @Test
    public void test_close_multiple_resources_in_LIFO_order() throws Exception {
        final AutoCloser          closer    = new AutoCloser();
        final List<MockCloseable> resources = new ArrayList<>();
        final List<Integer>       order     = new ArrayList<>();

        // Register resources in order 1, 2, 3
        for (int i = 1; i <= 3; i++) {
            final Integer       id       = new Integer(i);
            final MockCloseable resource = MockCloseable.onClose(() -> order.add(id));
            resources.add(resource);
            closer.register(resource);
        }

        closer.close();

        // All resources should be closed
        for (final MockCloseable resource : resources)
            assertTrue(resource.isClosed(), "All resources should be closed");

        // Should be closed in reverse order: 3, 2, 1
        assertEquals(Arrays.asList(3, 2, 1), order, "Resources should be closed in LIFO order");
    }

    @Test
    public void test_close_with_exception_still_closes_all_resources() {
        final AutoCloser    closer    = new AutoCloser();
        final MockCloseable resource1 = new MockCloseable();
        final MockCloseable resource2 = MockCloseable.onClose(() -> {
                                          throw new IOException("Test exception from resource2");
                                      });
        final MockCloseable resource3 = new MockCloseable();

        closer.register(resource1);
        closer.register(resource2);
        closer.register(resource3);

        final Exception exception = assertThrows(Exception.class, closer::close);

        // All resources should still be closed despite exception
        assertTrue(resource1.isClosed(), "Resource1 should be closed despite exception");
        assertTrue(resource2.isClosed(), "Resource2 should be closed despite exception");
        assertTrue(resource3.isClosed(), "Resource3 should be closed despite exception");

        assertEquals("Test exception from resource2", exception.getMessage());
    }

    @Test
    public void test_close_with_multiple_exceptions_uses_suppressed() {
        final AutoCloser    closer    = new AutoCloser();
        final MockCloseable resource1 = MockCloseable.onClose(() -> {
                                          throw new IOException("Exception from resource1");
                                      });
        final MockCloseable resource2 = MockCloseable.onClose(() -> {
                                          throw new RuntimeException("Exception from resource2");
                                      });
        final MockCloseable resource3 = MockCloseable.onClose(() -> {
                                          throw new IllegalStateException("Exception from resource3");
                                      });

        closer.register(resource1);
        closer.register(resource2);
        closer.register(resource3);

        final Exception exception = assertThrows(Exception.class, closer::close);

        // First exception encountered (resource3, closed first) should be the main exception
        assertEquals("Exception from resource3", exception.getMessage());

        // Other exceptions should be suppressed
        final Throwable[] suppressed = exception.getSuppressed();
        assertEquals(2, suppressed.length, "Should have 2 suppressed exceptions");
        assertEquals("Exception from resource2", suppressed[0].getMessage());
        assertEquals("Exception from resource1", suppressed[1].getMessage());
    }

    @Test
    public void test_try_with_resources_automatically_closes() throws Exception {
        final MockCloseable resource1 = new MockCloseable();
        final MockCloseable resource2 = new MockCloseable();

        try (final AutoCloser closer = new AutoCloser()) {
            closer.register(resource1);
            closer.register(resource2);
        }

        assertTrue(resource1.isClosed(), "Resource1 should be closed by try-with-resources");
        assertTrue(resource2.isClosed(), "Resource2 should be closed by try-with-resources");
    }

    @Test
    public void test_rethrow_null_throws_npe() throws Exception {
        final AutoCloser closer = new AutoCloser();

        final NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            throw closer.rethrow(null);
        });

        assertEquals("th == null", exception.getMessage());
        closer.close();
    }

    @Test
    public void test_rethrow_Exception_preserves_type() throws Exception {
        final AutoCloser closer            = new AutoCloser();
        final Exception  originalException = new Exception("Test Exception");

        final Exception rethrown = assertThrows(Exception.class, () -> {
            throw closer.rethrow(originalException);
        });

        assertSame(originalException, rethrown, "Rethrown exception should be the same instance");
        closer.close();
    }

    @Test
    public void test_rethrow_RuntimeException_preserves_type() throws Exception {
        final AutoCloser       closer            = new AutoCloser();
        final RuntimeException originalException = new RuntimeException("Test RuntimeException");

        final RuntimeException rethrown = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(originalException);
        });

        assertSame(originalException, rethrown, "Rethrown RuntimeException should be the same instance");
        closer.close();
    }

    @Test
    public void test_rethrow_Error_preserves_type() throws Exception {
        final AutoCloser closer        = new AutoCloser();
        final Error      originalError = new Error("Test Error");

        final Error rethrown = assertThrows(Error.class, () -> {
            throw closer.rethrow(originalError);
        });

        assertSame(originalError, rethrown, "Rethrown Error should be the same instance");
        closer.close();
    }

    @Test
    public void test_rethrow_Throwable_wraps_in_RuntimeException() throws Exception {
        final AutoCloser closer = new AutoCloser();

        // Create a custom Throwable that's not Exception, RuntimeException, or Error
        final Throwable originalThrowable = new Throwable("Test Throwable") {
            private static final long serialVersionUID = 1L;
        };

        final RuntimeException rethrown = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(originalThrowable);
        });

        assertSame(originalThrowable, rethrown.getCause(), "Original throwable should be the cause");
        closer.close();
    }

    @Test
    public void test_rethrow_with_close_preserves_original_exception() {
        final AutoCloser       closer            = new AutoCloser();
        final MockCloseable    resource          = MockCloseable.onClose(() -> {
                                                     throw new IOException("Exception during close");
                                                 });
        final RuntimeException originalException = new RuntimeException("Original exception");

        closer.register(resource);

        final RuntimeException rethrown = assertThrows(RuntimeException.class, () -> {
            try {
                throw closer.rethrow(originalException);
            } finally {
                closer.close();
            }
        });

        assertSame(originalException, rethrown, "Original exception should be preserved");

        final Throwable[] suppressed = rethrown.getSuppressed();
        assertEquals(1, suppressed.length, "Should have 1 suppressed exception");
        assertEquals("Exception during close", suppressed[0].getMessage());
        assertTrue(resource.isClosed(), "Resource should still be closed");
    }

    @Test
    public void test_multiple_close_calls_safe() throws Exception {
        final AutoCloser    closer   = new AutoCloser();
        final MockCloseable resource = new MockCloseable();

        closer.register(resource);

        // First close should work
        closer.close();
        assertTrue(resource.isClosed(), "Resource should be closed after first close");

        // Second close should not throw or cause issues
        closer.close();
        assertTrue(resource.isClosed(), "Resource should remain closed after second close");
    }

    @Test
    public void test_register_after_close_throws_exception() throws Exception {
        final AutoCloser    closer    = new AutoCloser();
        final MockCloseable resource1 = new MockCloseable();
        final MockCloseable resource2 = new MockCloseable();

        closer.register(resource1);
        closer.close();
        assertTrue(resource1.isClosed(), "First resource should be closed");

        // Register another resource after close should throw exception
        final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            closer.register(resource2);
        });

        assertEquals("already closed", exception.getMessage());
    }

    @Test
    public void test_register_after_try_with_resources_throws_exception() throws Exception {
        AutoCloser          closer    = new AutoCloser();
        final MockCloseable resource1 = new MockCloseable();

        try (final AutoCloser closer2 = closer) {
            closer2.register(resource1);
        }

        assertTrue(resource1.isClosed(), "Resource should be closed after try-with-resources");

        // Attempting to register after try-with-resources should throw
        final MockCloseable         resource2 = new MockCloseable();
        final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                                                  closer.register(resource2);
                                              });

        assertEquals("already closed", exception.getMessage());
    }

}