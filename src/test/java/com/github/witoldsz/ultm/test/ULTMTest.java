package com.github.witoldsz.ultm.test;

import com.github.witoldsz.ultm.TxManager;
import com.github.witoldsz.ultm.ULTM;
import com.github.witoldsz.ultm.UnitOfWorkException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.concurrent.TimeUnit.SECONDS;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import static org.jooq.SQLDialect.H2;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author witoldsz
 */
public class ULTMTest {

    private final Table<Record> PERSONS = DSL.tableByName("PERSONS");
    private final Field<Integer> ID = DSL.fieldByName(Integer.class, "PERSONS", "ID");
    private final Field<String> NAME = DSL.fieldByName(String.class, "PERSONS", "NAME");
    private final Random random = new Random();

    private TxManager txManager;
    private JdbcDataSource h2DataSource;
    private DataSource managedDataSource;

    private DSLContext jooq() {
        return DSL.using(managedDataSource, H2);
    }

    @Before
    public void setup() throws SQLException {
        h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:db1;DB_CLOSE_DELAY=10");

        try (Connection conn = h2DataSource.getConnection()) {
            conn.createStatement().execute("create table PERSONS (ID int, NAME varchar);");
        }
        try (Connection conn = h2DataSource.getConnection()) {
            conn.createStatement().executeQuery("select * from PERSONS;");
        }
        ULTM ultm = new ULTM(h2DataSource);
        txManager = ultm.getTxManager();
        managedDataSource = ultm.getManagedDataSource();
    }

    @After
    public void tearDown() throws SQLException {
        try (Connection conn = h2DataSource.getConnection()) {
            conn.createStatement().execute("SHUTDOWN");
        }
        try (Connection conn = h2DataSource.getConnection()) {
            conn.createStatement().executeQuery("SELECT * FROM persons;");
        } catch (SQLException ex) {
            assertThat(ex.getMessage(), containsString("Table \"PERSONS\" not found"));
        }
    }

    @Test
    public void should_begin_and_commit() {
        txManager.begin();
        jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Witold").execute();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), notNullValue());
        txManager.commit();

        txManager.begin();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), notNullValue());
        txManager.commit();
    }

    @Test
    public void should_begin_and_rollback() {
        txManager.begin();
        jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), notNullValue());
        txManager.rollback();

        txManager.begin();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), nullValue());
        txManager.commit();
    }

    @Test
    public void should_wrap_tx_within_UnitOfWork() throws Exception {
        txManager.tx(() ->
            jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute()
        );
        assertThat(txManager.txResult(() -> jooq().selectFrom(PERSONS).fetchOne()), notNullValue());
    }

    @Test
    public void should_rollback_tx_within_UnitOfWork() throws Exception {
        try {
            txManager.tx(() -> {
                jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute();
                throw new RuntimeException("Something happened!");
            });
            fail("This test should not get here.");
        } catch (RuntimeException e) {
            // ignore
        }
        assertThat(txManager.txResult(() -> jooq().selectFrom(PERSONS).fetchOne()), nullValue());
    }

    @Test
    public void should_wrap_tx_within_UnitOfWorkCall_with_result() throws Exception {
        Integer result = txManager.txResult(() ->
            jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute()
        );
        assertThat(txManager.txResult(() -> jooq().selectFrom(PERSONS).fetchOne()), notNullValue());
        assertThat(result, is(1));
    }

    @Test
    public void should_rollback_tx_within_UnitOfWorkCall_with_result() throws Exception {
        try {
            Object ignore = txManager.txResult(() -> {
                jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute();
                throw new RuntimeException("Something happened!");
            });
            fail("This test should not get here.");
        } catch (RuntimeException e) {
            // noop
        }
        assertThat(txManager.txResult(() -> jooq().selectFrom(PERSONS).fetchOne()), nullValue());
    }

    @Test
    public void should_propagate_every_exceptions_as_is_from_UnitOfWork() {
        try {
            txManager.tx(() -> {
                jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute();
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
            txManager.txResult(() -> {
                jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute();
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
            txManager.txWrapped(() -> {
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
            txManager.txWrappedResult(() -> {
                throw new Exception("Something bad happened");
            });
            fail("This test should not get here.");
        } catch (Exception ex) {
            assertThat(ex.getClass(), equalTo(UnitOfWorkException.class));
            assertThat(ex.getCause().getMessage(), is("Something bad happened"));
        }
    }

    @Test
    public void many_threads_torture_scenario() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        for (int i = 0; i < 1000; ++i) {
            int id = i;
            executor.submit(() -> txManager.txWrapped(() -> {
                    jooq().insertInto(PERSONS).set(ID, id).execute();
                    if (id % 2 == 1) {
                        throw new RuntimeException("Odd have no luck...");
                    }
                }));
        }
        executor.shutdown();
        executor.awaitTermination(15, SECONDS); //usually few hundred ms should do

        assertThat(txManager.txResult(() -> jooq().selectCount().from(PERSONS).fetchOne().value1()), is(500));
    }

    @Test
    public void should_allow_noop() throws Exception {
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
    public void should_throw_when_acting_without_transaction() {
        try {
            jooq().selectFrom(PERSONS).fetchOne();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage(), is("Transaction is not active."));
            throw ex;
        }
    }
}
