package org.paramscope.slice;

import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.Expr;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JFieldRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.ref.JStaticFieldRef;
import sootup.core.jimple.common.stmt.AbstractDefinitionStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.signatures.MethodSignature;
import sootup.java.core.jimple.basic.JavaLocal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

// 封装类“关注的值”
public class FocusedValues {
    // 关注的 “临时变量”
    private final List<MethodJavaLocal> focusedLocals;
    // 关注的 “静态字段“
    private final List<JStaticFieldRef> focusedStaticFields;
    // 关注的 ”动态字段“
    private final List<JInstanceFieldRef> focusedInstanceFields;
    // 关注的 ”数组引用”
    private final List<JArrayRef> focusedArrayRefs;

    // INFO: StaticField can be defined when init or defined anywhere else, use a tracker to track it.
    private final HashMap<JStaticFieldRef, StaticFieldRefTracker> staticFieldRefTrackers;

    // Focused Values are often used in a context, where the method signature is also needed
    private final MethodSignature methodSignature;

    // a patch used to record randomized locals.
    private final List<Local> secureRandomizedArrays;
    private final List<Local> insecureRandomizedArrays;

    public FocusedValues(
            List<MethodJavaLocal> focusedLocals,
            List<JStaticFieldRef> focusedStaticFields,
            List<JInstanceFieldRef> focusedInstanceFields,
            List<JArrayRef> focusedArrayRefs,
            MethodSignature methodSignature
    ) {
        this.focusedLocals = new ArrayList<>(focusedLocals);
        this.focusedStaticFields = new ArrayList<>(focusedStaticFields);
        this.focusedInstanceFields = new ArrayList<>(focusedInstanceFields);
        this.focusedArrayRefs = new ArrayList<>(focusedArrayRefs);
        this.staticFieldRefTrackers = new HashMap<>();
        this.methodSignature = methodSignature;
        this.secureRandomizedArrays = new ArrayList<>();
        this.insecureRandomizedArrays = new ArrayList<>();
    }

    public FocusedValues(FocusedValues values, MethodSignature methodSignature) {
        this.focusedLocals = new ArrayList<>(values.getFocusedLocals());
        this.focusedStaticFields = new ArrayList<>(values.getFocusedStaticFields());
        this.focusedInstanceFields = new ArrayList<>(values.getFocusedInstanceFields());
        this.focusedArrayRefs = new ArrayList<>(values.getFocusedArrayRefs());
        this.staticFieldRefTrackers = new HashMap<>(values.getStaticFieldRefTrackers());
        this.methodSignature = methodSignature;
        this.secureRandomizedArrays = new ArrayList<>();
        this.insecureRandomizedArrays = new ArrayList<>();
    }

    public List<MethodJavaLocal> getFocusedLocals() {
        return focusedLocals;
    }

    public List<JStaticFieldRef> getFocusedStaticFields() {
        return focusedStaticFields;
    }

    public List<JInstanceFieldRef> getFocusedInstanceFields() {
        return focusedInstanceFields;
    }

    public List<JArrayRef> getFocusedArrayRefs() {
        return focusedArrayRefs;
    }

    public HashMap<JStaticFieldRef, StaticFieldRefTracker> getStaticFieldRefTrackers() {
        return staticFieldRefTrackers;
    }

    public List<Local> getSecureRandomizedArrays() {
        return secureRandomizedArrays;
    }

    public List<Local> getInsecureRandomizedArrays() {
        return insecureRandomizedArrays;
    }

    public void addAllFocusedLocals(List<MethodJavaLocal> locals) {
        for (MethodJavaLocal local : locals) {
            if (!this.focusedLocals.contains(local)) {
                this.focusedLocals.add(local);
            }
        }
    }

    public void addAllFocusedStaticFields(List<JStaticFieldRef> staticFields) {
        for (JStaticFieldRef staticFieldRef : staticFields) {
            if (!this.contains(staticFieldRef)) {
                this.add(staticFieldRef);
            }
        }
    }

    public void addAllFocusedInstanceFields(List<JInstanceFieldRef> instanceFields) {
        for (JInstanceFieldRef instanceFieldRef : instanceFields) {
            if (!this.contains(instanceFieldRef)) {
                this.add(instanceFieldRef);
            }
        }
    }

    public void addAllFocusedArrayRefs(List<JArrayRef> arrayRefs) {
        for (JArrayRef arrayRef : arrayRefs) {
            if (!this.contains(arrayRef)) {
                this.add(arrayRef);
            }
        }
    }

    public void putAllStaticFieldTrackers(HashMap<JStaticFieldRef, StaticFieldRefTracker> staticFieldRefTrackers) {
        this.staticFieldRefTrackers.putAll(staticFieldRefTrackers);
    }

    public void add(JavaLocal local) {
        focusedLocals.add(new MethodJavaLocal(local, methodSignature));
    }

    public void add(JStaticFieldRef staticFieldRef) {
        if (!this.contains(staticFieldRef)) {
            focusedStaticFields.add(staticFieldRef);
            StaticFieldRefTracker SFtacker = new StaticFieldRefTracker(staticFieldRef);
            staticFieldRefTrackers.put(staticFieldRef, SFtacker);
        }
    }

    public void add(JInstanceFieldRef instanceFieldRef) {
        // TODO: When def is JInstanceFieldRef, check getBase(). Consider if we need to track Base or JInstanceFieldRef
        focusedInstanceFields.add(instanceFieldRef);
    }

    public void add(JArrayRef arrayRef) {
        // TODO: When def is JArrayRef, check getBase(). Consider if we need to track Base or ArrayRef
        // And check if ArrayRef.getBase() is ArrayRef recursively
        focusedArrayRefs.add(arrayRef);
    }

    public void add(LValue lvalue) {
        if (lvalue instanceof JavaLocal local) {
            add(local);
        } else if (lvalue instanceof JStaticFieldRef staticFieldRef) {
            add(staticFieldRef);
        } else if (lvalue instanceof JInstanceFieldRef instanceFieldRef) {
            add(instanceFieldRef);
        } else if (lvalue instanceof JArrayRef arrayRef) {
            add(arrayRef);
        }
    }

    public void addAll(FocusedValues values) {
        addAllFocusedLocals(values.getFocusedLocals());
        addAllFocusedStaticFields(values.getFocusedStaticFields());
        addAllFocusedInstanceFields(values.getFocusedInstanceFields());
        addAllFocusedArrayRefs(values.getFocusedArrayRefs());
        putAllStaticFieldTrackers(values.getStaticFieldRefTrackers());
    }

    public void addAll(List<?> values) {
        for (Object value : values) {
            if (!this.contains((Value) value)) {
                if (value instanceof JavaLocal local) {
                    add(local);
                } else if (value instanceof JStaticFieldRef staticFieldRef) {
                    add(staticFieldRef);
                } else if (value instanceof JInstanceFieldRef instanceFieldRef) {
                    add(instanceFieldRef);
                } else if (value instanceof JArrayRef arrayRef) {
                    add(arrayRef);
                }
            }
        }
    }

    public void addAllFromStmtUses(Stmt stmt) {
//        addAll(
//                stmt.getUses()
//                        .filter(value -> value instanceof JavaLocal || value instanceof JFieldRef || value instanceof JArrayRef)
//                        .toList()
//        );
        if (stmt.containsInvokeExpr()) {
            if (stmt.getInvokeExpr() instanceof AbstractInstanceInvokeExpr instanceInvokeExpr) {
                add(instanceInvokeExpr.getBase());
            }
            for (Immediate arg : stmt.getInvokeExpr().getArgs()) {
                if (arg instanceof Local local) {
                    add(local);
                }
            }
        } else if (stmt instanceof AbstractDefinitionStmt definitionStmt) {
            if (definitionStmt.getRightOp() instanceof JFieldRef fieldRef) {
                add(fieldRef);
            }
            if (definitionStmt.getRightOp() instanceof JArrayRef arrayRef) {
                add(arrayRef);
            }
            if (definitionStmt.getRightOp() instanceof Local local) {
                add(local);
            }
            if ((definitionStmt.getRightOp() instanceof Expr) && !stmt.containsInvokeExpr()) {
                addAll(
                        stmt.getUses()
                                .filter(value -> value instanceof JavaLocal || value instanceof JFieldRef || value instanceof JArrayRef)
                                .toList()
                );
            }
        }
    }

    public void remove(JavaLocal local) {
        focusedLocals.remove(new MethodJavaLocal(local, methodSignature));
    }

    public void remove(JStaticFieldRef staticFieldRef) {
        focusedStaticFields.remove(getEquivSFVal(staticFieldRef));
    }

    public void remove(JInstanceFieldRef instanceFieldRef) {
        focusedInstanceFields.remove(getEquivIFVal(instanceFieldRef));
    }

    public void remove(JArrayRef arrayRef) {
        focusedArrayRefs.remove(getEquivArrayRef(arrayRef));
    }

    public void remove(LValue lvalue) {
        if (lvalue instanceof JavaLocal local) {
            remove(local);
        } else if (lvalue instanceof JStaticFieldRef staticFieldRef) {
            remove(staticFieldRef);
        } else if (lvalue instanceof JInstanceFieldRef instanceFieldRef) {
            remove(instanceFieldRef);
        } else if (lvalue instanceof JArrayRef arrayRef) {
            remove(arrayRef);
        }
    }

    public void removeAll(FocusedValues values) {
        focusedLocals.removeAll(values.getFocusedLocals());
        for (JStaticFieldRef staticFieldRef : values.getFocusedStaticFields()) {
            remove(staticFieldRef);
        }
        for (JInstanceFieldRef instanceFieldRef : values.getFocusedInstanceFields()) {
            remove(instanceFieldRef);
        }
        for (JArrayRef arrayRef : values.getFocusedArrayRefs()) {
            remove(arrayRef);
        }
    }

    public void removeAll(List<?> values) {
        for (Object value : values) {
            if (this.contains((Value) value)) {
                if (value instanceof JavaLocal local) {
                    remove(local);
                } else if (value instanceof JStaticFieldRef staticFieldRef) {
                    remove(staticFieldRef);
                } else if (value instanceof JInstanceFieldRef instanceFieldRef) {
                    remove(instanceFieldRef);
                } else if (value instanceof JArrayRef arrayRef) {
                    remove(arrayRef);
                }
            }
        }
    }

    public List<Value> allValues() {
        ArrayList<Value> values = new ArrayList<>();
        values.addAll(focusedLocals.stream().map(MethodJavaLocal::javaLocal).toList());
        values.addAll(focusedStaticFields);
        values.addAll(focusedInstanceFields);
        values.addAll(focusedArrayRefs);
        return values;
    }

    public boolean isEmpty() {
        return focusedLocals.isEmpty()
                && focusedStaticFields.isEmpty()
                && focusedInstanceFields.isEmpty()
                && focusedArrayRefs.isEmpty();
    }

    public boolean contains(Value value) {
        if (value instanceof JavaLocal local) {
            if (methodSignature != null) {
                boolean localsContains = focusedLocals.contains(new MethodJavaLocal(local, methodSignature));
                boolean IFContains = false;
                for (JInstanceFieldRef IFvalue : focusedInstanceFields) {
                    if (IFvalue.getBase().equivTo(local)) {
                        IFContains = true;
                        break;
                    }
                }
                return localsContains || IFContains;
            } else {
                boolean localsContains = focusedLocals.stream().map(MethodJavaLocal::javaLocal).toList().contains(local);
                boolean IFContains = false;
                for (JInstanceFieldRef IFvalue : focusedInstanceFields) {
                    if (IFvalue.getBase().equivTo(local)) {
                        IFContains = true;
                        break;
                    }
                }
                return localsContains || IFContains;
            }
        } else if (value instanceof JStaticFieldRef SFvalue) {
            // INFO:  "@2270 <xxxClass field1>" and "@2279 <xxxClass field1>" are different
            for (JStaticFieldRef staticFieldRef : focusedStaticFields) {
                if (staticFieldRef.equivTo(SFvalue)) {
                    return true;
                }
            }
        } else if (value instanceof JInstanceFieldRef IFvalue) {
            for (JInstanceFieldRef instanceFieldRef : focusedInstanceFields) {
                if (instanceFieldRef.equivTo(IFvalue)) {
                    return true;
                }
            }
        } else if (value instanceof JArrayRef ARvalue) {
            for (JArrayRef arrayRef : focusedArrayRefs) {
                if (arrayRef.equivTo(ARvalue)) {
                    return true;
                }
            }
            return contains(ARvalue.getBase());
        }
        return false;
    }

    public boolean onlyContainsStaticFieldRef() {
        return isEmptyLocal() && !isEmptyStaticField() && isEmptyInstanceField() && isEmptyArrayRef();
    }

    private JStaticFieldRef getEquivSFVal(JStaticFieldRef SFvalue) {
        for (JStaticFieldRef SFref : focusedStaticFields) {
            if (SFref.equivTo(SFvalue)) {
                return SFref;
            }
        }
        return null;
    }

    private JInstanceFieldRef getEquivIFVal(JInstanceFieldRef IFvalue) {
        for (JInstanceFieldRef IFref : focusedInstanceFields) {
            if (IFref.equivTo(IFvalue)) {
                return IFref;
            }
        }
        return null;
    }

    private JArrayRef getEquivArrayRef(JArrayRef ARvalue) {
        for (JArrayRef ARref : focusedArrayRefs) {
            if (ARref.equivTo(ARvalue)) {
                return ARref;
            }
        }
        return null;
    }

    public boolean isEmptyLocal() {
        return focusedLocals.isEmpty();
    }

    public boolean isEmptyStaticField() {
        return focusedStaticFields.isEmpty();
    }

    public boolean isEmptyInstanceField() {
        return focusedInstanceFields.isEmpty();
    }

    public boolean isEmptyArrayRef() {
        return focusedArrayRefs.isEmpty();
    }

}
