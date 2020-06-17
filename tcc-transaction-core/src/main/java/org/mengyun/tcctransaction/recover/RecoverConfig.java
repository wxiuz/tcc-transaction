package org.mengyun.tcctransaction.recover;

import java.util.Set;

/**
 * Created by changming.xie on 6/1/16.
 */
public interface RecoverConfig {
    /**
     * 最大恢复次数
     *
     * @return
     */
    int getMaxRetryCount();

    /**
     * 恢复间隔
     *
     * @return
     */
    int getRecoverDuration();

    /**
     * quartz job的cron表达式
     *
     * @return
     */
    String getCronExpression();

    /**
     * 需要重试的异常
     *
     * @return
     */
    Set<Class<? extends Exception>> getDelayCancelExceptions();

    void setDelayCancelExceptions(Set<Class<? extends Exception>> delayRecoverExceptions);

    int getAsyncTerminateThreadCorePoolSize();

    int getAsyncTerminateThreadMaxPoolSize();

    int getAsyncTerminateThreadWorkQueueSize();
}
