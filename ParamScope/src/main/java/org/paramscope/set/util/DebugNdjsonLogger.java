package org.paramscope.set.util;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

public final class DebugNdjsonLogger {
    // Always write to the workspace-root logfile, regardless of process cwd.
    private static final String LOG_PATH = "C:\\Users\\Erio\\Desktop\\ParamScopeRun\\paramscope-SymbolicExecution\\paramscope\\debug-69dd00.log";

    private DebugNdjsonLogger() {}

    // #region agent log
    public static void log(String runId, String hypothesisId, String location, String message, Map<String, Object> data) {
        long ts = Instant.now().toEpochMilli();
        String json = toJson(runId, hypothesisId, location, message, data, ts);
        try (FileWriter w = new FileWriter(LOG_PATH, true)) {
            w.write(json);
            w.write("\n");
        } catch (IOException ignored) {
        }
    }
    // #endregion

    private static String toJson(String runId, String hypothesisId, String location, String message,
                                 Map<String, Object> data, long timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"sessionId\":\"").append(escape("69dd00")).append("\",");
        sb.append("\"runId\":\"").append(escape(runId)).append("\",");
        sb.append("\"hypothesisId\":\"").append(escape(hypothesisId)).append("\",");
        sb.append("\"location\":\"").append(escape(location)).append("\",");
        sb.append("\"message\":\"").append(escape(message)).append("\",");
        sb.append("\"timestamp\":").append(timestamp).append(",");
        sb.append("\"data\":").append(mapToJson(data));
        sb.append("}");
        return sb.toString();
    }

    private static String mapToJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (var e : data.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("\"").append(escape(String.valueOf(v))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}

