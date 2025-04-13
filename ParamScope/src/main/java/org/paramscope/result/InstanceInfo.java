package org.paramscope.result;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;

public class InstanceInfo {
    @Expose
    Object instance;
    @Expose
    String arrayInfo;
    @Expose
    String nullReason;
    @Expose
    String runningExceptions;
    @Expose
    String securityInfo;

    public InstanceInfo(Object instance) {
        this.instance = instance;
    }

    public void setArrayInfo(String arrayInfo) {
        this.arrayInfo = arrayInfo;
    }

    public void setNullReason(String nullReason) {
        this.nullReason = nullReason;
    }

    public void setSecurityInfo(String securityInfo) {
        this.securityInfo = securityInfo;
    }

    public void setRunningExceptions(ArrayList<String> exceptions) {
        StringBuilder exceptionStringBuilder = new StringBuilder("[");
        for (int i = 0; i < exceptions.size(); i++) {
            if (i == exceptions.size() - 1) {
                exceptionStringBuilder.append(exceptions.get(i));
            } else {
                exceptionStringBuilder.append(exceptions.get(i)).append(", ");
            }
        }
        exceptionStringBuilder.append("]");
        this.runningExceptions = exceptionStringBuilder.toString();
    }

    @Override
    public String toString() {
        StringBuilder arrayStringBuilder = new StringBuilder("[");
        if (instance.getClass().isArray()) {
            Object[] arrayObject = (Object[]) instance;
            for (int i = 0; i < arrayObject.length; i++) {
                arrayStringBuilder.append(arrayObject[i]);
                if (i < arrayObject.length - 1) {
                    arrayStringBuilder.append(", ");
                }
            }
            arrayStringBuilder.append("]");
        }

        return "InstanceInfo{" +
                "instance=" + (instance.getClass().isArray() ? arrayStringBuilder : instance.toString()) +
                ", arrayInfo='" + arrayInfo + '\'' +
                '}';
    }
}
