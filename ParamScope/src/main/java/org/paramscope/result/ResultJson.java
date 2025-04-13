package org.paramscope.result;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;

public class ResultJson {
    @Expose
    ArrayList<ResultEntry> results;

    public ResultJson() {
        results = new ArrayList<>();
    }

    public void addResult(ResultEntry result) {
        results.add(result);
    }
}
