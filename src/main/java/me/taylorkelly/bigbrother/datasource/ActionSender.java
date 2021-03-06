package me.taylorkelly.bigbrother.datasource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;

import me.taylorkelly.bigbrother.ActionProvider;
import me.taylorkelly.bigbrother.BBLogging;
import me.taylorkelly.bigbrother.BBSettings;
import me.taylorkelly.bigbrother.BigBrother;
import me.taylorkelly.bigbrother.WorldManager;
import me.taylorkelly.bigbrother.datablock.Action;
import me.taylorkelly.bigbrother.datablock.Heartbeat;
import me.taylorkelly.bigbrother.tablemgrs.BBDataTable;

/**
 * Sends Action data to the Action table in intervals.
 * 
 * @author Rob
 * 
 */
public class ActionSender {
    
    public static final LinkedBlockingQueue<Action> SENDING = new LinkedBlockingQueue<Action>();
    private static int sendingTask;
    private static BigBrother plugin;
    
    public static void shutdown(final BigBrother bb) {
        if (sendingTask >= 0) {
            bb.getServer().getScheduler().cancelTask(sendingTask);
        }
    }
    
    public static void initialize(final BigBrother bb, final File dataFolder, final WorldManager manager) {
        plugin = bb;
        sendingTask = bb.getServer().getScheduler().scheduleAsyncRepeatingTask(bb, new SendingTask(dataFolder, manager), BBSettings.sendDelay * 30, BBSettings.sendDelay * 30);
        if (sendingTask < 0) {
            BBLogging.severe("Unable to schedule sending of blocks");
        }
    }
    
    public static void offer(final Action dataBlock) {
        SENDING.add(dataBlock);
    }
    
    private static boolean sendBlocksSQL(final Collection<Action> collection, final WorldManager manager) {
        // Try to refactor most of these into the table managers.
        
        // Send a heartbeat after sending blocks.
        final Heartbeat hb = new Heartbeat(plugin);
        hb.send();
        PreparedStatement ps = null;
        final ResultSet rs = null;
        try {
            final String statementSql = BBDataTable.getInstance().getPreparedDataBlockStatement();
            BBLogging.debug(statementSql);
            ps = BBDB.prepare(statementSql);
            for (final Action block : collection) {
                if (BBSettings.worldExclusionList.contains(block.world)) {
                    continue;
                }
                
                ps.setLong(1, block.date);
                ps.setInt(2, block.player.getID());
                ps.setInt(3, ActionProvider.getActionID(block));
                ps.setInt(4, manager.getWorld(block.world));
                ps.setInt(5, block.x);
                if (block.y < 0) {
                    block.y = 0;
                }
                if (block.y > 127) {
                    block.y = 127;
                }
                ps.setInt(6, block.y);
                ps.setInt(7, block.z);
                ps.setInt(8, block.type);
                ps.setString(9, block.data);
                ps.addBatch();
            }
            ps.executeBatch();
            BBDB.commit();
            return true;
        } catch (final SQLException ex) {
            BBLogging.severe("Data Insert SQL Exception when sending blocks", ex);
            BBLogging.severe("Possible cause of previous SQLException: ", ex.getNextException());
            return false;
        } finally {
            BBDB.cleanup("Data Insert", ps, rs);
        }
    }
    
    private static void sendBlocksFlatFile(final File dataFolder, final Collection<Action> collection) {
        final File dir = new File(dataFolder, "logs");
        if (!dir.exists()) {
            dir.mkdir();
        }
        BufferedWriter bwriter = null;
        FileWriter fwriter = null;
        try {
            for (final Action block : collection) {
                final File file = new File(dir, fixName(block.player.getName()) + ".log");
                final StringBuilder builder = new StringBuilder(Long.toString(System.currentTimeMillis()));
                builder.append(" - ");
                builder.append(block.toString());
                builder.append(" ");
                builder.append(block.world);
                builder.append("@(");
                builder.append(block.x);
                builder.append(",");
                builder.append(block.y);
                builder.append(",");
                builder.append(block.z);
                builder.append(") info: ");
                builder.append(block.type);
                builder.append(", ");
                builder.append(block.data);
                
                fwriter = new FileWriter(file, true);
                bwriter = new BufferedWriter(fwriter);
                bwriter.write(builder.toString());
                bwriter.newLine();
                bwriter.flush();
                bwriter.close();
                fwriter.close();
            }
        } catch (final IOException e) {
            BBLogging.severe("Data Insert IO Exception", e);
        } finally {
            try {
                if (bwriter != null) {
                    bwriter.close();
                }
                if (fwriter != null) {
                    fwriter.close();
                }
            } catch (final IOException e) {
                BBLogging.severe("Data Insert IO Exception (on close)", e);
            }
        }
    }
    
    public static String fixName(final String player) {
        return player.replace(".", "").replace(":", "").replace("<", "").replace(">", "").replace("*", "").replace("\\", "").replace("/", "").replace("?", "").replace("\"", "").replace("|", "");
    }
    
    private static class SendingTask implements Runnable {
        
        private final File dataFolder;
        private final WorldManager manager;
        
        public SendingTask(final File dataFolder, final WorldManager manager) {
            this.dataFolder = dataFolder;
            this.manager = manager;
        }
        
        public void run() {
            if (SENDING.size() == 0)
                return;
            final Collection<Action> collection = new ArrayList<Action>();
            SENDING.drainTo(collection);
            
            final boolean worked = sendBlocksSQL(collection, manager);
            if (BBSettings.flatLog) {
                sendBlocksFlatFile(dataFolder, collection);
            }
            
            if (!worked) {
                SENDING.addAll(collection);
                BBLogging.warning("SQL send failed. Keeping data for later send.");
            }
        }
    }
}
