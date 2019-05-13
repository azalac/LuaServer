/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package luaserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 *
 * @author azalac
 */
public class LuaLoader {

    private final Globals globals = JsePlatform.standardGlobals();

    private final HashMap<Path, LuaValue> files = new HashMap<>();

    private final LuaTable endpoints = new LuaTable();

    private final Consumer<LuaEndpoint> onendpoint;

    public LuaLoader(DatabaseWrapper database, Consumer<LuaEndpoint> onendpoint) {
        globals.set("endpoints", endpoints);
        globals.set("database", CoerceJavaToLua.coerce(database));

        globals.set("modules", new LuaTable());
        
        globals.set("make_path", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return arg.isstring() ? CoerceJavaToLua.coerce(Paths.get(arg.tojstring())) : LuaValue.NIL;
            }
        });

        this.onendpoint = onendpoint;
    }

    public void LoadDirectory(Path files) {
        try {
            Files.walk(files).filter(p -> p.toString().endsWith("lua")).forEach(this::LoadEndpoint);
        } catch (IOException ex) {
            Logger.getLogger(LuaLoader.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void FinishLoading() {
        for (LuaValue key : endpoints.keys()) {
            LuaValue value = endpoints.get(key);
            if (value.istable()) {
                onendpoint.accept(LuaEndpoint.LoadEndpoint(value.checktable()));
            } else {
                Logger.getLogger(LuaLoader.class.getName()).log(Level.SEVERE, "Found non-table in endpoints");
            }
        }
    }

    private void LoadEndpoint(Path file) {
        Logger.getLogger(LuaLoader.class.toString()).log(Level.INFO, "Loading lua file {0}", file.toString());

        try {
            LuaValue value = globals.loadfile(file.toString());
            value.invoke(LuaValue.valueOf(file.toAbsolutePath().toString()));
            files.put(file, value);
        } catch (LuaError ex) {
            Logger.getLogger(LuaLoader.class.toString()).log(Level.SEVERE, "Could not load file: {0}", ex.getMessage());
        }
    }

}
