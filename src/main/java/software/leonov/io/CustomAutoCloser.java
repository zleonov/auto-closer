package software.leonov.io;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An {@link AutoCloseable} utility that collects {@code AutoCloseable} resources and closes them all when it is
 * {@link #close closed}. This class is the {@code AutoCloseable} analog to Guava's
 * <a href="https://guava.dev/releases/26.0-jre/api/docs/index.html?com/google/common/io/Closer.html" target=
 * "_blank">Closer</a> class. See Guava's <a href="https://github.com/google/guava/issues/3450" target="_blank">Issue
 * 3450</a>, <a href="https://github.com/google/guava/issues/3068" target="_blank">Issue 3068</a>, and
 * <a href="https://github.com/google/guava/issues/1020" target="_blank">Issue 1020</a> for further discussion.
 * <p>
 * After the {@link #close()} method has been called attempts to {@link #register(AutoCloseable) register} new resources
 * will result in an {@link IllegalStateException}. This class is not thread-safe, attempts to use this class
 * concurrently will result in undefined behavior.
 * <p>
 * {@code AutoCloseable} class has two main use cases:
 * <ul>
 * <li>When the number of {@code AutoCloseable} resources is not known until runtime (e.g. the resources are user
 * supplied).</li>
 * <li>When properly closing all {@code AutoCloseable} resources requires nested
 * <a href="http://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html" target=
 * "_blank">try-with-resources</a> blocks which make the code too cumbersome.</li>
 * </ul>
 * <p>
 * This class is intended to be used in the following pattern:
 * <p>
 * Standard Java 6 try-catch-finally style:
 * 
 * <pre><code class="line-numbers match-braces language-java">
 * final AutoCloser closer = new AutoCloser();
 * try {
 *     final InputStream in = closer.register(...);
 *     final OutputStream out = closer.register(...);
 *     ...
 * } catch (final Throwable t) { // We catch a Throwable instance to ensure that all possible errors are caught
 *     ...
 *     throw closer.rethrow(t); // Must be last statement in the catch block
 * } finally {
 *     closer.close();
 * }
 * </code></pre>
 * 
 * Java 7+ try-with-resources style:
 * 
 * <pre><code class="line-numbers match-braces language-java">
 * try (final AutoCloser closer = new AutoCloser()) {
 *     final InputStream in = closer.register(...);
 *     final OutputStream out = closer.register(...);
 *     ...
 * }
 * </code></pre>
 *
 * @author Zhenya Leonov
 */
public final class CustomAutoCloser implements AutoCloseable {

    private final Deque<AutoCloseable> stack = new ArrayDeque<>(4);
    private Throwable                  thrown;

    private boolean closed;

    /**
     * Creates a new {@code AutoCloser} instance.
     */
    public CustomAutoCloser() {
    }

    /**
     * Registers the given {@code AutoCloseable} resource to be closed when this {@code AutoCloser} is {@link #close
     * closed}.
     *
     * @param <C>      the type of {@code AutoCloseable}
     * @param resource the given {@code AutoCloseable} resource
     * @return the given {@code AutoCloseable} resource
     * @throws IllegalStateException if this {@code AutoCloser} has already been closed
     */
    public <C extends AutoCloseable> C register(final C resource) {
        if (closed)
            throw new IllegalStateException("already closed");

        if (resource != null)
            stack.push(resource);

        return resource;
    }

    /**
     * Stores the given throwable and rethrows it <i>as is</i>. In the unlikely possibility that it is a direct instance of
     * {@code Throwable}, it will be rethrown wrapped in a {@code RuntimeException}.
     * <p>
     * This method always throws and as such should be called as {@code throw closer.rethrow} to ensure the compiler knows
     * that it will throw.
     *
     * @param th the given throwable
     * @return this method always throws and as such should be called as {@code throw closer.rethrow}
     * @throws Exception        if the given throwable is an {@code Exception}
     * @throws Error            if the given throwable is an {@code Error}
     * @throws RuntimeException if the given throwable is a {@code RuntimeException} or a direct instance of
     *                          {@code Throwable}
     */
    public RuntimeException rethrow(final Throwable th) throws Exception {
        throw rethrow(th, Exception.class);
    }

    /**
     * Stores the given throwable and rethrows it <i>as is</i> if it is an unchecked or matches the declared throwable type.
     * Otherwise, it will be rethrown wrapped in a {@code RuntimeException}.
     * <p>
     * This method always throws and as such should be called as {@code throw closer.rethrow} to ensure the compiler knows
     * that it will throw.
     *
     * @param <X>          the declared throwable type
     * @param th           the given throwable
     * @param declaredType the class of the declared throwable
     * @return this method always throws and as such should be called as {@code throw closer.rethrow}
     * @throws X                when the given throwable matches the declared type
     * @throws Error            if the given throwable is an {@code Error}
     * @throws RuntimeException if the given throwable is a {@code RuntimeException} or does not match the declared type
     */
    public <X extends Throwable> RuntimeException rethrow(final Throwable th, final Class<X> declaredType) throws X {
        throw rethrow(th, declaredType, null);
    }

    /**
     * Stores the given throwable and rethrows it <i>as is</i> if it is an unchecked or matches one of the declared
     * throwable types. Otherwise, it will be rethrown wrapped in a {@code RuntimeException}.
     * <p>
     * This method always throws and as such should be called as {@code throw closer.rethrow} to ensure the compiler knows
     * that it will throw.
     *
     * @param <X1>          the first declared throwable type
     * @param <X2>          the second declared throwable type
     * @param th            the given throwable
     * @param declaredType1 the class of the first declared throwable
     * @param declaredType2 the class of the second declared throwable
     * @return this method always throws and as such should be called as {@code throw closer.rethrow}
     * @throws X1               when the given throwable matches the declared type {@code X1}
     * @throws X2               when the given throwable matches the declared type {@code X2}
     * @throws Error            if the given throwable is an {@code Error}
     * @throws RuntimeException if the given throwable is a {@code RuntimeException} or does not match any of the declared
     *                          types
     */
    public <X1 extends Throwable, X2 extends Throwable> RuntimeException rethrow(final Throwable th, final Class<X1> declaredType1, final Class<X2> declaredType2) throws X1, X2 {
        requireNonNull(th, "th == null");
        thrown = th;
        throw as(th, declaredType1, declaredType2);
    }

    /**
     * Closes all registered {@code AutoCloseable} resources in reverse (LIFO) order.
     * <p>
     * If an exception occurs while closing a resource it will be rethrown <i>as is</i>.
     * <p>
     * If multiple resources throw exceptions during closing, the first exception encountered becomes the primary exception,
     * and subsequent exceptions are added as suppressed exceptions.
     * <p>
     * If an exception was {@link #rethrow(Throwable) previously caught}, that exception takes precedence and becomes the
     * primary exception. Any subsequent exceptions which occur during closing are added as suppressed.
     *
     * @throws Exception if the encountered exception is an {@code Exception}
     * @throws Error     if the encountered exception is an {@code Error}
     */
    @Override
    public void close() throws Exception {
        close(Exception.class);
    }

    /**
     * Closes all registered {@code AutoCloseable} resources in reverse (LIFO) order.
     * <p>
     * If an exception occurs while closing a resource it will be rethrown <i>as is</i> if it is an unchecked or matches the
     * declared exception type. Otherwise, it will be rethrown wrapped in a {@code RuntimeException}.
     * <p>
     * If multiple resources throw exceptions during closing, the first exception encountered becomes the primary exception,
     * and subsequent exceptions are added as suppressed exceptions.
     * <p>
     * If an exception was {@link #rethrow(Throwable) previously caught}, that exception takes precedence and becomes the
     * primary exception. Any subsequent exceptions which occur during closing are added as suppressed.
     *
     * @param <X>          the declared exception type
     * @param declaredType the class of the declared exception
     * @throws X                if the encountered exception matches the declared exception type
     * @throws Error            if the encountered exception is an {@code Error}
     * @throws RuntimeException if the encountered exception is a {@code RuntimeException} or does not match the declared
     *                          exception type
     */
    public <X extends Exception> void close(final Class<X> declaredType) throws X {
        if (closed)
            return;

        closed = true;
        Throwable th = thrown;

        while (!stack.isEmpty())
            try {
                stack.pop().close();
            } catch (final Throwable e) {
                if (th == null)
                    th = e;
                else
                    th.addSuppressed(e);
            }

        if (thrown == null && th != null)
            throw as(th, declaredType);
    }

    private static <X extends Throwable> RuntimeException as(final Throwable th, final Class<X> type) throws X {
        throw as(th, type, null);
    }

    private static <X1 extends Throwable, X2 extends Throwable> RuntimeException as(final Throwable th, final Class<X1> first, final Class<X2> second) throws X1, X2 {
        if (first != null && first.isInstance(th))
            throw first.cast(th);

        if (second != null && second.isInstance(th))
            throw second.cast(th);

        if (th instanceof RuntimeException)
            throw (RuntimeException) th;

        if (th instanceof Error)
            throw (Error) th;

        throw new RuntimeException(th.getMessage(), th);
    }

}
