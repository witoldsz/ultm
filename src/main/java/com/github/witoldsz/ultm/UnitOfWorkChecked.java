package com.github.witoldsz.ultm;

/**
 *
 * @author witoldsz
 */
@FunctionalInterface
public interface UnitOfWorkChecked {

    void run() throws Exception;
}
