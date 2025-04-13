package org.paramscope.api;

import com.google.gson.annotations.Expose;
import sootup.core.signatures.MethodSignature;

import java.util.List;
import java.util.Objects;

public class APIParamInfo {
    @Expose
    private final String classNameString;
    @Expose
    private final String subMethodSignatureString;
    @Expose
    private final List<Integer> paramPosList;
    private final String methodSignatureString;

    @Expose(serialize = false)
    private MethodSignature methodSignature;

    public APIParamInfo(String classNameString, String subMethodSignatureString, List<Integer> paramPosList) {
        this.classNameString = classNameString;
        this.subMethodSignatureString = subMethodSignatureString;
        this.paramPosList = paramPosList;
        this.methodSignatureString = "<" + classNameString + ": " + subMethodSignatureString + ">";
    }

    public String getSubMethodSignatureString() {
        return subMethodSignatureString;
    }

    public String getClassNameString() {
        return classNameString;
    }

    public List<Integer> getParamPosList() {
        return paramPosList;
    }

    public MethodSignature getMethodSignature() {
        return methodSignature;
    }

    public void setMethodSignature(MethodSignature methodSignature) {
        this.methodSignature = methodSignature;
    }

    public String getMethodSignatureString() {
        return methodSignatureString;
    }

    @Override
    public String toString() {
        return "APIParamInfo{" +
                "classNameString='" + classNameString + '\'' +
                ", paramPosList=" + paramPosList +
                ", subMethodSignatureString='" + subMethodSignatureString + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        APIParamInfo that = (APIParamInfo) o;
        return Objects.equals(classNameString, that.classNameString) && Objects.equals(subMethodSignatureString, that.subMethodSignatureString) && Objects.equals(paramPosList, that.paramPosList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classNameString, subMethodSignatureString, paramPosList);
    }
}
