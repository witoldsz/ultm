package com.github.witoldsz.ultm.internal;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *
 * @author witoldsz
 */
@FunctionalInterface
public interface ConnectionProvider {

    Connection get() throws SQLException;
}
