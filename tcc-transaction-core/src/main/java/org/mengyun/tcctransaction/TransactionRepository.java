package org.mengyun.tcctransaction;

import org.mengyun.tcctransaction.api.TransactionXid;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 当一个TCC事务在执行时，此时会将TCC事务的执行日志进行存储，当TCC事务执行完成之后，则会删除对应的TCC事务日志。<br/>
 * TCC事务日志持久化，可以支持多种持久化的方式：<br/>
 * (1) 持久化至文件系统 {@link org.mengyun.tcctransaction.repository.FileSystemTransactionRepository} <br/>
 * (2) 持久化至Redis {@link org.mengyun.tcctransaction.repository.RedisTransactionRepository}  <br/>
 * (3) 持久化至关系型数据库 {@link org.mengyun.tcctransaction.repository.JdbcTransactionRepository}  <br/>
 * </p>
 *
 * @author changmingxie
 * @date 11/12/15
 */
public interface TransactionRepository {

    /**
     * 新建一个TCC事务
     *
     * @param transaction
     * @return
     */
    int create(Transaction transaction);

    /**
     * 更新TCC事务
     *
     * @param transaction
     * @return
     */
    int update(Transaction transaction);

    /**
     * 删除TCC事务
     *
     * @param transaction
     * @return
     */
    int delete(Transaction transaction);

    /**
     * 根据事务ID查询事务
     *
     * @param xid
     * @return
     */
    Transaction findByXid(TransactionXid xid);

    /**
     * 查询异常的事务用于重新恢复，异常事务的判断依据则是事务记录的更新时间超出指定范围，则认为异常
     *
     * @param date
     * @return
     */
    List<Transaction> findAllUnmodifiedSince(Date date);
}
