package org.paramscope.api;

import java.util.Objects;

public class ModifyBaseVirtualInvokeMethod {
    String declClass;
    String name;

    public ModifyBaseVirtualInvokeMethod(String declClass, String name) {
        this.declClass = declClass;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModifyBaseVirtualInvokeMethod that = (ModifyBaseVirtualInvokeMethod) o;
        return Objects.equals(declClass, that.declClass) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declClass, name);
    }
}
