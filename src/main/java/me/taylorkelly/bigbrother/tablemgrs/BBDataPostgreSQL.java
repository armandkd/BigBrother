package me.taylorkelly.bigbrother.tablemgrs;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import me.taylorkelly.bigbrother.ActionProvider;
import me.taylorkelly.bigbrother.BBLogging;
import me.taylorkelly.bigbrother.WorldManager;
import me.taylorkelly.bigbrother.datablock.Action;
import me.taylorkelly.bigbrother.datasource.BBDB;

import org.bukkit.block.Block;

public class BBDataPostgreSQL extends BBDataMySQL {
    public static String getMySQLIgnore() {
        return " ";
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see me.taylorkelly.bigbrother.tablemgrs.DBTable#onLoad()
     */
    @Override
    public void onLoad() {
        
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see me.taylorkelly.bigbrother.tablemgrs.DBTable#getCreateSyntax()
     */
    @Override
    public String getCreateSyntax() {
        return "CREATE TABLE \"" + getTableName() + "\" (" + "\"id\" SERIAL," + "\"date\" INT NOT NULL DEFAULT '0'," + "\"player\" INT NOT NULL DEFAULT 0," + "\"action\" smallint NOT NULL DEFAULT '0'," + "\"world\" smallint NOT NULL DEFAULT '0'," + "\"x\" int NOT NULL DEFAULT '0'," + "\"y\" smallint NOT NULL DEFAULT '0'," + "\"z\" int NOT NULL DEFAULT '0'," + "\"type\" smallint NOT NULL DEFAULT '0'," + "\"data\" TEXT NOT NULL DEFAULT ''," + "\"rbacked\" boolean NOT NULL DEFAULT '0'," + "PRIMARY KEY (\"id\"));" + "CREATE INDEX \"" + getTableName() + "_index_world\"" + " ON \"" + getTableName() + "\" (id);" + "CREATE INDEX \"" + getTableName() + "_index_x_y_z\"" + " ON \"" + getTableName() + "\" (x, y, z);" + "CREATE INDEX \"" + getTableName() + "_index_player\"" + " ON \"" + getTableName() + "\" (player);" + "CREATE INDEX \"" + getTableName() + "_index_action\"" + " ON \"" + getTableName() + "\" (action);" + "CREATE INDEX \"" + getTableName() + "_index_date\"" + " ON \"" + getTableName() + "\" (date);" + "CREATE INDEX \"" + getTableName() + "_index_type\"" + " ON \"" + getTableName() + "\" (type);" + "CREATE INDEX \"" + getTableName() + "_index_rbacked\"" + " ON \"" + getTableName() + "\" (rbacked);";
    }
    
    @Override
    public String getPreparedDataBlockStatement() throws SQLException {
        // rbacked is truly boolean, unlike in MySQL
        return "INSERT INTO " + getTableName() + " (date, player, action, world, x, y, z, type, data, rbacked) VALUES (?,?,?,?,?,?,?,?,?,false)";
    }
    
    @Override
    public String getCleanseAged(final Long timeAgo, final long deletesPerCleansing) {
        String cleansql = "DELETE FROM \"" + getTableName() + "\" WHERE ";
        if (deletesPerCleansing > 0) {
            // LIMIT on DELETE is not supported by postgresql.
            cleansql += " id IN (SELECT id FROM \"" + getTableName() + "\" WHERE date < " + timeAgo + " LIMIT " + deletesPerCleansing + ")";
        } else {
            cleansql += " date < " + timeAgo;
        }
        cleansql += ";";
        return cleansql;
    }
    
    @Override
    public int getCleanseByLimit(final Statement stmt, final Long maxRecords, final long deletesPerCleansing) throws SQLException {
        String cleansql = "DELETE FROM \"" + getTableName() + "\" LEFT OUTER JOIN (SELECT \"id\" FROM \"bbdata\" ORDER BY \"id\" DESC LIMIT 0," + maxRecords + ") AS \"savedValues\" ON \"savedValues.id\" = \"bbdata.id\" WHERE \"savedValues.id\" IS NULL";
        if (deletesPerCleansing > 0) {
            cleansql += " LIMIT " + deletesPerCleansing;
        }
        return stmt.executeUpdate(cleansql);
    }
    
    @Override
    public ArrayList<Action> getBlockHistory(final Block block, final WorldManager manager) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        final ArrayList<Action> blockList = new ArrayList<Action>();
        
        try {
            // TODO maybe more customizable actions?
            ps = BBDB.prepare("SELECT  bbdata.id, date, player, action, x, y, z, type, data, rbacked, bbworlds.name AS world" + " FROM " + BBDataTable.getInstance().getTableName() + " AS bbdata" + " INNER JOIN " + BBWorldsTable.getInstance().getTableName() + " AS bbworlds" + " ON bbworlds.id = bbdata.world" + " WHERE rbacked = false AND x = ? AND y = ? AND z = ? AND bbdata.world = ? ORDER BY bbdata.id ASC;");
            
            ps.setInt(1, block.getX());
            ps.setInt(2, block.getY());
            ps.setInt(3, block.getZ());
            ps.setInt(4, manager.getWorld(block.getWorld().getName()));
            rs = ps.executeQuery();
            BBDB.commit();
            
            while (rs.next()) {
                final String data = rs.getString("data");
                final Action newBlock = ActionProvider.findAndProvide(rs.getInt("action"), BBUsersTable.getInstance().getUserByID(rs.getInt("player")), rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("type"), data);
                newBlock.date = rs.getLong("date");
                blockList.add(newBlock);
            }
        } catch (final SQLException ex) {
            BBLogging.severe("Find SQL Exception", ex);
        } finally {
            BBDB.cleanup("Find", ps, rs);
        }
        return blockList;
        
    }
}
