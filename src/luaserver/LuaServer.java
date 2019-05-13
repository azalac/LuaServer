/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package luaserver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author azalac
 */
public class LuaServer {

    public static void main(String[] args) throws IOException, SQLException {
        
        CountDownLatch latch = new CountDownLatch(1);
        
        Database db = new Database(getProperties("connection.properties"), latch);
        
        DatabaseWrapper db_wrapper = new DatabaseWrapper(db);
        
        ServerManager server = new ServerManager("localhost", 80);
        
        Logger.getLogger(LuaServer.class.toString()).log(Level.INFO, "Successfully created server");
        
        LuaLoader loader = new LuaLoader(db_wrapper, server::addEndpoint);
        
        loader.LoadDirectory(Paths.get("endpoints"));

        loader.FinishLoading();
        
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Logger.getLogger(LuaServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        server.start();
        
        Logger.getLogger(LuaServer.class.toString()).log(Level.INFO, "Started server, waiting for connections");
        
    }
    
    public static Properties getProperties(String path) throws IOException {
        
        Properties prop = new Properties();
        try(InputStream is = new FileInputStream(path);) {
            prop.load(is);
        } catch (FileNotFoundException ex) {
            Files.createFile(Paths.get(path));
        }
        
        return prop;
    }
    
}
