package ultm.core.spi;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;
import javax.sql.DataSource;
import ultm.core.TxManager;
import ultm.core.UnitOfWork;
import ultm.core.UnitOfWorkChecked;
import ultm.core.UnitOfWorkException;

/**
 *
 * @author witoldsz
 */
public class ThreadLocalTxManager implements TxManager, ConnectionProvider {

    private static final WrappedConnection PHANTOM_CONNECTON = new WrappedConnection(null);

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
        if (c == null || c == PHANTOM_CONNECTON) {
            connections.set(c = new WrappedConnection(rawDataSource.getConnection()));
            if (c.getAutoCommit()) c.setAutoCommit(false);
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
        connections.set(PHANTOM_CONNECTON);
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
        } else {
            connections.remove();
        }
        return c == PHANTOM_CONNECTON ? Optional.empty() : Optional.of(c.getDelegate());
    }

    private void throwIfAlreadyAssigned() {
        if (connections.get() != null) {
            throw new IllegalStateException("Transaction is in progress already.");
        }
    }

}
