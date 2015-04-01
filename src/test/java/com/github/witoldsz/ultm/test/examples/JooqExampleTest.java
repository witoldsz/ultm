package com.github.witoldsz.ultm.test.examples;

import com.github.witoldsz.ultm.TxManager;
import com.github.witoldsz.ultm.ULTM;
import com.github.witoldsz.ultm.test.H2DemoDatabase;
import java.sql.SQLException;
import java.util.Random;
import static org.hamcrest.CoreMatchers.is;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import static org.jooq.SQLDialect.H2;
import org.jooq.Table;
import org.jooq.impl.DSL;
import static org.jooq.impl.DSL.count;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 * Example of (superb) jOOQ with ULTM.
 * @author Witold Szczerba
 */
public class JooqExampleTest {

    private final H2DemoDatabase h2DemoDatabase = new H2DemoDatabase();
    private final Table<Record> PERSONS = DSL.tableByName("PERSONS");
    private final Field<Integer> ID = DSL.fieldByName(Integer.class, "PERSONS", "ID");
    private final Field<String> NAME = DSL.fieldByName(String.class, "PERSONS", "NAME");
    private final Random random = new Random();

    private TxManager txManager;
    private DSLContext jooq;

    @Before
    public void setup() throws SQLException {
        h2DemoDatabase.setup();
        ULTM ultm = new ULTM(h2DemoDatabase.getDataSource());
        txManager = ultm.getTxManager();
        jooq = DSL.using(ultm.getManagedDataSource(), H2);
    }

    @After
    public void tearDown() throws SQLException {
        h2DemoDatabase.tearDown();
    }

    @Test
    public void jooq_example_with_begin_commit_rollback() {
        txManager.begin();
        jooq.insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Mr jOOQ").execute();
        assertThat(personsCount(), is(1));
        txManager.commit();

        txManager.begin();
        jooq.delete(PERSONS).execute();
        assertThat(personsCount(), is(0));
        txManager.rollback();

        txManager.begin();
        assertThat(personsCount(), is(1));
        txManager.commit();
    }

    /**
     * This test is using transaction executors declaring no checked exceptions,
     * so there is no need to declare or catch them.
     */
    @Test
    public void jooq_example_with_unit_of_work() {
        txManager.tx(() -> {
            jooq.insertInto(PERSONS).set(ID, random.nextInt()).set(NAME, "Mr jOOQ").execute();
            assertThat(personsCount(), is(1));
        });

        try {
            txManager.tx(() -> {
                jooq.delete(PERSONS).execute();
                assertThat(personsCount(), is(0));
                throw new RuntimeException("I am bad exception");
            });
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), is("I am bad exception"));
        }

        int personsCount = txManager.txResult(this::personsCount);
        assertThat(personsCount, is(1));
    }

    private Integer personsCount() {
        return jooq.selectCount().from(PERSONS).fetchOne(count());
    }

}
