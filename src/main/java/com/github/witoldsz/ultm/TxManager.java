package com.github.witoldsz.ultm;

/**
 *
 * @author witoldsz
 */
public interface TxManager {

    /**
     * Wraps unit within transaction with result. It works like this:
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
     * <ul> <li>{@link #begin()}, <li>{@link #commit()} or <li>{@link #rollback()}
     * @throws Exception it can be any exception propagated from within unit-of-work
     * @see UnitOfWork
     * @see UnitOfWorkCall
     */
    <T> T txResult(UnitOfWorkCall<T> unit) throws Exception;

    /**
     * It does same thing as {@link #txResult(UnitOfWorkCall)} but the checked exceptions get wrapped
     * in {@link UnitOfWorkException}.
     *
     * @param <T> type of result
     * @param unit unit-of-work
     * @return result of unit-of-work
     * @throws UnitOfWorkException same as in {@link #txResult(UnitOfWorkCall)} and also a wrapped of any
     * checked exception thrown within unit-of-work
     * @see #txResult(UnitOfWorkCall)
     * @see UnitOfWork
     * @see UnitOfWorkCall
     */
    <T> T txWrappedResult(UnitOfWorkCall<T> unit);

    /**
     * It does same thing as {@link #txResult(UnitOfWorkCall)} but does not return any result.
     *
     * @param unit unit-of-work
     * @throws UnitOfWorkException
     * @throws Exception
     * @see #txResult(UnitOfWorkCall)
     */
    void tx(UnitOfWork unit) throws Exception;


    /**
     * It does same thing as {@link #txWrappedResult(UnitOfWorkCall)} but does not return any result.
     *
     * @param unit unit-of-work
     * @throws UnitOfWorkException
     * @see #txResult(UnitOfWorkCall)
     * @see #txWrappedResult(UnitOfWorkCall)
     */
    void txWrapped(UnitOfWork unit);

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
