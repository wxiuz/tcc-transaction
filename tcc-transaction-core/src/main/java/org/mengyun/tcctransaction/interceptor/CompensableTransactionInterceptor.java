package org.mengyun.tcctransaction.interceptor;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mengyun.tcctransaction.NoExistedTransactionException;
import org.mengyun.tcctransaction.SystemException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.utils.ReflectionUtils;
import org.mengyun.tcctransaction.utils.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 可补偿事务拦截器，处理实际的TCC事务，优先级高于{@link ResourceCoordinatorInterceptor}。
 * 该拦截器主要处理被@Compensable标注的方法逻辑，判断当前方法是不是TCC事务的入口和是不是参与TCC
 * 事务的跨服务的服务端入口。根据以上两种情况进行不同的处理。
 * <p>
 * Created by changmingxie on 10/30/15.
 */
public class CompensableTransactionInterceptor {

    static final Logger logger = Logger.getLogger(CompensableTransactionInterceptor.class.getSimpleName());

    private TransactionManager transactionManager;

    private Set<Class<? extends Exception>> delayCancelExceptions = new HashSet<Class<? extends Exception>>();

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions.addAll(delayCancelExceptions);
    }

    /**
     * @param pjp
     * @return
     * @throws Throwable
     * @Compensable 标注的方法拦截
     */
    public Object interceptCompensableMethod(ProceedingJoinPoint pjp) throws Throwable {

        CompensableMethodContext compensableMethodContext = new CompensableMethodContext(pjp);

        // 当前是否有激活的事务
        boolean isTransactionActive = transactionManager.isTransactionActive();

        if (!TransactionUtils.isLegalTransactionContext(isTransactionActive, compensableMethodContext)) {
            throw new SystemException("no active compensable transaction while propagation is mandatory for method " + compensableMethodContext.getMethod().getName());
        }

        // 根据当前的方法角色进行不同的业务逻辑
        switch (compensableMethodContext.getMethodRole(isTransactionActive)) {
            case ROOT:
                // 当前方法是TCC事务的入口
                return rootMethodProceed(compensableMethodContext);
            case PROVIDER:
                // 当前方法作为跨服务TCC事务时服务提供方的入口
                return providerMethodProceed(compensableMethodContext);
            default:
                return pjp.proceed();
        }
    }

    /**
     * TCC事务入口处理：需要开启一个新的事务
     *
     * @param compensableMethodContext
     * @return
     * @throws Throwable
     */
    private Object rootMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {
        Object returnValue = null;
        Transaction transaction = null;
        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();
        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();
        Set<Class<? extends Exception>> allDelayCancelExceptions = new HashSet<Class<? extends Exception>>();
        allDelayCancelExceptions.addAll(this.delayCancelExceptions);
        allDelayCancelExceptions.addAll(Arrays.asList(compensableMethodContext.getAnnotation().delayCancelExceptions()));

        try {
            // 开启新的事务
            transaction = transactionManager.begin(compensableMethodContext.getUniqueIdentity());
            try {
                // 实际的业务方法调用
                returnValue = compensableMethodContext.proceed();
            } catch (Throwable tryingException) {
                // 支持指定异常不会滚事务，等待job进行重试恢复，如SocketTimeOutException等
                if (!isDelayCancelException(tryingException, allDelayCancelExceptions)) {
                    logger.warn(String.format("compensable transaction trying failed. transaction content:%s", JSON.toJSONString(transaction)), tryingException);
                    // 回滚事务
                    transactionManager.rollback(asyncCancel);
                }
                throw tryingException;
            }
            // 提交事务
            transactionManager.commit(asyncConfirm);
        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }
        return returnValue;
    }

    /**
     * 服务提供者作为TCC事务的一部分，入口方法的逻辑
     *
     * @param compensableMethodContext
     * @return
     * @throws Throwable
     */
    private Object providerMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {
        Transaction transaction = null;
        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();
        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        try {
            switch (TransactionStatus.valueOf(compensableMethodContext.getTransactionContext().getStatus())) {
                case TRYING:
                    // 如果是在Trying阶段，此时需要在服务提供者方开启一个新的分支事务
                    transaction = transactionManager.propagationNewBegin(compensableMethodContext.getTransactionContext());
                    return compensableMethodContext.proceed();
                case CONFIRMING:
                    try {
                        // 如果是事务提交，此时需要检查事务是否存在
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        transactionManager.commit(asyncConfirm);
                    } catch (NoExistedTransactionException excepton) {
                        //the transaction has been commit,ignore it.
                    }
                    break;
                case CANCELLING:
                    try {
                        // 如果是事务回滚，此时需要检查事务是否存在
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        transactionManager.rollback(asyncCancel);
                    } catch (NoExistedTransactionException exception) {
                        //the transaction has been rollback,ignore it.
                    }
                    break;
            }

        } finally {
            transactionManager.cleanAfterCompletion(transaction);
        }
        Method method = compensableMethodContext.getMethod();
        return ReflectionUtils.getNullValue(method.getReturnType());
    }

    private boolean isDelayCancelException(Throwable throwable, Set<Class<? extends Exception>> delayCancelExceptions) {

        if (delayCancelExceptions != null) {
            for (Class delayCancelException : delayCancelExceptions) {

                Throwable rootCause = ExceptionUtils.getRootCause(throwable);

                if (delayCancelException.isAssignableFrom(throwable.getClass())
                        || (rootCause != null && delayCancelException.isAssignableFrom(rootCause.getClass()))) {
                    return true;
                }
            }
        }

        return false;
    }

}
