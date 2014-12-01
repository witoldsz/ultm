package com.github.witoldsz.ultm.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;
import javax.sql.DataSource;
import com.github.witoldsz.ultm.TxManager;
import com.github.witoldsz.ultm.UnitOfWork;
import com.github.witoldsz.ultm.UnitOfWorkChecked;
import com.github.witoldsz.ultm.UnitOfWorkException;

/**
 *
 * @author witoldsz
 */
public class ThreadLocalTxManager implements TxManager, ConnectionProvider {

    private static final WrappedConnection TRANSACTION_MARKER = new WrappedConnection(null);

    private final ThreadLocal<WrappedConnection> connections = new ThreadLocal<>();
    private final DataSource rawDataSource;
    private final Consumer<Connection> connectionTuner;

    public ThreadLocalTxManager(DataSource rawDataSource, Consumer<Connection> connectionTuner) {
        this.rawDataSource = rawDataSource;
        this.connectionTuner = connectionTuner;
    }

    @Override
    public WrappedConnection get() throws SQLException {
        WrappedConnection c = connections.get();
        if (c == null) {
            throw new IllegalStateException("Transaction is not active.");
        }
        if (c == TRANSACTION_MARKER) {
            Connection rawConnection = rawDataSource.getConnection();
            connections.set(c = new WrappedConnection(rawConnection));
            if (c.getAutoCommit()) c.setAutoCommit(false); // just to make sure
            connectionTuner.accept(c);
        }
        return c;
    }

    @Override
    public void txChecked(UnitOfWorkChecked w) throws Exception {
        begin();
        boolean beforeCommit = true;
        try {
            w.run();
            beforeCommit = false;
            commit();
        } catch (Exception e) {
            if (beforeCommit) rollback();
            throw e;
        }
    }

    @Override
    public void tx(UnitOfWork w) {
        try {
            txChecked(() -> w.run());
        } catch (UnitOfWorkException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnitOfWorkException(ex);
        }
    }

    @Override
    public void begin() {
        throwIfAlreadyAssigned();
        connections.set(TRANSACTION_MARKER);
    }

    @Override
    public void commit() {
        pullDelegatedConnection().ifPresent( delegated -> {
            try {
                delegated.commit();
                delegated.close();
            } catch (SQLException ex) {
                throw new UnitOfWorkException(ex);
            }
        });
    }

    @Override
    public void rollback() {
        pullDelegatedConnection().ifPresent( delegated -> {
            try {
                delegated.rollback();
                delegated.close();
            } catch (SQLException ex) {
                throw new UnitOfWorkException(ex);
            }
        });
    }

    private Optional<Connection> pullDelegatedConnection() {
        WrappedConnection c = connections.get();
        if (c == null) {
            throw new IllegalStateException("Transaction is not active.");
        }
        connections.remove();
        return c == TRANSACTION_MARKER ? Optional.empty() : Optional.of(c.getDelegate());
    }

    private void throwIfAlreadyAssigned() {
        if (connections.get() != null) {
            throw new IllegalStateException("Transaction is in progress already.");
        }
    }

}
