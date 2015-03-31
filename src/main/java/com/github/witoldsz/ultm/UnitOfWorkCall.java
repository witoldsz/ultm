package com.github.witoldsz.ultm;

/**
 *
 * @author witoldsz
 */
@FunctionalInterface
public interface UnitOfWorkCall<T> {

    T call() throws Exception;
}
