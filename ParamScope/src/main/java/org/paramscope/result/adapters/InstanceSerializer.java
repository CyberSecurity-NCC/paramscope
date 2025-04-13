package org.paramscope.result.adapters;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

public class InstanceSerializer implements JsonSerializer<Object> {
    @Override
    public JsonElement serialize(Object src, Type typeOfSrc, JsonSerializationContext context) {
        if (src.getClass().isArray()) {
            StringBuilder arrayString = new StringBuilder("[");
            Object[] array = (Object[]) src;
            for (int i = 0; i < array.length; i++) {
                arrayString.append(array[i].toString());
                if (i < array.length - 1) {
                    arrayString.append(", ");
                }
            }
            arrayString.append("]");
            return new JsonPrimitive(arrayString.toString());
        } else {
            return new JsonPrimitive(src.toString());
        }
    }
}
