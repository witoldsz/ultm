package ultm.core;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static java.util.concurrent.TimeUnit.SECONDS;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import static org.hamcrest.CoreMatchers.containsString;
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
    public void one_thread_scenario_begin_commit() {
        txManager.begin();
        jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Witold").execute();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), notNullValue());
        txManager.commit();

        txManager.begin();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), notNullValue());
        txManager.commit();
    }

    @Test
    public void one_thread_scenario_begin_rollback() {
        txManager.begin();
        jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), notNullValue());
        txManager.rollback();

        txManager.begin();
        assertThat(jooq().selectFrom(PERSONS).fetchOne(), nullValue());
        txManager.commit();
    }

    @Test
    public void one_thread_scenario_unit_of_work() {
        txManager.tx(() ->
            jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute()
        );
        txManager.tx(() -> assertThat(jooq().selectFrom(PERSONS).fetchOne(), notNullValue()));
    }

    @Test
    public void one_thread_scenario_unit_of_work_exception() {
        try {
            txManager.tx(() -> {
                jooq().insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Robert").execute();
                throw new RuntimeException("Something bad happened");
            });
        } catch (UnitOfWorkException ex) {
            assertThat(ex.getCause().getMessage(), is("Something bad happened"));
        }
        txManager.tx(() -> assertThat(jooq().selectFrom(PERSONS).fetchOne(), nullValue()));
    }

    @Test
    public void many_threads_scenario() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        for (int i = 0; i < 1000; ++i) {
            int id = i;
            executor.submit(() -> txManager.tx(() -> {
                    jooq().insertInto(PERSONS).set(ID, id).execute();
                    if (id % 2 == 1) {
                        throw new RuntimeException("Odd have no luck...");
                    }
                }));
        }
        executor.shutdown();
        executor.awaitTermination(3, SECONDS);
        txManager.tx(() -> assertThat(jooq().selectCount().from(PERSONS).fetchOne().value1(), is(500)));
    }
}
