package com.github.witoldsz.ultm;

import java.sql.Connection;
import java.util.function.Consumer;
import com.github.witoldsz.ultm.internal.ManagedDataSource;
import com.github.witoldsz.ultm.internal.ThreadLocalTxManager;
import javax.sql.DataSource;

/**
 *
 * @author witoldsz
 */
public class ULTM {

    private final Consumer<Connection> noopTuner = c -> {};
    private final DataSource managedDataSource;
    private final ThreadLocalTxManager threadLocalTxManager;

    public ULTM(DataSource rawDataSource) {
        threadLocalTxManager = new ThreadLocalTxManager(rawDataSource, noopTuner);
        managedDataSource = new ManagedDataSource(rawDataSource, threadLocalTxManager);
    }

    public ULTM(DataSource rawDataSource, Consumer<Connection> connectionTuner) {
        threadLocalTxManager = new ThreadLocalTxManager(rawDataSource, connectionTuner);
        managedDataSource = new ManagedDataSource(rawDataSource, threadLocalTxManager);
    }

    public DataSource getManagedDataSource() {
        return managedDataSource;
    }

    public TxManager getTxManager() {
        return threadLocalTxManager;
    }
}
