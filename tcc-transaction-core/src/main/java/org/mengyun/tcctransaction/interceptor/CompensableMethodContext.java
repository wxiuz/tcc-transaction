package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mengyun.tcctransaction.api.Compensable;
import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.UniqueIdentity;
import org.mengyun.tcctransaction.common.MethodRole;
import org.mengyun.tcctransaction.support.FactoryBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * {@link org.mengyun.tcctransaction.api.Compensable} 标注的方法信息
 * <p>
 * Created by changming.xie on 04/04/19.
 */
public class CompensableMethodContext {
    /**
     * JoinPoint
     */
    ProceedingJoinPoint pjp = null;

    /**
     * 方法
     */
    Method method = null;

    /**
     * Compensable信息
     */
    Compensable compensable = null;

    /**
     * 事务传播特性
     */
    Propagation propagation = null;

    TransactionContext transactionContext = null;

    public CompensableMethodContext(ProceedingJoinPoint pjp) {
        this.pjp = pjp;
        this.method = getCompensableMethod();
        this.compensable = method.getAnnotation(Compensable.class);
        this.propagation = compensable.propagation();
        // 从方法调用的参数中获取TransactionContext参数的值
        this.transactionContext = FactoryBuilder.factoryOf(compensable.transactionContextEditor()).getInstance().get(pjp.getTarget(), method, pjp.getArgs());

    }

    public Compensable getAnnotation() {
        return compensable;
    }

    public Propagation getPropagation() {
        return propagation;
    }

    public TransactionContext getTransactionContext() {
        return transactionContext;
    }

    public Method getMethod() {
        return method;
    }

    public Object getUniqueIdentity() {
        Annotation[][] annotations = this.getMethod().getParameterAnnotations();

        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation.annotationType().equals(UniqueIdentity.class)) {

                    Object[] params = pjp.getArgs();
                    Object unqiueIdentity = params[i];

                    return unqiueIdentity;
                }
            }
        }

        return null;
    }


    private Method getCompensableMethod() {
        Method method = ((MethodSignature) (pjp.getSignature())).getMethod();

        if (method.getAnnotation(Compensable.class) == null) {
            try {
                method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
        return method;
    }

    /**
     * 判断当前被@Compensable 标注的方法类型<br/>
     * (1)ROOT： 说明当前方法为TCC事务的入口，需要开启一个新的TCC事务
     * (2)PROVIDER： 说明当前方法作为跨应用服务提供者参与到TCC事务中，而不是TCC事务入口
     * (3)NORMAL：当前方法与被@Compensable标注的主动调用方在同一个应用中
     *
     * @param isTransactionActive
     * @return
     */
    public MethodRole getMethodRole(boolean isTransactionActive) {
        if ((propagation.equals(Propagation.REQUIRED) && !isTransactionActive && transactionContext == null) ||
                propagation.equals(Propagation.REQUIRES_NEW)) {
            // 如果当前方法需要事务，并且当前还不存在事务，同时transactionContext参数为null，则说明是TCC事务入口
            // 如果当前方法必须要一个新事务，则肯定为一个ROOT事务
            return MethodRole.ROOT;
        } else if ((propagation.equals(Propagation.REQUIRED) || propagation.equals(Propagation.MANDATORY)) && !isTransactionActive && transactionContext != null) {
            // 在TCC事务中，如果当前方法需要事务，并且当前不存在事务，同时transactionContext不为空，则说明当前方法作为服务提供者参与到TCC事务中，而不是一个TCC事务的入口
            return MethodRole.PROVIDER;
        } else {
            return MethodRole.NORMAL;
        }
    }

    public Object proceed() throws Throwable {
        return this.pjp.proceed();
    }
}