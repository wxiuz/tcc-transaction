package org.mengyun.tcctransaction.api;

/**
 * Created by changming.xie on 1/18/17.
 */
public enum Propagation {
    /**
     * 必须有事务，没有则新建一个新事务
     */
    REQUIRED(0),
    /**
     * 支持事务的方式执行，如果没有，则不创建，继续执行
     */
    SUPPORTS(1),
    /**
     * 必须有事务，没有则报错
     */
    MANDATORY(2),
    /**
     * 强制新开一个新事务
     */
    REQUIRES_NEW(3);

    private final int value;

    Propagation(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }
}