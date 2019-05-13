/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package luaserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author azalac
 */
public class HTTPHelpers {

    public static enum HTTPStatusCode {
        CONTINUE(100),
        SWITCHING_PROTOCOLS(101),
        PROCESSING(102),
        OK(200),
        CREATED(201),
        ACCEPTED(202),
        NON_AUTHORITATIVE_INFO(203),
        NO_CONTENT(204),
        RESET_CONTENT(205),
        PARTIAL_CONTENT(206),
        MULTI_STATUS(207),
        ALREADY_REPORTED(208),
        IM_USED(226),
        MULTIPLE_CHOICES(300),
        MOVED_PERMANENTLY(301),
        FOUND(302),
        SEE_OTHER(303),
        NOT_MODIFIED(304),
        USE_PROXY(305),
        TEMPORARY_REDIRECT(307),
        PERMANENT_REDIRECT(308),
        BAD_REQUEST(400),
        UNAUTHORIZED(401),
        PAYMENT_REQUIRED(402),
        FORBIDDEN(403),
        NOT_FOUND(404),
        METHOD_NOT_ALLOWED(405),
        NOT_ACCEPTABLE(406),
        PROXY_AUTH_REQUIRED(407),
        REQUEST_TIMEOUT(408),
        CONFLICT(409),
        GONE(410),
        LENGTH_REQUIRED(411),
        PRECONDITION_FAILED(412),
        PAYLOAD_TOO_LARGE(413),
        REQUEST_URI_TOO_LONG(414),
        UNSUPPORTED_MEDIA_TYPE(415),
        REQUESTED_RANGE_NOT_SATISFIABLE(416),
        EXPECTATION_FAILED(417),
        IM_A_TEAPOT(418),
        MISDIRECTED_REQUEST(421),
        UNPROCESSABLE_ENTITY(422),
        LOCKED(423),
        FAILED_DEPENDANCY(424),
        TOO_EARLY(425),
        UPGRADE_REQUIRED(426),
        PRECONDITION_REQUIRED(428),
        TOO_MANY_REQUESTS(429),
        REQUEST_HEADER_FIELDS_TOO_LARGE(431),
        INTERNAL_SERVER_ERROR(500),
        NOT_IMPLEMENTED(501),
        BAD_GATEWAY(502),
        SERVICE_UNAVAILABLE(503),
        GATEWAY_TIMEOUT(504),
        HTTP_VERSION_NOT_SUPPORTED(505),
        VARIANT_ALSO_NEGOTIATES(506),
        INSUFFICIENT_STORAGE(507),
        LOOP_DETECTED(508),
        NOT_EXTENDED(510),
        NETWORK_AUTH_REQUIRED(511);

        private final static HashMap<Integer, HTTPStatusCode> statuses = new HashMap<>();

        private final int code;

        static {
            for (HTTPStatusCode status : HTTPStatusCode.values()) {
                statuses.put(status.code, status);
            }
        }

        private HTTPStatusCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static HTTPStatusCode getStatusByCode(int code) {
            return statuses.getOrDefault(code, null);
        }

    }

    public static class HTTPRequest {

        private final String method;
        private final String request;
        private final String version;
        private final String content;

        private final HashMap<String, String> headers;

        private HashMap<String, String> querydata;

        public HTTPRequest(String method, String request, String version, HashMap<String, String> headers, String content) {
            this.method = method;
            this.request = request;
            this.version = version;
            this.headers = headers;
            this.content = content;
        }

        public Map<String, String> getHeaders() {
            return Collections.unmodifiableMap(headers);
        }
        
        public String getHeader(String key) {
            return headers.getOrDefault(key, null);
        }

        public String getMethod() {
            return method;
        }

        public String getResource() {
            int index = request.indexOf('?');
            
            return index == -1 ? request : request.substring(0, index);
        }
        
        public String getRequest() {
            return request;
        }

        public String getQueryString() {
            int index = request.indexOf('?');

            if (index == -1) {
                return "";
            }

            return request.substring(index + 1);
        }

        public Map<String, String> getQueryData() {
            if (querydata != null) {
                return Collections.unmodifiableMap(querydata);
            }

            querydata = new HashMap<>();
            
            for (String chunk : getQueryString().split("&")) {
                int i = chunk.indexOf('=');
                querydata.put(chunk.substring(0, i == -1 ? chunk.length() : i), i == -1 ? "" : chunk.substring(i + 1));
            }

            return querydata;
        }
        
        public String getQueryValue(String key) {
            return getQueryData().getOrDefault(key, null);
        }

        public String getVersion() {
            return version;
        }

        public String getContent() {
            return content;
        }

    }

    public static class HTTPResponse {

        private HTTPStatusCode status;
        private String reason;

        private final HashMap<String, String> headers = new HashMap<>();
        private String content;

        public HTTPResponse() {
            this(HTTPStatusCode.INTERNAL_SERVER_ERROR, HTTPStatusCode.INTERNAL_SERVER_ERROR.name(), null);
        }

        public HTTPResponse(HTTPStatusCode status) {
            this(status, status.name(), null);
        }

        public HTTPResponse(HTTPStatusCode status, String content) {
            this(status, status.name(), content);
        }

        public HTTPResponse(HTTPStatusCode status, String reason, String content) {
            this.status = status;
            this.reason = reason;
            this.content = content;
        }

        public void setStatus(int code) {
            status = HTTPStatusCode.getStatusByCode(code);
        }

        public void setStatus(HTTPStatusCode status) {
            this.status = status;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public void setContent(String content) {
            this.content = content;
            headers.put("Content-Length", Integer.toString(content.length()));
        }

        public void setHeader(String name, Object value) {
            headers.put(name, value.toString());
        }

        public String getHeader(String name) {
            return headers.getOrDefault(name, null);
        }
        
        public String getLines() {

            StringBuilder response = new StringBuilder();

            response.append("HTTP/1.1 ").append(status.getCode()).append(" ")
                    .append(reason == null ? this.status.name() : reason).append("\r\n");

            headers.entrySet().forEach((header) -> {
                response.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            });

            response.append("\r\n");

            if (content != null) {
                response.append(content);
            }

            return response.toString();
        }

    }

}
