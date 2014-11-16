package ultm.core.spi;

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
