package org.mengyun.tcctransaction.repository;


import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.serializer.KryoPoolSerializer;
import org.mengyun.tcctransaction.serializer.ObjectSerializer;
import org.mengyun.tcctransaction.utils.CollectionUtils;
import org.mengyun.tcctransaction.utils.StringUtils;

import javax.sql.DataSource;
import javax.transaction.xa.Xid;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 关系型数据库存储TCC事务日志
 * <p>
 * Created by changmingxie on 10/30/15.
 */
public class JdbcTransactionRepository extends CachableTransactionRepository {
    /**
     * 事务所属应用
     */
    private String domain;

    /**
     * 事务日志表的后缀
     */
    private String tbSuffix;

    /**
     * TCC事务日志数据源
     */
    private DataSource dataSource;

    /**
     * 日志内容序列化器
     */
    private ObjectSerializer serializer = new KryoPoolSerializer();

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getTbSuffix() {
        return tbSuffix;
    }

    public void setTbSuffix(String tbSuffix) {
        this.tbSuffix = tbSuffix;
    }

    public void setSerializer(ObjectSerializer serializer) {
        this.serializer = serializer;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * 添加新事务
     *
     * @param transaction
     * @return
     */
    protected int doCreate(Transaction transaction) {
        Connection connection = null;
        PreparedStatement stmt = null;

        try {
            connection = this.getConnection();
            StringBuilder builder = new StringBuilder();
            builder.append("INSERT INTO " + getTableName() +
                    "(GLOBAL_TX_ID,BRANCH_QUALIFIER,TRANSACTION_TYPE,CONTENT,STATUS,RETRIED_COUNT,CREATE_TIME,LAST_UPDATE_TIME,VERSION");
            builder.append(StringUtils.isNotEmpty(domain) ? ",DOMAIN ) VALUES (?,?,?,?,?,?,?,?,?,?)" : ") VALUES (?,?,?,?,?,?,?,?,?)");

            stmt = connection.prepareStatement(builder.toString());

            stmt.setBytes(1, transaction.getXid().getGlobalTransactionId());
            stmt.setBytes(2, transaction.getXid().getBranchQualifier());
            stmt.setInt(3, transaction.getTransactionType().getId());
            // 事务对象序列化后存储至库中
            stmt.setBytes(4, serializer.serialize(transaction));
            stmt.setInt(5, transaction.getStatus().getId());
            stmt.setInt(6, transaction.getRetriedCount());
            stmt.setTimestamp(7, new java.sql.Timestamp(transaction.getCreateTime().getTime()));
            stmt.setTimestamp(8, new java.sql.Timestamp(transaction.getLastUpdateTime().getTime()));
            stmt.setLong(9, transaction.getVersion());

            if (StringUtils.isNotEmpty(domain)) {
                stmt.setString(10, domain);
            }

            stmt.executeUpdate();
            return 1;
        } catch (SQLException e) {
            if (e instanceof SQLIntegrityConstraintViolationException) {
                return 0;
            } else {
                throw new TransactionIOException(e);
            }
        } catch (Throwable throwable) {
            throw new TransactionIOException(throwable);
        } finally {
            closeStatement(stmt);
            this.releaseConnection(connection);
        }
    }

    /**
     * 更新事务
     *
     * @param transaction
     * @return
     */
    protected int doUpdate(Transaction transaction) {
        Connection connection = null;
        PreparedStatement stmt = null;

        java.util.Date lastUpdateTime = transaction.getLastUpdateTime();
        long currentVersion = transaction.getVersion();

        transaction.updateTime();
        transaction.updateVersion();

        try {
            connection = this.getConnection();

            StringBuilder builder = new StringBuilder();
            builder.append("UPDATE " + getTableName() + " SET " +
                    "CONTENT = ?,STATUS = ?,LAST_UPDATE_TIME = ?, RETRIED_COUNT = ?,VERSION = VERSION+1 WHERE GLOBAL_TX_ID = ? AND BRANCH_QUALIFIER = ? AND VERSION = ?");

            builder.append(StringUtils.isNotEmpty(domain) ? " AND DOMAIN = ?" : "");

            stmt = connection.prepareStatement(builder.toString());

            stmt.setBytes(1, serializer.serialize(transaction));
            stmt.setInt(2, transaction.getStatus().getId());
            stmt.setTimestamp(3, new Timestamp(transaction.getLastUpdateTime().getTime()));

            stmt.setInt(4, transaction.getRetriedCount());
            stmt.setBytes(5, transaction.getXid().getGlobalTransactionId());
            stmt.setBytes(6, transaction.getXid().getBranchQualifier());
            stmt.setLong(7, currentVersion);

            if (StringUtils.isNotEmpty(domain)) {
                stmt.setString(8, domain);
            }

            int result = stmt.executeUpdate();

            return result;

        } catch (Throwable e) {
            transaction.setLastUpdateTime(lastUpdateTime);
            transaction.setVersion(currentVersion);
            throw new TransactionIOException(e);
        } finally {
            closeStatement(stmt);
            this.releaseConnection(connection);
        }
    }

    /**
     * 删除事务
     *
     * @param transaction
     * @return
     */
    protected int doDelete(Transaction transaction) {
        Connection connection = null;
        PreparedStatement stmt = null;

        try {
            connection = this.getConnection();

            StringBuilder builder = new StringBuilder();
            builder.append("DELETE FROM " + getTableName() +
                    " WHERE GLOBAL_TX_ID = ? AND BRANCH_QUALIFIER = ?");

            builder.append(StringUtils.isNotEmpty(domain) ? " AND DOMAIN = ?" : "");

            stmt = connection.prepareStatement(builder.toString());

            stmt.setBytes(1, transaction.getXid().getGlobalTransactionId());
            stmt.setBytes(2, transaction.getXid().getBranchQualifier());

            if (StringUtils.isNotEmpty(domain)) {
                stmt.setString(3, domain);
            }

            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new TransactionIOException(e);
        } finally {
            closeStatement(stmt);
            this.releaseConnection(connection);
        }
    }

    protected Transaction doFindOne(Xid xid) {

        List<Transaction> transactions = doFind(Arrays.asList(xid));

        if (!CollectionUtils.isEmpty(transactions)) {
            return transactions.get(0);
        }
        return null;
    }

    /**
     * 查询所有异常事务
     *
     * @param date
     * @return
     */
    @Override
    protected List<Transaction> doFindAllUnmodifiedSince(java.util.Date date) {

        List<Transaction> transactions = new ArrayList<Transaction>();

        Connection connection = null;
        PreparedStatement stmt = null;

        try {
            connection = this.getConnection();

            StringBuilder builder = new StringBuilder();

            builder.append("SELECT GLOBAL_TX_ID, BRANCH_QUALIFIER, CONTENT,STATUS,TRANSACTION_TYPE,CREATE_TIME,LAST_UPDATE_TIME,RETRIED_COUNT,VERSION");
            builder.append(StringUtils.isNotEmpty(domain) ? ",DOMAIN" : "");
            builder.append("  FROM " + getTableName() + " WHERE LAST_UPDATE_TIME < ?");
            builder.append(" AND IS_DELETE = 0 ");
            builder.append(StringUtils.isNotEmpty(domain) ? " AND DOMAIN = ?" : "");

            stmt = connection.prepareStatement(builder.toString());

            stmt.setTimestamp(1, new Timestamp(date.getTime()));

            if (StringUtils.isNotEmpty(domain)) {
                stmt.setString(2, domain);
            }

            ResultSet resultSet = stmt.executeQuery();

            this.constructTransactions(resultSet, transactions);
        } catch (Throwable e) {
            throw new TransactionIOException(e);
        } finally {
            closeStatement(stmt);
            this.releaseConnection(connection);
        }

        return transactions;
    }

    protected List<Transaction> doFind(List<Xid> xids) {

        List<Transaction> transactions = new ArrayList<Transaction>();

        if (CollectionUtils.isEmpty(xids)) {
            return transactions;
        }

        Connection connection = null;
        PreparedStatement stmt = null;

        try {
            connection = this.getConnection();

            StringBuilder builder = new StringBuilder();
            builder.append("SELECT GLOBAL_TX_ID, BRANCH_QUALIFIER, CONTENT,STATUS,TRANSACTION_TYPE,CREATE_TIME,LAST_UPDATE_TIME,RETRIED_COUNT,VERSION");
            builder.append(StringUtils.isNotEmpty(domain) ? ",DOMAIN" : "");
            builder.append("  FROM " + getTableName() + " WHERE");

            if (!CollectionUtils.isEmpty(xids)) {
                for (Xid xid : xids) {
                    builder.append(" ( GLOBAL_TX_ID = ? AND BRANCH_QUALIFIER = ? ) OR");
                }

                builder.delete(builder.length() - 2, builder.length());
            }

            builder.append(StringUtils.isNotEmpty(domain) ? " AND DOMAIN = ?" : "");

            stmt = connection.prepareStatement(builder.toString());

            int i = 0;

            for (Xid xid : xids) {
                stmt.setBytes(++i, xid.getGlobalTransactionId());
                stmt.setBytes(++i, xid.getBranchQualifier());
            }

            if (StringUtils.isNotEmpty(domain)) {
                stmt.setString(++i, domain);
            }

            ResultSet resultSet = stmt.executeQuery();

            this.constructTransactions(resultSet, transactions);
        } catch (Throwable e) {
            throw new TransactionIOException(e);
        } finally {
            closeStatement(stmt);
            this.releaseConnection(connection);
        }

        return transactions;
    }

    protected void constructTransactions(ResultSet resultSet, List<Transaction> transactions) throws SQLException {
        while (resultSet.next()) {
            byte[] transactionBytes = resultSet.getBytes(3);
            Transaction transaction = (Transaction) serializer.deserialize(transactionBytes);
            transaction.changeStatus(TransactionStatus.valueOf(resultSet.getInt(4)));
            transaction.setLastUpdateTime(resultSet.getDate(7));
            transaction.setVersion(resultSet.getLong(9));
            transaction.resetRetriedCount(resultSet.getInt(8));
            transactions.add(transaction);
        }
    }


    protected Connection getConnection() {
        try {
            return this.dataSource.getConnection();
        } catch (SQLException e) {
            throw new TransactionIOException(e);
        }
    }

    protected void releaseConnection(Connection con) {
        try {
            if (con != null && !con.isClosed()) {
                con.close();
            }
        } catch (SQLException e) {
            throw new TransactionIOException(e);
        }
    }

    private void closeStatement(Statement stmt) {
        try {
            if (stmt != null && !stmt.isClosed()) {
                stmt.close();
            }
        } catch (Exception ex) {
            throw new TransactionIOException(ex);
        }
    }

    /**
     * 获取事务日志存储表，默认是TCC_TRANSACTION，如果自定义了后缀则为TCC_TRANSACTION${tbSuffix}
     *
     * @return
     */
    private String getTableName() {
        return StringUtils.isNotEmpty(tbSuffix) ? "TCC_TRANSACTION" + tbSuffix : "TCC_TRANSACTION";
    }
}
