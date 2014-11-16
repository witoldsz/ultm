package ultm.core;

/**
 *
 * @author witoldsz
 */
@FunctionalInterface
public interface UnitOfWorkChecked {

    void run() throws Exception;
}
