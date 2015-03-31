package com.github.witoldsz.ultm;

/**
 *
 * @author witoldsz
 */
@FunctionalInterface
public interface UnitOfWork {

    void run() throws Exception;
}
