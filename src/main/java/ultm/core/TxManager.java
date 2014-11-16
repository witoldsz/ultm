package ultm.core;

/**
 *
 * @author witoldsz
 */
public interface TxManager {

    void txChecked(UnitOfWorkChecked w) throws Exception;
    void tx(UnitOfWork w);
    void begin();
    void commit();
    void rollback();
}
