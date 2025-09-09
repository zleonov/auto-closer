package software.leonov.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public final class CustomAutoCloserTest {

    @Test
    public void test_register_returns_same_resource() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final MockCloseable resource = new MockCloseable();

        final MockCloseable result = closer.register(resource);

        assertSame(resource, result, "register() should return the same resource instance");
        closer.close();
    }

    @Test
    public void test_register_null_returns_null() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();

        final MockCloseable result = closer.register(null);

        assertNull(result, "register(null) should return null");
        closer.close();
    }

    @Test
    public void test_register_null_does_not_affect_closing() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
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
        final CustomAutoCloser closer = new CustomAutoCloser();

        // Should not throw any exception
        closer.close();
    }

    @Test
    public void test_close_single_resource() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final MockCloseable resource = new MockCloseable();

        closer.register(resource);
        closer.close();

        assertTrue(resource.isClosed(), "Resource should be closed");
    }

    @Test
    public void test_close_multiple_resources_in_LIFO_order() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
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
        final CustomAutoCloser closer = new CustomAutoCloser();
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
        final CustomAutoCloser closer = new CustomAutoCloser();
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

        try (final CustomAutoCloser closer = new CustomAutoCloser()) {
            closer.register(resource1);
            closer.register(resource2);
        }

        assertTrue(resource1.isClosed(), "Resource1 should be closed by try-with-resources");
        assertTrue(resource2.isClosed(), "Resource2 should be closed by try-with-resources");
    }

    @Test
    public void test_rethrow_null_throws_npe() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();

        final NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            throw closer.rethrow(null);
        });

        assertEquals("th == null", exception.getMessage());
        closer.close();
    }

    @Test
    public void test_rethrow_Exception_preserves_type() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final Exception  originalException = new Exception("Test Exception");

        final Exception rethrown = assertThrows(Exception.class, () -> {
            throw closer.rethrow(originalException);
        });

        assertSame(originalException, rethrown, "Rethrown exception should be the same instance");
        closer.close();
    }

    @Test
    public void test_rethrow_RuntimeException_preserves_type() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final RuntimeException originalException = new RuntimeException("Test RuntimeException");

        final RuntimeException rethrown = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(originalException);
        });

        assertSame(originalException, rethrown, "Rethrown RuntimeException should be the same instance");
        closer.close();
    }

    @Test
    public void test_rethrow_Error_preserves_type() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final Error      originalError = new Error("Test Error");

        final Error rethrown = assertThrows(Error.class, () -> {
            throw closer.rethrow(originalError);
        });

        assertSame(originalError, rethrown, "Rethrown Error should be the same instance");
        closer.close();
    }

    @Test
    public void test_rethrow_Throwable_wraps_in_RuntimeException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();

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
        final CustomAutoCloser closer = new CustomAutoCloser();
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
        final CustomAutoCloser closer = new CustomAutoCloser();
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
        final CustomAutoCloser closer = new CustomAutoCloser();
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
        CustomAutoCloser closer = new CustomAutoCloser();
        final MockCloseable resource1 = new MockCloseable();

        try (final CustomAutoCloser closer2 = closer) {
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

    // === Tests for Type-Safe close(Class<X> declaredType) method ===

    @Test
    public void test_typed_close_with_IOException_throws_IOException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final MockCloseable resource = MockCloseable.onClose(() -> {
            throw new IOException("Test IOException");
        });

        closer.register(resource);

        final IOException exception = assertThrows(IOException.class, () -> {
            closer.close(IOException.class);
        });

        assertEquals("Test IOException", exception.getMessage());
        assertTrue(resource.isClosed(), "Resource should be closed despite exception");
    }

    @Test
    public void test_typed_close_with_SQLException_throws_SQLException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final MockCloseable resource = MockCloseable.onClose(() -> {
            throw new SQLException("Test SQLException");
        });

        closer.register(resource);

        final SQLException exception = assertThrows(SQLException.class, () -> {
            closer.close(SQLException.class);
        });

        assertEquals("Test SQLException", exception.getMessage());
        assertTrue(resource.isClosed(), "Resource should be closed despite exception");
    }

    @Test
    public void test_typed_close_wraps_non_matching_exception() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final MockCloseable resource = MockCloseable.onClose(() -> {
            throw new SQLException("Test SQLException"); // SQLException thrown but IOException expected
        });

        closer.register(resource);

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            closer.close(IOException.class);
        });

        // SQLException should be wrapped as cause
        assertTrue(exception.getCause() instanceof SQLException);
        assertEquals("Test SQLException", exception.getCause().getMessage());
        assertTrue(resource.isClosed(), "Resource should be closed despite exception");
    }

    @Test
    public void test_typed_close_preserves_RuntimeException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final RuntimeException originalException = new IllegalArgumentException("Test RuntimeException");
        final MockCloseable resource = MockCloseable.onClose(() -> {
            throw originalException;
        });

        closer.register(resource);

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            closer.close(IOException.class);
        });

        // RuntimeException should be preserved as-is
        assertSame(originalException, exception);
        assertTrue(resource.isClosed(), "Resource should be closed despite exception");
    }

    @Test
    public void test_typed_close_preserves_Error() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final Error originalError = new OutOfMemoryError("Test Error");
        final MockCloseable resource = MockCloseable.onClose(() -> {
            throw originalError;
        });

        closer.register(resource);

        final Error exception = assertThrows(Error.class, () -> {
            closer.close(IOException.class);
        });

        // Error should be preserved as-is
        assertSame(originalError, exception);
        assertTrue(resource.isClosed(), "Resource should be closed despite exception");
    }

    @Test
    public void test_typed_close_without_exception() throws IOException {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final MockCloseable resource = new MockCloseable();

        closer.register(resource);

        // Should not throw any exception
        closer.close(IOException.class);

        assertTrue(resource.isClosed(), "Resource should be closed");
    }

    // === Tests for Type-Safe rethrow(Throwable, Class<X> declaredType) method ===

    @Test
    public void test_single_typed_rethrow_with_matching_IOException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final IOException originalException = new IOException("Test IOException");

        final IOException exception = assertThrows(IOException.class, () -> {
            throw closer.rethrow(originalException, IOException.class);
        });

        assertSame(originalException, exception, "Should preserve original IOException");
        closer.close();
    }

    @Test
    public void test_single_typed_rethrow_with_matching_SQLException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final SQLException originalException = new SQLException("Test SQLException");

        final SQLException exception = assertThrows(SQLException.class, () -> {
            throw closer.rethrow(originalException, SQLException.class);
        });

        assertSame(originalException, exception, "Should preserve original SQLException");
        closer.close();
    }

    @Test
    public void test_single_typed_rethrow_wraps_non_matching_exception() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final SQLException originalException = new SQLException("Test SQLException");

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(originalException, IOException.class); // SQLException not in allowed type
        });

        assertTrue(exception.getCause() instanceof SQLException);
        assertEquals("Test SQLException", exception.getCause().getMessage());
        closer.close();
    }

    @Test
    public void test_single_typed_rethrow_preserves_RuntimeException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final RuntimeException originalException = new IllegalStateException("Test RuntimeException");

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(originalException, IOException.class); // RuntimeException should be preserved
        });

        assertSame(originalException, exception, "RuntimeException should be preserved");
        closer.close();
    }

    @Test
    public void test_single_typed_rethrow_preserves_Error() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final Error originalError = new AssertionError("Test Error");

        final Error exception = assertThrows(Error.class, () -> {
            throw closer.rethrow(originalError, IOException.class); // Error should be preserved
        });

        assertSame(originalError, exception, "Error should be preserved");
        closer.close();
    }

    @Test
    public void test_single_typed_rethrow_with_custom_Throwable() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final Throwable customThrowable = new Throwable("Custom Throwable") {
            private static final long serialVersionUID = 1L;
        };

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(customThrowable, IOException.class);
        });

        assertSame(customThrowable, exception.getCause(), "Custom Throwable should be wrapped as cause");
        closer.close();
    }

    @Test
    public void test_single_typed_rethrow_null_throws_npe() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();

        final NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            throw closer.rethrow(null, IOException.class);
        });

        assertEquals("th == null", exception.getMessage());
        closer.close();
    }

    // === Tests for Type-Safe rethrow(Throwable, Class<X1>, Class<X2>) method ===

    @Test
    public void test_dual_typed_rethrow_with_first_matching_type() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final IOException originalException = new IOException("Test IOException");

        final IOException exception = assertThrows(IOException.class, () -> {
            throw closer.rethrow(originalException, IOException.class, SQLException.class);
        });

        assertSame(originalException, exception, "Should preserve original IOException");
        closer.close();
    }

    @Test
    public void test_dual_typed_rethrow_with_second_matching_type() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final SQLException originalException = new SQLException("Test SQLException");

        final SQLException exception = assertThrows(SQLException.class, () -> {
            throw closer.rethrow(originalException, IOException.class, SQLException.class);
        });

        assertSame(originalException, exception, "Should preserve original SQLException");
        closer.close();
    }

    @Test
    public void test_dual_typed_rethrow_wraps_non_matching_exception() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final InterruptedException originalException = new InterruptedException("Test InterruptedException");

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(originalException, IOException.class, SQLException.class); // InterruptedException not allowed
        });

        assertTrue(exception.getCause() instanceof InterruptedException);
        assertEquals("Test InterruptedException", exception.getCause().getMessage());
        closer.close();
    }

    @Test
    public void test_dual_typed_rethrow_preserves_RuntimeException() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final RuntimeException originalException = new IllegalArgumentException("Test RuntimeException");

        final RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            throw closer.rethrow(originalException, IOException.class, SQLException.class);
        });

        assertSame(originalException, exception, "RuntimeException should be preserved");
        closer.close();
    }

    @Test
    public void test_dual_typed_rethrow_preserves_Error() throws Exception {
        final CustomAutoCloser closer = new CustomAutoCloser();
        final Error originalError = new OutOfMemoryError("Test Error");

        final Error exception = assertThrows(Error.class, () -> {
            throw closer.rethrow(originalError, IOException.class, SQLException.class);
        });

        assertSame(originalError, exception, "Error should be preserved");
        closer.close();
    }

    // === Integration Tests ===

//    @Test
//    public void test_typed_close_with_single_rethrown_exception_preserves_original() throws Exception {
//        final CustomAutoCloser closer = new CustomAutoCloser();
//        final MockCloseable resource = MockCloseable.onClose(() -> {
//            throw new SQLException("Close exception");
//        });
//        final IOException originalException = new IOException("Original exception");
//
//        closer.register(resource);
//
//        final IOException exception = assertThrows(IOException.class, () -> {
//            try {
//                throw closer.rethrow(originalException, IOException.class);
//            } finally {
//                closer.close(IOException.class);
//            }
//        });
//
//        // Original exception should be preserved
//        assertSame(originalException, exception);
//
//        // Close exception should be suppressed
//        final Throwable[] suppressed = exception.getSuppressed();
//        assertEquals(1, suppressed.length, "Should have 1 suppressed exception");
//        assertEquals("Close exception", suppressed[0].getMessage());
//        assertTrue(resource.isClosed(), "Resource should be closed");
//    }

//    @Test
//    public void test_typed_close_with_dual_rethrown_exception_preserves_original() throws Exception {
//        final CustomAutoCloser closer = new CustomAutoCloser();
//        final MockCloseable resource = MockCloseable.onClose(() -> {
//            throw new RuntimeException("Close exception");
//        });
//        final SQLException originalException = new SQLException("Original exception");
//
//        closer.register(resource);
//
//        final SQLException exception = assertThrows(SQLException.class, () -> {
//            try {
//                throw closer.rethrow(originalException, IOException.class, SQLException.class);
//            } finally {
//                closer.close(IOException.class);
//            }
//        });
//
//        // Original exception should be preserved
//        assertSame(originalException, exception);
//
//        // Close exception should be suppressed  
//        final Throwable[] suppressed = exception.getSuppressed();
//        assertEquals(1, suppressed.length, "Should have 1 suppressed exception");
//        assertEquals("Close exception", suppressed[0].getMessage());
//        assertTrue(resource.isClosed(), "Resource should be closed");
//    }

//    @Test
//    public void test_method_signature_type_safety() throws IOException, SQLException {
//        // This test demonstrates that the method signatures are properly type-safe
//        final CustomAutoCloser closer = new CustomAutoCloser();
//        
//        // Single type - only IOException can be thrown
//        try {
//            closer.register(new MockCloseable());
//        } catch (Exception e) {
//            throw closer.rethrow(e, IOException.class); // Compiler knows this throws IOException
//        } finally {
//            closer.close(IOException.class); // Compiler knows this throws IOException
//        }
//
//        // Dual types - IOException or SQLException can be thrown
//        final CustomAutoCloser closer2 = new CustomAutoCloser();
//        try {
//            closer2.register(new MockCloseable());
//        } catch (Exception e) {
//            throw closer2.rethrow(e, IOException.class, SQLException.class); // Throws IOException | SQLException
//        } finally {
//            closer2.close(); // Uses default Exception
//        }
//    }

    // === Helper class for testing ===

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

//    private static final class MockCloseable implements AutoCloseable {
//        private boolean closed = false;
//        private final CheckedRunnable closeAction;
//
//        private MockCloseable() {
//            this.closeAction = null;
//        }
//
//        private MockCloseable(final CheckedRunnable closeAction) {
//            this.closeAction = closeAction;
//        }
//
//        public static MockCloseable onClose(final CheckedRunnable action) {
//            return new MockCloseable(action);
//        }
//
//        public boolean isClosed() {
//            return closed;
//        }
//
//        @Override
//        public void close() throws Exception {
//            closed = true;
//            if (closeAction != null) {
//                closeAction.run();
//            }
//        }
//    }

}