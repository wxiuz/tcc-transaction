package org.mengyun.tcctransaction;


import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.common.TransactionType;

import javax.transaction.xa.Xid;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCC事务Model
 * <p>
 * Created by changmingxie on 10/26/15.
 */
public class Transaction implements Serializable {

    private static final long serialVersionUID = 7291423944314337931L;

    /**
     * 事务ID，事务的唯一性标志
     */
    private TransactionXid xid;

    /**
     * 事务的当前状态： TRYING，CONFIRMING，CANCELLING
     */
    private TransactionStatus status;

    /**
     * 事务类型： ROOT，BRANCH
     */
    private TransactionType transactionType;

    /**
     * 事务的已重试次数
     */
    private volatile int retriedCount = 0;

    private Date createTime = new Date();

    private Date lastUpdateTime = new Date();

    /**
     * 事务当前版本：乐观锁
     */
    private long version = 1;

    /**
     * 事务的参与者：包括事务的发起方和其他参与方
     */
    private List<Participant> participants = new ArrayList<Participant>();

    /**
     * 扩展信息
     */
    private Map<String, Object> attachments = new ConcurrentHashMap<String, Object>();

    public Transaction() {

    }

    /**
     * TRYING + BRANCH事务
     *
     * @param transactionContext
     */
    public Transaction(TransactionContext transactionContext) {
        this.xid = transactionContext.getXid();
        this.status = TransactionStatus.TRYING;
        this.transactionType = TransactionType.BRANCH;
    }

    /**
     * TRYING
     *
     * @param transactionType
     */
    public Transaction(TransactionType transactionType) {
        this.xid = new TransactionXid();
        this.status = TransactionStatus.TRYING;
        this.transactionType = transactionType;
    }

    /**
     * TRYING
     *
     * @param uniqueIdentity
     * @param transactionType
     */
    public Transaction(Object uniqueIdentity, TransactionType transactionType) {
        this.xid = new TransactionXid(uniqueIdentity);
        this.status = TransactionStatus.TRYING;
        this.transactionType = transactionType;
    }

    /**
     * 为事务添加参与者
     *
     * @param participant
     */
    public void enlistParticipant(Participant participant) {
        participants.add(participant);
    }


    public Xid getXid() {
        return xid.clone();
    }

    public TransactionStatus getStatus() {
        return status;
    }


    public List<Participant> getParticipants() {
        return participants;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void changeStatus(TransactionStatus status) {
        this.status = status;
    }

    /**
     * 提交当前事务，依次调用所有的参与者发起事务的提交
     */
    public void commit() {
        for (Participant participant : participants) {
            participant.commit();
        }
    }

    /**
     * 回滚当前事务，依次调用所有的参与者发起事务的回滚
     */
    public void rollback() {
        for (Participant participant : participants) {
            participant.rollback();
        }
    }

    public int getRetriedCount() {
        return retriedCount;
    }

    public void addRetriedCount() {
        this.retriedCount++;
    }

    public void resetRetriedCount(int retriedCount) {
        this.retriedCount = retriedCount;
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    public long getVersion() {
        return version;
    }

    public void updateVersion() {
        this.version++;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(Date date) {
        this.lastUpdateTime = date;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void updateTime() {
        this.lastUpdateTime = new Date();
    }


}
