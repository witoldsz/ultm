package com.github.witoldsz.ultm.test;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;

/**
 *
 * @author witoldsz
 */
public class H2DemoDatabase {

    private JdbcDataSource h2DataSource;

    public void setup() throws SQLException {
        h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:db1;DB_CLOSE_DELAY=-1");

        try (Connection conn = h2DataSource.getConnection()) {
            conn.createStatement().execute("create table PERSONS (ID int, NAME varchar);");
        }
    }

    public void tearDown() throws SQLException {
        try (Connection conn = h2DataSource.getConnection()) {
            conn.createStatement().execute("SHUTDOWN");
        }
    }

    public DataSource getDataSource() {
        return h2DataSource;
    }

}
