package com.andreidemus.http.client;

import com.andreidemus.http.common.Request;
import com.andreidemus.http.common.Response;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;

public class RequestsClient {
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String USER_AGENT = "User-Agent";

    private static final String DEFAULT_USER_AGENT = "Java-Requests/0.0.1";

    public Response get(Request request) {
        return send(request.method("GET"));
    }

    public Response post(Request request) {
        return send(request.method("POST"));
    }

    public Response put(Request request) {
        return send(request.method("PUT"));
    }

    public Response delete(Request request) {
        return send(request.method("DELETE"));
    }

    public Response head(Request request) {
        return send(request.method("HEAD"));
    }

    //TODO CONNECT, OPTIONS, TRACE, PATCH

    public Response send(Request request) {
        try {
            final HttpURLConnection conn = constructRequest(request);
            final Response response = parseResponse(conn);
            conn.disconnect();
            return response;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpURLConnection constructRequest(Request request) throws IOException {
        String urlStr = request.url() + request.path();
        if (request.hasPathParams()) {
            urlStr += "?" + request.pathParamsAsString();
        }
        final URL url = new URL(urlStr);
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod(request.method());

        request.headers().forEach((key, vals) -> {
            vals.forEach(val -> conn.addRequestProperty(key, val));
        });
        if (!request.headers().containsKey(USER_AGENT)) {
            conn.addRequestProperty(USER_AGENT, DEFAULT_USER_AGENT);
        }

        if (request.hasBody()) {
            if (!request.headers().containsKey(CONTENT_TYPE)) {
                conn.addRequestProperty(CONTENT_TYPE, "text/plain; " + request.charset().name());
            }
            final byte[] bytes = request.body();
            conn.addRequestProperty(CONTENT_LENGTH, String.valueOf(bytes.length));
            writeRequestBody(conn, bytes);
        } else if (request.hasFormParams()) {
            if (!request.headers().containsKey(CONTENT_TYPE)) {
                conn.addRequestProperty(CONTENT_TYPE, "application/x-www-form-urlencoded");
            }
            final byte[] bytes = request.formParamsAsString().getBytes(request.charset());
            conn.addRequestProperty(CONTENT_LENGTH, String.valueOf(bytes.length));
            writeRequestBody(conn, bytes);
        }

        return conn;
    }

    private void writeRequestBody(HttpURLConnection conn, byte[] bytes) throws IOException {
        conn.setDoOutput(true);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }
    }

    private Response parseResponse(HttpURLConnection conn) throws IOException {
        final int status = conn.getResponseCode();
        final String reason = conn.getResponseMessage();
        final byte[] body = readBody(conn);

        //conn.getHeaderFields() returns incorrect headers ("Header : value" lines are not parsed correctly)
        //TODO replace HttpURLConnection
        return new Response(status, reason, body, conn.getHeaderFields());
    }

    private byte[] readBody(HttpURLConnection conn) {
        try (InputStream in = getInputStream(conn)) {
            if (in == null) {
                return new byte[]{};
            }
            byte[] bytes = new byte[in.available()];
            in.read(bytes);
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream getInputStream(HttpURLConnection conn) {
        try {
            if (conn.getResponseCode() < HTTP_BAD_REQUEST) {
                return conn.getInputStream();
            } else {
                return conn.getErrorStream();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
