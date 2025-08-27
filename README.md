# AutoCloser

A Java utility for managing multiple AutoCloseable resources with proper exception handling.

## Overview

AutoCloser simplifies resource management in Java by collecting multiple `AutoCloseable` resources and closing them all when the AutoCloser itself is closed. It handles exceptions properly using suppressed exception semantics and closes resources in reverse order (LIFO).

This library is the `AutoCloseable` analog to Guava's `Closer` class and eliminates the need for nested try-with-resources blocks.

## Features

- **LIFO Resource Closing**: Resources are closed in reverse order of registration
- **Exception Handling**: Proper exception suppression when multiple resources throw during close
- **Lifecycle Management**: Prevents registration of new resources after closing
- **Try-with-resources Support**: Works seamlessly with try-with-resources statements
- **Thread-safe**: Safe for single-threaded use (not designed for concurrent access)

## Requirements

- Java 8 or later
- JUnit 5 (for tests)

## Usage

### Basic Usage

```java
try (final AutoCloser closer = new AutoCloser()) {
    final FileInputStream input = closer.register(new FileInputStream("input.txt"));
    final FileOutputStream output = closer.register(new FileOutputStream("output.txt"));
    
    // Use resources...
} // All resources automatically closed in reverse order
```

### Manual Resource Management

```java
final AutoCloser closer = new AutoCloser();
try {
    final InputStream in = closer.register(new FileInputStream("data.txt"));
    final OutputStream out = closer.register(new FileOutputStream("output.txt"));
    
    // Use resources...
} catch (final Throwable t) {
    throw closer.rethrow(t);
} finally {
    closer.close();
}
```

### Exception Handling with Rethrow

```java
final AutoCloser closer = new AutoCloser();
try {
    final Resource resource = closer.register(new SomeResource());
    
    // Work that might throw exceptions...
    if (someCondition) {
        throw new RuntimeException("Something went wrong");
    }
    
} catch (final Exception e) {
    throw closer.rethrow(e); // Preserves original exception, suppresses close exceptions
} finally {
    closer.close();
}
```

### Utility Methods for Quiet Closing

```java
// Close with custom exception handling
AutoCloseable resource = new FileInputStream("file.txt");
AutoCloseables.closeQuietly(resource, throwable -> {
    logger.warn("Failed to close resource", throwable);
});

// Close and add exception to existing throwable
Exception primaryException = new RuntimeException("Main error");
AutoCloseables.closeQuietly(resource, primaryException); // Adds as suppressed exception
```

## API

### AutoCloser

- `<T extends AutoCloseable> T register(T resource)` - Registers a resource for closing
- `RuntimeException rethrow(Throwable t)` - Stores exception and rethrows it
- `void close()` - Closes all registered resources in reverse order

### AutoCloseables

- `closeQuietly(AutoCloseable resource, Consumer<Throwable> exceptionHandler)` - Close with custom exception handling
- `closeQuietly(AutoCloseable resource, Throwable primaryException)` - Close and add exception as suppressed

## Building

```bash
mvn clean compile
mvn test
mvn package
```

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.