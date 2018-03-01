package com.neighborhood.aka.laplce.estuary.bean.data;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

/**
 * Created by z on 17-3-31.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BinlogKey extends BaseDataJsonKey {
    /**
     * 要同步的数据库的信息
     */
    private String dbResId;
    private String mysqlJournalName;
    private long mysqlPosition;
    private long mysqlTimestamp;
    private long serverId;
    /**
     * 保存在zk中的binlog文件, 与savedOffset是对应的.
     */
    private String savedJournalName;
    /**
     * 保存在zk中的偏移量, 会与本身数据的offset不一致, 比当前数据的offset靠前.
     */
    private long savedOffset;

    public String getDbResId() {
        return dbResId;
    }

    public void setDbResId(String dbResId) {
        this.dbResId = dbResId;
    }

    public String getMysqlJournalName() {
        return mysqlJournalName;
    }

    public void setMysqlJournalName(String mysqlJournalName) {
        this.mysqlJournalName = mysqlJournalName;
    }

    public long getMysqlPosition() {
        return mysqlPosition;
    }

    public void setMysqlPosition(long mysqlPosition) {
        this.mysqlPosition = mysqlPosition;
    }

    public long getMysqlTimestamp() {
        return mysqlTimestamp;
    }

    public void setMysqlTimestamp(long mysqlTimestamp) {
        this.mysqlTimestamp = mysqlTimestamp;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public String getSavedJournalName() {
        return savedJournalName;
    }

    public void setSavedJournalName(String savedJournalName) {
        this.savedJournalName = savedJournalName;
    }

    public long getSavedOffset() {
        return savedOffset;
    }

    public void setSavedOffset(long savedOffset) {
        this.savedOffset = savedOffset;
    }

    @Override
    public String toString() {
        return "BinlogKey{" +
            "dbResId='" + dbResId + '\'' +
            ", mysqlJournalName='" + mysqlJournalName + '\'' +
            ", mysqlPosition=" + mysqlPosition +
            ", mysqlTimestamp=" + mysqlTimestamp +
            ", serverId=" + serverId +
            ", savedJournalName='" + savedJournalName + '\'' +
            ", savedOffset=" + savedOffset +
                "appName='" + appName + '\'' +
                ", appServerIp='" + appServerIp + '\'' +
                ", appServerPort=" + appServerPort +
                ", syncTaskId='" + syncTaskId + '\'' +
                ", syncTaskStartTime=" + syncTaskStartTime +
                ", syncTaskSequence=" + syncTaskSequence +
                ", sourceType='" + sourceType + '\'' +
                ", dbName='" + dbName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", msgUuid='" + msgUuid + '\'' +
                ", msgSyncStartTime=" + msgSyncStartTime +
                ", msgSyncEndTime=" + msgSyncEndTime +
                ", dbEffectTime=" + dbEffectTime +
                ", msgSyncUsedTime=" + msgSyncUsedTime +
                ", msgSize=" + msgSize +
                ", kafkaTopic='" + kafkaTopic + '\'' +
                ", kafkaPartition=" + kafkaPartition +
                ", kafkaOffset=" + kafkaOffset +
                ", eventType='" + eventType + '\'' +
            '}';
    }
}
