package org.paramscope.data;

import org.paramscope.api.ModifyBaseVirtualInvokeMethod;

import java.util.HashSet;
import java.util.Set;

public class ModifyBaseVirtualInvokeMethodSet {
    private static final Set<ModifyBaseVirtualInvokeMethod> MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET = new HashSet<>();

    // This is a method set which will modify BaseVar when invoke method.
    // temporarily record these, we will work on realizing an analysis rather than this set.
    static {
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "append"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "delete"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "deleteCharAt"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "insert"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "replace"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "reverse"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "setCharAt"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuilder", "setLength"));

        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "append"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "delete"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "deleteCharAt"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "insert"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "replace"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "reverse"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "setCharAt"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.lang.StringBuffer", "setLength"));

        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.List", "add"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.List", "addAll"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.List", "clear"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.List", "remove"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.List", "removeAll"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.List", "set"));

        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.ArrayList", "ensureCapacity"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.ArrayList", "removeIf"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.ArrayList", "removeRange"));

        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "addFirst"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "addLast"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "offer"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "offerFirst"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "offerLast"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "poll"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "pollFirst"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "pollLast"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "pop"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "push"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "removeFirst"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "removeFirstOccurrence"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "removeLast"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.LinkedList", "removeLastOccurrence"));

        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "addElement"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "ensureCapacity"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "insertElementAt"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "removeAllElements"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "removeElement"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "removeElementAt"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "removeIf"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "removeRange"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "replaceAll"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "retainAll"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "setElementAt"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Vector", "trimToSize"));

        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Map", "clear"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Map", "put"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Map", "putAll"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.Map", "remove"));

        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.TreeMap", "pollFirstEntry"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("java.util.TreeMap", "pollLastEntry"));

        // Project motivation examples (treat setters as base-modifying to keep them in slices for replay)
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("motivationExample2$Config", "setA"));
        MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET.add(new ModifyBaseVirtualInvokeMethod("motivationExample2$Config", "setF"));
    }

    public static Set<ModifyBaseVirtualInvokeMethod> getSet() {
        return MODIFY_BASE_VIRTUAL_INVOKE_METHOD_SET;
    }
}
