/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package luaserver;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import luaserver.HTTPHelpers.HTTPRequest;
import luaserver.HTTPHelpers.HTTPResponse;
import luaserver.HTTPHelpers.HTTPStatusCode;
import luaserver.ServerManager.EndpointRedirectException;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

/**
 *
 * @author azalac
 */
public interface LuaEndpoint {

    public String getResourceName();

    public HTTPResponse HandleRequest(HTTPRequest request);

    static class LuaScriptEndpoint implements LuaEndpoint {

        private String name;
        private String MimeType = "application/json";

        private final HashMap<String, LuaFunction> operations = new HashMap<>();

        public LuaScriptEndpoint(LuaTable endpoint) {
            LuaValue lname = endpoint.get("name");

            if (lname.isstring()) {
                name = lname.tojstring();
            } else {
                throw new IllegalArgumentException("Endpoint name must be a string");
            }

            LuaValue lmimetype = endpoint.get("mimetype");

            if (lmimetype.isstring()) {
                MimeType = lmimetype.tojstring();
            } else if (!lmimetype.isnil()) {
                throw new IllegalArgumentException(name + ": mime type must be a string or nil");
            }

            LuaValue handler = endpoint.get("handler"),
                    handlers = endpoint.get("handlers");

            if (handler.isfunction()) {
                operations.put(null, handler.checkfunction());
            } else if (handlers.istable()) {
                LuaTable table = handlers.checktable();
                for (LuaValue key : table.keys()) {
                    operations.put(key.tojstring(), table.get(key).checkfunction());
                }
            } else {
                throw new IllegalArgumentException(name + ": handler or handlers must be declared for scripts");
            }

        }

        @Override
        public String getResourceName() {
            return name;
        }

        @Override
        public HTTPResponse HandleRequest(HTTPRequest request) {
            LuaFunction fn = operations.get(request.getQueryValue("operation"));

            if (fn == null) {
                return new HTTPResponse(HTTPStatusCode.UNAUTHORIZED, "Could not find operation '" + request.getQueryValue("operation") + "'");
            }

            LuaValue[] args = new LuaValue[]{
                fn, CoerceJavaToLua.coerce(request)
            };

            LuaValue ret;
            try {
                ret = fn.invoke(args).arg1();
            } catch (LuaError er) {
                Logger.getLogger(LuaEndpoint.class.toString()).log(Level.SEVERE, "Error during lua execution", er);
                return new HTTPResponse(HTTPStatusCode.INTERNAL_SERVER_ERROR, "Could not execute script");
            }

            LuaValue status = ret.get("status"),
                    reason = ret.get("reason"),
                    headers = ret.get("headers"),
                    content = ret.get("content");

            HTTPResponse response = new HTTPResponse();

            if (!status.isnil() && status.isint()) {
                response.setStatus(status.checkint());
            }

            if (!reason.isnil() && reason.isstring()) {
                response.setReason(reason.tojstring());
            }

            if (!headers.isnil() && headers.istable()) {
                LuaTable table = headers.checktable();
                for (LuaValue key : table.keys()) {
                    response.setHeader(key.tojstring(), table.get(key).tojstring());
                }
            }

            if (!content.isnil()) {
                JsonElement json = LuaToJson(content);

                response.setContent(json.toString());
            }

            // no mime-type specified by script, set it to the default
            if (response.getHeader("Content-Type") == null) {
                response.setHeader("Content-Type", MimeType);
            }

            return response;
        }

    }

    static class ResourceEndpoint implements LuaEndpoint {

        private String name;
        private String MimeType = "application/text";

        private Path file;

        public ResourceEndpoint(LuaTable endpoint) {
            LuaValue lname = endpoint.get("name");

            if (lname.isstring()) {
                name = lname.tojstring();
            } else {
                throw new IllegalArgumentException("Endpoint name must be a string");
            }

            LuaValue lmimetype = endpoint.get("mimetype");

            if (lmimetype.isstring()) {
                MimeType = lmimetype.tojstring();
            } else if (!lmimetype.isnil()) {
                throw new IllegalArgumentException(name + ": mime type must be a string or nil");
            }

            LuaValue lpath = endpoint.get("path");

            if (lpath.isstring()) {
                file = Paths.get(lpath.tojstring());
            } else {
                throw new IllegalArgumentException(name + ": Resource path must be a string");
            }
        }

        @Override
        public String getResourceName() {
            return name;
        }

        @Override
        public HTTPResponse HandleRequest(HTTPRequest request) {
            if (!request.getMethod().equalsIgnoreCase("GET")) {
                return new HTTPResponse(HTTPStatusCode.METHOD_NOT_ALLOWED, "Resources must be gotten with the GET method");
            }

            try {
                byte[] data = Files.readAllBytes(file);

                HTTPResponse response = new HTTPResponse(HTTPStatusCode.OK, new String(data));

                response.setHeader("Content-Type", MimeType);

                return response;
            } catch (IOException ex) {
                return new HTTPResponse(HTTPStatusCode.INTERNAL_SERVER_ERROR, "IOException while getting resource");
            }
        }

    }

    static class AliasEndpoint implements LuaEndpoint {

        private final String name, redirect_name;

        private LuaFunction inputmutator;

        public AliasEndpoint(LuaTable endpoint) {
            LuaValue lname = endpoint.get("name");

            if (lname.isstring()) {
                name = lname.tojstring();
            } else {
                throw new IllegalArgumentException("Endpoint name must be a string");
            }

            LuaValue lredirect = endpoint.get("to");

            if (lredirect.isstring()) {
                redirect_name = lredirect.tojstring();
            } else {
                throw new IllegalArgumentException(name + ": Redirect name must be a string");
            }

            LuaValue linput = endpoint.get("request_mutator");

            if (linput.isfunction()) {
                inputmutator = linput.checkfunction();
            } else if (!linput.isnil()) {
                throw new IllegalArgumentException(name + ": Request Mutator must be a function or nil");
            }

        }

        @Override
        public String getResourceName() {
            return name;
        }

        @Override
        public HTTPResponse HandleRequest(HTTPRequest request) {
            HTTPRequest redirect = new HTTPRequest(request.getMethod(),
                    this.redirect_name + "?" + request.getQueryString(),
                    request.getVersion(), new HashMap<>(request.getHeaders()),
                    request.getContent());

            if (inputmutator != null && !inputmutator.isnil()) {
                redirect = (HTTPRequest) CoerceLuaToJava.coerce(inputmutator.call(CoerceJavaToLua.coerce(redirect)), HTTPRequest.class);
            }

            throw new EndpointRedirectException(redirect);
        }

    }

    public static LuaEndpoint LoadEndpoint(LuaTable endpoint) {
        LuaValue type = endpoint.get("type");

        if (type.isstring()) {
            switch (type.tojstring().toLowerCase().trim()) {
                case "script":
                    return new LuaScriptEndpoint(endpoint);
                case "resource":
                    return new ResourceEndpoint(endpoint);
                case "alias":
                    return new AliasEndpoint(endpoint);
                default:
                    Logger.getLogger(LuaEndpoint.class.toString()).log(Level.SEVERE, "Unknown endpoint type {0}", type.tojstring());
            }
        }
        return null;
    }

    public static JsonElement LuaToJson(LuaValue value) {
        switch (value.type()) {
            case LuaValue.TNONE:
            case LuaValue.TNIL:
            case LuaValue.TUSERDATA:
            case LuaValue.TLIGHTUSERDATA:
            case LuaValue.TTHREAD:
            case LuaValue.TFUNCTION:
                return JsonNull.INSTANCE;
            case LuaValue.TBOOLEAN:
                return new JsonPrimitive(value.checkboolean());
            case LuaValue.TINT:
                return new JsonPrimitive(value.checklong());
            case LuaValue.TNUMBER:
                return new JsonPrimitive(value.checkdouble());
            case LuaValue.TSTRING:
                return new JsonPrimitive(value.checkjstring());
            case LuaValue.TTABLE: {
                JsonObject object = new JsonObject();
                LuaTable tbl = value.checktable();
                for (LuaValue key : tbl.keys()) {
                    object.add(key.tojstring(), LuaToJson(tbl.get(key)));
                }
                return object;
            }
        }

        throw new AssertionError("Unknown lua value type " + value.typename());
    }

}
