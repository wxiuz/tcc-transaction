package org.mengyun.tcctransaction;

import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionContextEditor;
import org.mengyun.tcctransaction.support.FactoryBuilder;
import org.mengyun.tcctransaction.utils.StringUtils;

import java.lang.reflect.Method;

/**
 * Created by changmingxie on 10/30/15.
 */
public final class Terminator {

    public Terminator() {

    }

    /**
     * confirm和cancel方法的调用
     *
     * @param transactionContext
     * @param invocationContext
     * @param transactionContextEditorClass
     * @return
     */
    public static Object invoke(TransactionContext transactionContext, InvocationContext invocationContext, Class<? extends TransactionContextEditor> transactionContextEditorClass) {
        if (StringUtils.isNotEmpty(invocationContext.getMethodName())) {
            try {
                // 目标对象先从Spring ApplicationContext中获取，如果不存在则通过反射获取目标对象的实例
                Object target = FactoryBuilder.factoryOf(invocationContext.getTargetClass()).getInstance();
                Method method = target.getClass().getMethod(invocationContext.getMethodName(), invocationContext.getParameterTypes());
                // 为待调用的方法TransactionContext参数设置参数值
                FactoryBuilder.factoryOf(transactionContextEditorClass).getInstance().set(transactionContext, target, method, invocationContext.getArgs());
                return method.invoke(target, invocationContext.getArgs());
            } catch (Exception e) {
                throw new SystemException(e);
            }
        }
        return null;
    }
}
