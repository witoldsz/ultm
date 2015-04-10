package com.github.witoldsz.ultm;

/**
 *
 * @author witoldsz
 */
public interface TxManager {

    /**
     * Wraps unit within transaction with result. It DOES NOT wrap checked exceptions, just throws them "as is".
     * Basically, it does this:
     * <pre>
     *  begin();
     *  try {
     *      T result = unit.call();
     *      commit();
     *      return result;
     *  } catch (Exception e) {
     *      rollback();
     *      throw e;
     *  }
     * </pre>
     *
     * @param <T> type of result
     * @param unit unit-of-work
     * @return result of unit-of-work
     * @throws UnitOfWorkException wraps SQLException from one of
     * <ul>
     *   <li>{@link #begin()},
     *   <li>{@link #commit()} or
     *   <li>{@link #rollback()}
     * </ul>
     * @throws Exception it can be any exception propagated from within unit-of-work
     * @see UnitOfWork
     * @see UnitOfWorkCall
     */
    <T> T txUnwrappedResult(UnitOfWorkCall<T> unit) throws Exception;

    /**
     * It does same thing as {@link #txUnwrappedResult(UnitOfWorkCall)} but the checked exceptions get wrapped
     * in {@link UnitOfWorkException}, so there is no need to declare or catch unchecked exceptions.
     * Basically, it does this:
     * <pre>
     *  try {
     *      return txUnwrappedResult(unit);
     *  } catch (RuntimeException ex) {
     *      throw ex;
     *  } catch (Exception ex) {
     *      throw new UnitOfWorkException(ex);
     *  }
     * </pre>
     *
     * @param <T> type of result
     * @param unit unit-of-work
     * @return result of unit-of-work
     * @throws UnitOfWorkException same as in {@link #txUnwrappedResult(UnitOfWorkCall)} and also a wrapped of any
     * checked exception thrown within unit-of-work
     * @see #txUnwrappedResult(UnitOfWorkCall)
     * @see UnitOfWork
     * @see UnitOfWorkCall
     */
    <T> T txResult(UnitOfWorkCall<T> unit);

    /**
     * It does same thing as {@link #txUnwrappedResult(UnitOfWorkCall)} but does not return any result.
     *
     * @param unit unit-of-work
     * @throws UnitOfWorkException same as in the equivalent with result
     * @throws Exception same as in the equivalent with result
     * @see #txUnwrappedResult(UnitOfWorkCall)
     */
    void txUnwrapped(UnitOfWork unit) throws Exception;


    /**
     * It does same thing as {@link #txResult(UnitOfWorkCall)} but does not return any result.
     *
     * @param unit unit-of-work
     * @throws UnitOfWorkException same as in the equivalent with result
     * @see #txUnwrappedResult(UnitOfWorkCall)
     * @see #txResult(UnitOfWorkCall)
     */
    void tx(UnitOfWork unit);

    /**
     * Begins a transaction.
     * @throws IllegalStateException when transaction is already in progress.
     */
    void begin();

    /**
     * Commits a transaction.
     * @throws IllegalStateException when no transaction is in progress.
     */
    void commit();

    /**
     * Transaction rollback.
     * @throws IllegalStateException when no transaction is in progress.
     */
    void rollback();
}
