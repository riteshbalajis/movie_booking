package com.movie_booking.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A small, dependency-free JDBC connection pool.
 *
 * <p><b>Why this exists.</b> The original code opened a brand-new TCP connection
 * (plus MySQL handshake and authentication) for every single DAO call. Under the
 * concurrent-booking load this application is built for, that dominates the
 * response time and eventually exhausts {@code max_connections} on the server.
 *
 * <p><b>How it works.</b> A bounded set of physical connections is kept in an
 * idle deque. {@link #borrow()} hands out a dynamic proxy that implements
 * {@link Connection}; every method is forwarded to the real connection except
 * {@code close()}, which returns it to the pool instead of closing the socket.
 * That means every existing try-with-resources block in the DAO layer keeps
 * working unchanged while silently becoming pooled.
 *
 * <p>Connections are reset (rollback of any open transaction, auto-commit back
 * to {@code true}) before they re-enter the pool, so a caller can never inherit
 * another caller's half-finished transaction.
 *
 * <p>This class is thread-safe.
 */
public final class ConnectionPool {

    private final String url;
    private final String user;
    private final String password;
    private final int maxSize;
    private final long borrowTimeoutMillis;
    private final int validationTimeoutSeconds;

    private final Deque<Connection> idle = new ArrayDeque<Connection>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition connectionReturned = lock.newCondition();

    /** Physical connections currently alive (idle + checked out). */
    private final AtomicInteger liveConnections = new AtomicInteger(0);

    private volatile boolean shutdown;

    public ConnectionPool(String url, String user, String password, int maxSize,
            long borrowTimeoutMillis, int validationTimeoutSeconds) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.maxSize = maxSize;
        this.borrowTimeoutMillis = borrowTimeoutMillis;
        this.validationTimeoutSeconds = validationTimeoutSeconds;
    }

    /**
     * Checks out a connection, blocking up to the configured timeout if the pool
     * is saturated.
     *
     * @return a proxied connection whose {@code close()} returns it to the pool
     */
    public Connection borrow() throws SQLException {
        if (shutdown) {
            throw new SQLException("Connection pool has been shut down.");
        }

        long deadline = System.currentTimeMillis() + borrowTimeoutMillis;

        while (true) {
            Connection physical = takeIdleOrCreate();
            if (physical != null) {
                return wrap(physical);
            }

            // Pool is at capacity and nothing is idle: wait for a return.
            lock.lock();
            try {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new SQLException("Timed out after " + borrowTimeoutMillis
                            + " ms waiting for a free connection (pool size " + maxSize + ").");
                }
                connectionReturned.await(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while waiting for a connection.", ex);
            } finally {
                lock.unlock();
            }
        }
    }

    /** @return an idle or freshly created connection, or {@code null} if at capacity. */
    private Connection takeIdleOrCreate() throws SQLException {
        while (true) {
            Connection candidate;
            lock.lock();
            try {
                candidate = idle.pollFirst();
            } finally {
                lock.unlock();
            }

            if (candidate == null) {
                break;
            }
            if (isUsable(candidate)) {
                return candidate;
            }
            // Stale (server restarted, idle timeout): drop it and try the next.
            discard(candidate);
        }

        // Nothing idle. Grow the pool if we are still allowed to.
        while (true) {
            int current = liveConnections.get();
            if (current >= maxSize) {
                return null;
            }
            if (liveConnections.compareAndSet(current, current + 1)) {
                try {
                    return openPhysicalConnection();
                } catch (SQLException ex) {
                    liveConnections.decrementAndGet();
                    throw ex;
                }
            }
        }
    }

    private Connection openPhysicalConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setAutoCommit(true);
        return connection;
    }

    private boolean isUsable(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(validationTimeoutSeconds);
        } catch (SQLException ex) {
            return false;
        }
    }

    private void discard(Connection connection) {
        liveConnections.decrementAndGet();
        closeQuietly(connection);
        signalWaiters();
    }

    /**
     * Returns a connection to the idle set after scrubbing any transaction state.
     * A connection that cannot be scrubbed is destroyed rather than reused.
     */
    private void release(Connection physical) {
        if (shutdown) {
            discard(physical);
            return;
        }

        try {
            if (!physical.getAutoCommit()) {
                // The caller leaked an open transaction. Abandon it - committing
                // here would silently persist a half-finished unit of work.
                physical.rollback();
                physical.setAutoCommit(true);
            }
            physical.clearWarnings();
        } catch (SQLException ex) {
            discard(physical);
            return;
        }

        lock.lock();
        try {
            idle.addLast(physical);
            connectionReturned.signal();
        } finally {
            lock.unlock();
        }
    }

    private void signalWaiters() {
        lock.lock();
        try {
            connectionReturned.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private Connection wrap(Connection physical) {
        return (Connection) Proxy.newProxyInstance(
                ConnectionPool.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                new PooledConnectionHandler(physical));
    }

    /** Closes every idle connection. Checked-out ones are destroyed on return. */
    public void shutdown() {
        shutdown = true;
        lock.lock();
        try {
            Connection connection;
            while ((connection = idle.pollFirst()) != null) {
                liveConnections.decrementAndGet();
                closeQuietly(connection);
            }
            connectionReturned.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int liveConnectionCount() {
        return liveConnections.get();
    }

    public int idleConnectionCount() {
        lock.lock();
        try {
            return idle.size();
        } finally {
            lock.unlock();
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing useful to do while discarding a connection.
        }
    }

    /**
     * Intercepts {@code close()} so that try-with-resources returns the
     * connection to the pool instead of tearing down the socket.
     */
    private final class PooledConnectionHandler implements InvocationHandler {

        private final Connection physical;
        private volatile boolean returned;

        private PooledConnectionHandler(Connection physical) {
            this.physical = physical;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            if ("close".equals(name)) {
                // Idempotent: a nested try-with-resources must not double-release.
                if (!returned) {
                    returned = true;
                    release(physical);
                }
                return null;
            }
            if ("isClosed".equals(name)) {
                return returned || physical.isClosed();
            }
            if ("toString".equals(name)) {
                return "PooledConnection[" + physical + "]";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }

            if (returned) {
                throw new SQLException("Connection has already been returned to the pool.");
            }

            try {
                return method.invoke(physical, args);
            } catch (InvocationTargetException ex) {
                // Unwrap so callers see the real SQLException, not a reflection wrapper.
                throw ex.getCause() == null ? ex : ex.getCause();
            }
        }
    }
}
