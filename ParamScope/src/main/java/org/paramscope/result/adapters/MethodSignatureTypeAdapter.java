package org.paramscope.result.adapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import sootup.core.signatures.MethodSignature;

import java.io.IOException;

public class MethodSignatureTypeAdapter extends TypeAdapter<MethodSignature> {
    @Override
    public void write(JsonWriter out, MethodSignature value) throws IOException {
        out.value(value.toString());
    }

    @Override
    public MethodSignature read(JsonReader in) throws IOException {
        return null;
    }
}
