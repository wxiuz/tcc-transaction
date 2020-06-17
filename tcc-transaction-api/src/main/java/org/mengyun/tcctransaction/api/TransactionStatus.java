package org.mengyun.tcctransaction.api;

/**
 * Created by changmingxie on 10/28/15.
 */
public enum TransactionStatus {
    /**
     * 正在进行Try操作
     */
    TRYING(1),
    /**
     * 正在进行Confir操作
     */
    CONFIRMING(2),
    /**
     * 正在进行Cancel操作
     */
    CANCELLING(3);

    private int id;

    TransactionStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TransactionStatus valueOf(int id) {
        switch (id) {
            case 1:
                return TRYING;
            case 2:
                return CONFIRMING;
            default:
                return CANCELLING;
        }
    }
}
