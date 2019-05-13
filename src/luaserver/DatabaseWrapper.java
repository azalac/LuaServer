/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package luaserver;

import java.io.Closeable;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

/**
 *
 * @author azalac
 */
public class DatabaseWrapper {

    public static class StatementWrapper implements Closeable {

        private final PreparedStatement statement;
        private final HashSet<StatementWrapper> openstatements;

        public StatementWrapper(PreparedStatement statement, HashSet<StatementWrapper> openstatements) {
            this.statement = statement;
            this.openstatements = openstatements;
            openstatements.add(this);
        }

        public LuaValue select() {
            return select(null);
        }

        public LuaValue update() {
            return update(null);
        }

        public LuaValue select(LuaTable parameters) {
            if (parameters != null && !parameters.isnil() && !setParameters(parameters)) {
                return LuaTable.NIL;
            }

            try {
                ResultSet results = statement.executeQuery();
                ResultSetMetaData metadata = results.getMetaData();
                LuaTable data = new LuaTable();
                int current = 1;

                while (results.next()) {
                    LuaTable row = new LuaTable();

                    for (int i = 1; i <= metadata.getColumnCount(); i++) {
                        row.set(metadata.getColumnLabel(i), CoerceJavaToLua.coerce(results.getObject(i)));
                    }

                    data.set(LuaValue.valueOf(current), row);
                    current++;
                }

                return data;
            } catch (SQLException ex) {
                Logger.getLogger(DatabaseWrapper.class.getName()).log(Level.SEVERE, null, ex);
                return LuaTable.NIL;
            }

        }

        public LuaValue update(LuaTable parameters) {
            if (parameters != null && !parameters.isnil()) {
                if (!setParameters(parameters)) {
                    return LuaTable.NIL;
                }
            }

            try {
                return LuaValue.valueOf(statement.executeUpdate());
            } catch (SQLException ex) {
                Logger.getLogger(DatabaseWrapper.class.getName()).log(Level.SEVERE, null, ex);
                return LuaTable.NIL;
            }
        }

        private boolean setParameters(LuaTable parameters) {
            try {
                statement.clearParameters();
                if (parameters.length() == 0) {
                    Logger.getLogger(DatabaseWrapper.class.getName()).log(Level.SEVERE,
                            "Warning: empty parameter table for statement ''{0}''", statement);
                }

                for (int i = 1; i <= parameters.length(); i++) {
                    setParameter(i, parameters.get(i));
                    System.out.println(i);
                }
            } catch (SQLException ex) {
                Logger.getLogger(DatabaseWrapper.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
            return true;
        }

        private void setParameter(int i, LuaValue value) throws SQLException {
            if (value.isboolean()) {
                statement.setBoolean(i, value.toboolean());
            } else if (value.isint()) {
                statement.setInt(i, value.toint());
            } else if (value.isnumber()) {
                statement.setDouble(i, value.todouble());
            } else if (value.isstring()) {
                statement.setString(i, value.tojstring());
            } else if (value.isuserdata()) {
                statement.setObject(i, value.touserdata());
            } else {
                Logger.getLogger(DatabaseWrapper.class.getName()).log(Level.SEVERE,
                        "Unsupported prepared statement type {0}", value.typename());
            }
            System.out.println(i + ":" + value.typename());
        }

        @Override
        public void close() throws IOException {
            try {
                statement.close();
                openstatements.remove(this);
            } catch (SQLException ex) {
                Logger.getLogger(DatabaseWrapper.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException(ex);
            }
        }

    }

    private final Database database;

    private final HashSet<StatementWrapper> openstatements = new HashSet<>();

    public DatabaseWrapper(Database database) {
        this.database = database;
    }

    public StatementWrapper prepare(String sql) {
        try {
            return new StatementWrapper(database.getConnection().prepareStatement(sql), openstatements);
        } catch (SQLException ex) {
            Logger.getLogger(DatabaseWrapper.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

}
