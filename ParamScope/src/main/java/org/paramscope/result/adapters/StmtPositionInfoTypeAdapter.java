package org.paramscope.result.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import sootup.core.jimple.basic.StmtPositionInfo;

import java.io.IOException;

public class StmtPositionInfoTypeAdapter extends TypeAdapter<StmtPositionInfo> {
    @Override
    public void write(JsonWriter out, StmtPositionInfo value) throws IOException {
        out.value(value.toString());
    }

    @Override
    public StmtPositionInfo read(JsonReader in) throws IOException {
        return null;
    }
}
