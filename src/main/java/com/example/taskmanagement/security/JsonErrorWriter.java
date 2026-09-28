package com.example.taskmanagement.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Writes errors raised in the filter chain (before any controller runs) in the same
 * {"success":false,"message":...,"data":null} shape the controllers use.
 */
public final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escaped + "\",\"data\":null}");
    }
}
