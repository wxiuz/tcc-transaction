package org.mengyun.tcctransaction.common;

/**
 * Created by changmingxie on 11/11/15.
 */
public enum MethodRole {
    /**
     * TCC事务入口方法
     */
    ROOT,
    /**
     *
     */
    CONSUMER,
    /**
     * TCC跨应用服务参与者入口方法
     */
    PROVIDER,
    /**
     * 同应用的方法
     */
    NORMAL;
}
