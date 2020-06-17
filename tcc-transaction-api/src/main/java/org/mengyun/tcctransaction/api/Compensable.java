package org.mengyun.tcctransaction.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * 可补偿事务注解，通过该注解进行方法标注，则将当前标注的方法作为TCC事务的参与者添加到TCC事务中
 * <p>
 * Created by changmingxie on 10/25/15.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Compensable {

    /**
     * 事务传播特性
     *
     * @return
     */
    Propagation propagation() default Propagation.REQUIRED;

    /**
     * 事务的confirm方法
     *
     * @return
     */
    String confirmMethod() default "";

    /**
     * 事务的cancel方法
     *
     * @return
     */
    String cancelMethod() default "";

    /**
     * 当前方法的TransactionContext参数处理工具类
     *
     * @return
     */
    Class<? extends TransactionContextEditor> transactionContextEditor() default DefaultTransactionContextEditor.class;

    /**
     * 延迟cancel处理，当捕获到这些异常时，此时不是马上进行回滚操作，而是等待恢复job重新执行恢复，达到最大次数仍然失败，则再进行回滚
     *
     * @return
     */
    Class<? extends Exception>[] delayCancelExceptions() default {};

    /**
     * 是否异步confirm
     *
     * @return
     */
    boolean asyncConfirm() default false;

    /**
     * 是否异步cancel
     *
     * @return
     */
    boolean asyncCancel() default false;

    class NullableTransactionContextEditor implements TransactionContextEditor {

        @Override
        public TransactionContext get(Object target, Method method, Object[] args) {
            return null;
        }

        @Override
        public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {

        }
    }

    /**
     * 默认TransactionContext参数处理工具类
     */
    class DefaultTransactionContextEditor implements TransactionContextEditor {

        /**
         * 从方法的参数列表中获取TransactionContext参数
         *
         * @param target
         * @param method
         * @param args
         * @return
         */
        @Override
        public TransactionContext get(Object target, Method method, Object[] args) {
            int position = getTransactionContextParamPosition(method.getParameterTypes());

            if (position >= 0) {
                return (TransactionContext) args[position];
            }

            return null;
        }

        /**
         * 为方法的参数中TransactionContext类型参数设置值
         *
         * @param transactionContext
         * @param target
         * @param method
         * @param args
         */
        @Override
        public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {

            int position = getTransactionContextParamPosition(method.getParameterTypes());
            if (position >= 0) {
                args[position] = transactionContext;
            }
        }

        public static int getTransactionContextParamPosition(Class<?>[] parameterTypes) {
            int position = -1;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i].equals(org.mengyun.tcctransaction.api.TransactionContext.class)) {
                    position = i;
                    break;
                }
            }
            return position;
        }

        public static TransactionContext getTransactionContextFromArgs(Object[] args) {

            TransactionContext transactionContext = null;

            for (Object arg : args) {
                if (arg != null && org.mengyun.tcctransaction.api.TransactionContext.class.isAssignableFrom(arg.getClass())) {

                    transactionContext = (org.mengyun.tcctransaction.api.TransactionContext) arg;
                }
            }

            return transactionContext;
        }
    }
}