package org.mengyun.tcctransaction.api;

import java.lang.reflect.Method;

/**
 * 事务上下文{@link TransactionContext}工具类。主要完成从方法中获取TransactionContext参数和为方法设置TransactionContext参数值
 * <p>
 * Created by changming.xie on 1/18/17.
 */
public interface TransactionContextEditor {

    /**
     * 从目标的对应方法参数中获取TransactionContext参数
     *
     * @param target
     * @param method
     * @param args
     * @return
     */
    TransactionContext get(Object target, Method method, Object[] args);

    /**
     * 为目标对应的方法的TransactionContext参数设置值
     *
     * @param transactionContext
     * @param target
     * @param method
     * @param args
     */
    void set(TransactionContext transactionContext, Object target, Method method, Object[] args);
}
