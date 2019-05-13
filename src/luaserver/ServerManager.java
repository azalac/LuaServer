/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package luaserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import luaserver.HTTPHelpers.HTTPRequest;
import luaserver.HTTPHelpers.HTTPResponse;
import luaserver.HTTPHelpers.HTTPStatusCode;

/**
 * For some reason, {@link com.sun.net.httpserver.HttpServer} doesn't work
 * nicely, so I had to make my own http server implementation. Hopefully it's
 * faster.
 *
 * @author azalac
 */
public class ServerManager implements AutoCloseable, Runnable {

    private static final int DEFAULT_BACKLOG = 10;

    private ServerSocket socket;
    private Thread current_thread;

    private static final Pattern HTTP_REQUEST_PATTERN
            = Pattern.compile("^(?<method>\\w+)\\s+(?<request>[A-Za-z!#-;=?@\\[\\]_~]+)\\s+(?<version>\\w+\\/\\d+\\.\\d+)$");

    private final HashMap<String, LuaEndpoint> endpoints = new HashMap<>();

    public ServerManager() throws IOException {
        this("127.0.0.1", 80);
    }

    public ServerManager(int port) throws IOException {
        this("127.0.0.1", port);
    }

    public ServerManager(String address, int port) throws IOException {
        this(new ServerSocket(port, DEFAULT_BACKLOG, InetAddress.getByName(address)));
    }

    public ServerManager(ServerSocket socket) {
        this.socket = socket;
    }

    public void start() {
        current_thread = new Thread(this, "LuaServer");

        current_thread.start();
    }

    public void stop() {
        current_thread.interrupt();
        current_thread = null;
    }

    public void addEndpoint(LuaEndpoint endpoint) {
        synchronized (endpoints) {
            endpoints.put(endpoint.getResourceName(), endpoint);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public void run() {

        while (!Thread.interrupted()) {

            try (
                    Socket client = socket.accept();
                    InputStream in = client.getInputStream();
                    OutputStream out = client.getOutputStream();
                    PrintStream ps = new PrintStream(out);) {

                Logger.getLogger(ServerManager.class.getName()).log(Level.INFO,
                        "Accepted connection from {0}", client.getRemoteSocketAddress());

                long pre = System.nanoTime();
                
                String response = handleRequest(in).getLines();

                long delta = System.nanoTime() - pre;
                
                ps.print(response);

                Logger.getLogger(ServerManager.class.getName()).log(Level.INFO,
                        "Finished in {0} seconds", delta / 1e9);

            } catch (IOException ex) {
                Logger.getLogger(ServerManager.class.getName()).log(Level.SEVERE, "Could not connect to client", ex);
            }

        }

    }

    private HTTPResponse handleRequest(InputStream in) throws IOException {

        HTTPRequest request;

        try {
            request = parseRequest(in);
        } catch (InvalidHTTPException ex) {
            return new HTTPResponse(ex.status, ex.getMessage());
        }

        return handleRequest(request);
    }

    private HTTPResponse handleRequest(HTTPRequest request) {
        LuaEndpoint endpoint = endpoints.getOrDefault(request.getResource(), null);

        if (endpoint != null) {
            try {
                return endpoint.HandleRequest(request);
            } catch (EndpointRedirectException redirect) {
                return handleRequest(redirect.redirect);
            }
        } else {
            return new HTTPResponse(HTTPStatusCode.NOT_FOUND, "Could not find resource " + request.getRequest());
        }
    }

    private static HTTPRequest parseRequest(InputStream input) throws IOException, InvalidHTTPException {

        BufferedReader reader = new BufferedReader(new InputStreamReader(input));

        String line = reader.readLine();
        
        if(line == null) {
            throw new InvalidHTTPException(HTTPStatusCode.BAD_REQUEST, "Invalid HTTP Request Line");
        }
        
        Matcher version_line = HTTP_REQUEST_PATTERN.matcher(line);

        if (!version_line.matches()) {
            throw new InvalidHTTPException(HTTPStatusCode.BAD_REQUEST, "Invalid HTTP Request Line");
        }

        HashMap<String, String> headers = new HashMap<>();
        StringBuilder content = new StringBuilder();

        while ((line = reader.readLine()) != null) {

            if (line.trim().isEmpty()) {
                break;
            }

            int index = line.indexOf(':');

            // no colon, but line is not CRLF, invalid line
            if (index == -1) {
                throw new InvalidHTTPException(HTTPStatusCode.BAD_REQUEST, "Invalid header line '" + line + "', must contain colon");
            }

            headers.put(line.substring(0, index).trim(), line.substring(index + 1).trim());

        }

        if (headers.containsKey("Content-Length")) {
            int length = Integer.parseInt(headers.get("Content-Length"));

            for (int codepoint = 0; codepoint != -1 && length >= 0; codepoint = input.read()) {
                content.appendCodePoint(codepoint);
                length--;
            }
        }

        return new HTTPRequest(version_line.group("method"),
                version_line.group("request"),
                version_line.group("version"),
                headers, content.toString());
    }

    private static class InvalidHTTPException extends Exception {

        private HTTPStatusCode status;

        public InvalidHTTPException(int status, String reason) {
            super(reason);

            this.status = Stream.of(HTTPStatusCode.values())
                    .filter(s -> s.getCode() == status).findFirst()
                    .orElse(HTTPStatusCode.INTERNAL_SERVER_ERROR);
        }

        public InvalidHTTPException(HTTPStatusCode status, String reason) {
            super(reason);
            this.status = status;
        }

        public HTTPStatusCode getStatus() {
            return status;
        }

    }

    public static class EndpointRedirectException extends Error {

        public final HTTPRequest redirect;

        public EndpointRedirectException(HTTPRequest to) {
            super("This exception should have been caught");
            redirect = to;
        }
    }

}
