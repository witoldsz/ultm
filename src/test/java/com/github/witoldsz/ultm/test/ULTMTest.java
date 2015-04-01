package com.github.witoldsz.ultm.test;

import com.github.witoldsz.ultm.TxManager;
import com.github.witoldsz.ultm.ULTM;
import com.github.witoldsz.ultm.UnitOfWorkException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.concurrent.TimeUnit.SECONDS;
import javax.sql.DataSource;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author witoldsz
 */
public class ULTMTest {

    private final H2DemoDatabase h2DemoDatabase = new H2DemoDatabase();
    private TxManager txManager;
    private DataSource managedDataSource;

    @Before
    public void setup() throws SQLException {
        h2DemoDatabase.setup();
        ULTM ultm = new ULTM(h2DemoDatabase.getDataSource());
        txManager = ultm.getTxManager();
        managedDataSource = ultm.getManagedDataSource();
    }

    @After
    public void tearDown() throws SQLException {
        h2DemoDatabase.tearDown();
    }

    private int insertPerson() throws SQLException {
        try (Connection conn = managedDataSource.getConnection()) {
            return conn.createStatement().executeUpdate("insert into PERSONS (ID, NAME) values (1, 'Mr Foo');");
        }
    }

    private Integer personsCount() throws SQLException {
        try (Connection conn = managedDataSource.getConnection()) {
            try (ResultSet r = conn.createStatement().executeQuery("select count(*) from PERSONS;")) {
                r.first();
                return r.getInt(1);
            }
        }
    }

    @Test
    public void should_begin_and_commit() throws SQLException {
        txManager.begin();
        insertPerson();
        assertThat(personsCount(), is(1));
        txManager.commit();

        txManager.begin();
        assertThat(personsCount(), is(1));
        txManager.commit();
    }

    @Test
    public void should_begin_and_rollback() throws SQLException {
        txManager.begin();
        insertPerson();
        assertThat(personsCount(), is(1));
        txManager.rollback();

        txManager.begin();
        assertThat(personsCount(), is(0));
        txManager.commit();
    }

    @Test
    public void should_wrap_tx_within_UnitOfWork() {
        txManager.tx(this::insertPerson);
        assertThat(txManager.txResult(this::personsCount), is(1));
    }

    @Test
    public void should_rollback_tx_within_UnitOfWork() {
        try {
            txManager.tx(() -> {
                insertPerson();
                throw new RuntimeException("Something happened!");
            });
            fail("This test should not get here.");
        } catch (RuntimeException e) {
            // ignore
        }
        assertThat(txManager.txResult(this::personsCount), is(0));
    }

    @Test
    public void should_wrap_tx_within_UnitOfWorkCall_with_result() {
        Integer result = txManager.txResult(this::insertPerson);
        assertThat(txManager.txResult(this::personsCount), is(1));
        assertThat(result, is(1));
    }

    @Test
    public void should_rollback_tx_within_UnitOfWorkCall_with_result() {
        try {
            Object ignore = txManager.txResult(() -> {
                insertPerson();
                throw new RuntimeException("Something happened!");
            });
            fail("This test should not get here.");
        } catch (RuntimeException e) {
            // noop
        }
        assertThat(txManager.txResult(this::personsCount), is(0));
    }

    @Test
    public void should_propagate_every_exceptions_unwrapped_from_UnitOfWork() {
        try {
            txManager.txUnwrapped(() -> {
                throw new Exception("Something bad happened");
            });
            fail("This test should not get here.");
        } catch (Exception ex) {
            assertThat(ex.getClass(), equalTo(Exception.class));
            assertThat(ex.getMessage(), is("Something bad happened"));
        }
    }

    @Test
    public void should_propagate_every_exceptions_as_is_from_UnitOfWorkCall() {
        try {
            txManager.txUnwrappedResult(() -> {
                throw new Exception("Something bad happened");
            });
            fail("This test should not get here.");
        } catch (Exception ex) {
            assertThat(ex.getClass(), equalTo(Exception.class));
            assertThat(ex.getMessage(), is("Something bad happened"));
        }
    }

    @Test
    public void should_wrap_checked_exceptions_from_UnitOfWork() {
        try {
            txManager.tx(() -> {
                throw new Exception("Something bad happened");
            });
            fail("This test should not get here.");
        } catch (Exception ex) {
            assertThat(ex.getClass(), equalTo(UnitOfWorkException.class));
            assertThat(ex.getCause().getMessage(), is("Something bad happened"));
        }
    }

    @Test
    public void should_wrap_checked_exceptions_from_UnitOfWorkCall() {
        try {
            txManager.txResult(() -> {
                throw new Exception("Something bad happened");
            });
            fail("This test should not get here.");
        } catch (Exception ex) {
            assertThat(ex.getClass(), equalTo(UnitOfWorkException.class));
            assertThat(ex.getCause().getMessage(), is("Something bad happened"));
        }
    }

    @Test
    public void many_threads_torture_scenario() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        for (int i = 0; i < 1000; ++i) {
            int id = i;
            executor.submit(() -> txManager.tx(() -> {
                    insertPerson();
                    if (id % 2 == 1) {
                        throw new RuntimeException("Odd have no luck...");
                    }
                }));
        }
        executor.shutdown();
        executor.awaitTermination(15, SECONDS); //usually few hundred ms should do

        assertThat(txManager.txResult(this::personsCount), is(500));
    }

    @Test
    public void should_allow_noop() {
        txManager.begin();
        // noop
        txManager.commit();

        txManager.begin();
        // noop
        txManager.rollback();

        txManager.tx(() -> {/* noop */});
    }

    @Test(expected = IllegalStateException.class)
    public void should_not_allow_begin_within_transaction() {
        try {
            txManager.begin();
            txManager.begin();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage(), is("Transaction is in progress already."));
            throw ex;
        }
    }

    @Test(expected = IllegalStateException.class)
    public void should_not_allow_commit_without_transaction() {
        try {
            txManager.commit();
        } catch (IllegalStateException ex) {
            assertThat("commit", ex.getMessage(), is("Transaction is not active."));
            throw ex;
        }
    }

    @Test(expected = IllegalStateException.class)
    public void should_not_allow_rollback_without_transaction() {
        try {
            txManager.rollback();
        } catch (IllegalStateException ex) {
            assertThat("rollback", ex.getMessage(), is("Transaction is not active."));
            throw ex;
        }
    }

    @Test(expected = IllegalStateException.class)
    public void should_throw_when_acting_without_transaction() throws SQLException {
        try {
            insertPerson();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage(), is("Transaction is not active."));
            throw ex;
        }
    }
}
