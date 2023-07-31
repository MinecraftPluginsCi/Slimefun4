package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.postgresql;

import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.IDataSourceAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.List;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_BACKPACK_ID;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_BACKPACK_NAME;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_BACKPACK_NUM;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_BACKPACK_SIZE;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_CHUNK;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_DATA_KEY;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_DATA_VALUE;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_INVENTORY_ITEM;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_INVENTORY_SLOT;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_LOCATION;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_PLAYER_NAME;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_PLAYER_UUID;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_RESEARCH_KEY;
import static com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlConstants.FIELD_SLIMEFUN_ID;

public class PostgreSqlAdapter implements IDataSourceAdapter<PostgreSqlConfig> {
    private HikariDataSource ds;
    private PostgreSqlConfig config;
    private String profileTable, researchTable, backpackTable, bpInvTable;
    private String blockRecordTable, blockDataTable, chunkDataTable, blockInvTable;

    @Override
    public void prepare(PostgreSqlConfig config) {
        ds = config.createDataSource();
        this.config = config;
    }

    @Override
    public void initStorage(DataType type) {
        switch (type) {
            case PLAYER_PROFILE -> {
                profileTable = SqlUtils.mapTable(DataScope.PLAYER_PROFILE, config.tablePrefix());
                researchTable = SqlUtils.mapTable(DataScope.PLAYER_RESEARCH, config.tablePrefix());
                backpackTable = SqlUtils.mapTable(DataScope.BACKPACK_PROFILE, config.tablePrefix());
                bpInvTable = SqlUtils.mapTable(DataScope.BACKPACK_INVENTORY, config.tablePrefix());
                createProfileTables();
            }
            case BLOCK_STORAGE -> {
                blockRecordTable = SqlUtils.mapTable(DataScope.BLOCK_RECORD, config.tablePrefix());
                blockDataTable = SqlUtils.mapTable(DataScope.BLOCK_DATA, config.tablePrefix());
                blockInvTable = SqlUtils.mapTable(DataScope.BLOCK_INVENTORY, config.tablePrefix());
                chunkDataTable = SqlUtils.mapTable(DataScope.CHUNK_DATA, config.tablePrefix());
                createBlockStorageTables();
            }
        }
    }

    @Override
    public void shutdown() {
        ds.close();
        ds = null;
        profileTable = null;
        researchTable = null;
        backpackTable = null;
        bpInvTable = null;
        blockDataTable = null;
        blockRecordTable = null;
        chunkDataTable = null;
        blockInvTable = null;
    }

    @Override
    public void setData(RecordKey key, RecordSet item) {
        var data = item.getAll();
        var fields = data.keySet();
        var fieldStr = SqlUtils.buildFieldStr(fields);
        if (fieldStr.isEmpty()) {
            throw new IllegalArgumentException("No data provided in RecordSet.");
        }

        var valStr = new StringBuilder();
        var flag = false;
        for (var field : fields) {
            if (flag) {
                valStr.append(", ");
            } else {
                flag = true;
            }
            valStr.append(SqlUtils.toSqlValStr(field, data.get(field)));
        }

        var updateFields = key.getFields();
        executeSql(
                "INSERT INTO " + mapTable(key.getScope()) + " (" + fieldStr.get() + ") "
                        + "VALUES (" + valStr + ")"
                        + (updateFields.isEmpty() ? "" : " ON CONFLICT (" + fieldStr.get() + ") DO UPDATE SET "
                        + String.join(", ", updateFields.stream().map(field -> {
                    var val = item.get(field);
                    if (val == null) {
                        throw new IllegalArgumentException("Cannot find value in RecordSet for the specific key: " + field);
                    }
                    return SqlUtils.buildKvStr(field, val);
                }).toList())
                ) + ";"
        );
    }

    @Override
    public List<RecordSet> getData(RecordKey key) {
        return executeQuery(
                "SELECT " + SqlUtils.buildFieldStr(key.getFields()).orElse("*")
                        + " FROM " + mapTable(key.getScope())
                        + SqlUtils.buildConditionStr(key.getConditions()) + ";"
        );
    }

    @Override
    public void deleteData(RecordKey key) {
        executeSql("DELETE FROM " + mapTable(key.getScope()) + SqlUtils.buildConditionStr(key.getConditions()) + ";");
    }


    private void createProfileTables() {
        createProfileTable();
        createResearchTable();
        createBackpackTable();
        createBackpackInventoryTable();
    }

    private void createBlockStorageTables() {
        createBlockRecordTable();
        createBlockDataTable();
        createBlockInvTable();
        createChunkDataTable();
    }

    private void createProfileTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + profileTable + "("
                        + FIELD_PLAYER_UUID + " CHAR(64) PRIMARY KEY NOT NULL, "
                        + FIELD_PLAYER_NAME + " CHAR(64) NOT NULL, "
                        + FIELD_BACKPACK_NUM + " INT DEFAULT 0"
                        + ");"
        );

        executeSql("CREATE INDEX index_player_name ON " + profileTable + " (" + FIELD_PLAYER_NAME + ");");
    }

    private void createResearchTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + researchTable + "("
                        + FIELD_PLAYER_UUID + " CHAR(64) NOT NULL, "
                        + FIELD_RESEARCH_KEY + " CHAR(64) NOT NULL, "
                        + "FOREIGN KEY (" + FIELD_PLAYER_UUID + ") "
                        + "REFERENCES " + profileTable + "(" + FIELD_PLAYER_UUID + ") "
                        + "ON UPDATE CASCADE ON DELETE CASCADE"
                        + ");"
        );

        executeSql("CREATE INDEX index_player_research ON " + researchTable + " (" + FIELD_PLAYER_UUID + ", " + FIELD_RESEARCH_KEY + ");");
    }

    private void createBackpackTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + backpackTable + "("
                        + FIELD_BACKPACK_ID + " CHAR(64) PRIMARY KEY NOT NULL, "
                        + FIELD_PLAYER_UUID + " CHAR(64) NOT NULL, "
                        + FIELD_BACKPACK_NUM + " INT NOT NULL, "
                        + FIELD_BACKPACK_NAME + " CHAR(64) NULL, "
                        + FIELD_BACKPACK_SIZE + " SMALLINT NOT NULL, "
                        + "FOREIGN KEY (" + FIELD_PLAYER_UUID + ") "
                        + "REFERENCES " + profileTable + "(" + FIELD_PLAYER_UUID + ") "
                        + "ON UPDATE CASCADE ON DELETE CASCADE"
                        + ");"
        );

        executeSql("CREATE INDEX index_player_backpack ON " + backpackTable + " (" + FIELD_PLAYER_UUID + ", " + FIELD_BACKPACK_NUM + ");");
    }

    private void createBackpackInventoryTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + bpInvTable + "("
                        + FIELD_BACKPACK_ID + " CHAR(64) NOT NULL, "
                        + FIELD_INVENTORY_SLOT + " SMALLINT NOT NULL, "
                        + FIELD_INVENTORY_ITEM + " TEXT NOT NULL, "
                        + "FOREIGN KEY (" + FIELD_BACKPACK_ID + ") "
                        + "REFERENCES " + backpackTable + "(" + FIELD_BACKPACK_ID + ") "
                        + "ON UPDATE CASCADE ON DELETE CASCADE, "
                        + "PRIMARY KEY (" + FIELD_BACKPACK_ID + ", " + FIELD_INVENTORY_SLOT + ")"
                        + ");"
        );
    }

    private void createBlockRecordTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + blockRecordTable + "("
                        + FIELD_LOCATION + " CHAR(64) PRIMARY KEY NOT NULL, "
                        + FIELD_CHUNK + " CHAR(64) NOT NULL, "
                        + FIELD_SLIMEFUN_ID + " CHAR(64) NOT NULL"
                        + ");"
        );

        executeSql("CREATE INDEX index_ticking ON " + blockRecordTable + " (" + FIELD_CHUNK + ");");
    }

    private void createBlockDataTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + blockDataTable + "("
                        + FIELD_LOCATION + " CHAR(64) NOT NULL, "
                        + FIELD_DATA_KEY + " CHAR(64) NOT NULL, "
                        + FIELD_DATA_VALUE + " TEXT NOT NULL, "
                        + "FOREIGN KEY (" + FIELD_LOCATION + ") "
                        + "REFERENCES " + blockRecordTable + "(" + FIELD_LOCATION + ") "
                        + "ON UPDATE CASCADE ON DELETE CASCADE, "
                        + "PRIMARY KEY (" + FIELD_LOCATION + ", " + FIELD_DATA_KEY + ")"
                        + ");"
        );
    }

    private void createChunkDataTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + chunkDataTable + "("
                        + FIELD_CHUNK + " CHAR(64) NOT NULL, "
                        + FIELD_DATA_KEY + " CHAR(64) NOT NULL, "
                        + FIELD_DATA_VALUE + " TEXT NOT NULL, "
                        + "PRIMARY KEY (" + FIELD_CHUNK + ", " + FIELD_DATA_KEY + ")"
                        + ");"
        );
    }

    private void createBlockInvTable() {
        executeSql(
                "CREATE TABLE IF NOT EXISTS "
                        + blockInvTable + "("
                        + FIELD_LOCATION + " CHAR(64) NOT NULL, "
                        + FIELD_INVENTORY_SLOT + " SMALLINT NOT NULL, "
                        + FIELD_INVENTORY_ITEM + " TEXT NOT NULL, "
                        + "FOREIGN KEY (" + FIELD_LOCATION + ") "
                        + "REFERENCES " + blockRecordTable + "(" + FIELD_LOCATION + ") "
                        + "ON UPDATE CASCADE ON DELETE CASCADE, "
                        + "PRIMARY KEY (" + FIELD_LOCATION + ", " + FIELD_INVENTORY_SLOT + ")"
                        + ");"
        );
    }

    private void executeSql(String sql) {
        try (var conn = ds.getConnection()) {
            SqlUtils.execSql(conn, sql);
        } catch (SQLException e) {
            throw new IllegalStateException("An exception thrown while executing sql: " + sql, e);
        }
    }

    private List<RecordSet> executeQuery(String sql) {
        try (var conn = ds.getConnection()) {
            return SqlUtils.execQuery(conn, sql);
        } catch (SQLException e) {
            throw new IllegalStateException("An exception thrown while executing sql: " + sql, e);
        }
    }

    private String mapTable(DataScope scope) {
        return switch (scope) {
            case PLAYER_PROFILE -> profileTable;
            case BACKPACK_INVENTORY -> bpInvTable;
            case BACKPACK_PROFILE -> backpackTable;
            case PLAYER_RESEARCH -> researchTable;
            case BLOCK_INVENTORY -> blockInvTable;
            case CHUNK_DATA -> chunkDataTable;
            case BLOCK_DATA -> blockDataTable;
            case BLOCK_RECORD -> blockRecordTable;
            case NONE -> throw new IllegalArgumentException("NONE cannot be a storage data scope!");
        };
    }
}
