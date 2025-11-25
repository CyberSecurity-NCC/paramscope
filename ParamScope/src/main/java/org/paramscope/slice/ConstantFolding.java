package org.paramscope.slice;

import org.paramscope.reflection.ConstantResolve;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.model.SootMethod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstantFolding {

    private SootMethod sootMethod;
    private Map<Value, Object> constantENV;
    private StmtGraph graph;
    private List<JIfStmt> ifStmts;
    private List<JSwitchStmt> switchStmts;

    private List<Stmt> filteredStmts;
    private List<Stmt> removeStmts;

    public ConstantFolding(){
        this.constantENV = new HashMap<>();
    }

    public ConstantFolding(SootMethod sootMethod) {
        this.constantENV = new HashMap<>();
        this.sootMethod = sootMethod;
        this.graph = sootMethod.getBody().getStmtGraph();
        this.ifStmts = new ArrayList<>();
        this.switchStmts = new ArrayList<>();
        this.filteredStmts = new ArrayList<>();
        this.removeStmts = new ArrayList<>();
        foldAssign();
        startAnalysis();
        filterStmts();
    }

    private void startAnalysis(){
        for(Stmt stmt: sootMethod.getBody().getStmts()){
            if(stmt instanceof JIfStmt ifStmt){
                ifStmts.add(ifStmt);
            }
            if(stmt instanceof JSwitchStmt switchStmt){
                switchStmts.add(switchStmt);
            }
        }

        for(JIfStmt ifStmt: ifStmts){
            Stmt realPathHead = (Stmt) graph.successors(ifStmt).get(evaluateIf(ifStmt)? 0: 1);
            removeStmts.addAll(graph.getBlockOf(realPathHead).getStmts());
        }

    }

    private boolean evaluateIf(JIfStmt ifStmt){
        AbstractConditionExpr expr = ifStmt.getCondition();
        if (expr instanceof JLeExpr leExpr){
            return evaluateValue(leExpr.getOp1()) <= evaluateValue(leExpr.getOp2());
        }
        if (expr instanceof JGeExpr geExpr){
            return evaluateValue(geExpr.getOp1()) >= evaluateValue(geExpr.getOp2());
        }
        if (expr instanceof JGtExpr gtExpr){
            return evaluateValue(gtExpr.getOp1()) > evaluateValue(gtExpr.getOp2());
        }
        if (expr instanceof JLtExpr ltExpr){
            return evaluateValue(ltExpr.getOp1()) < evaluateValue(ltExpr.getOp2());
        }
        if (expr instanceof JEqExpr eqExpr){
            return evaluateValue(eqExpr.getOp1()) == evaluateValue(eqExpr.getOp2());
        }
        if (expr instanceof JNeExpr neExpr){
            return evaluateValue(neExpr.getOp1()) != evaluateValue(neExpr.getOp2());
        }

        return true;
    }

    private int evaluateValue(Immediate immediate){
        if(immediate instanceof Local local){
            if (constantENV.get(local) instanceof Integer integer){
                return integer;
            }
            return 0;
        } else if(immediate instanceof Constant constant){
            if(ConstantResolve.resolve(constant) instanceof Integer integer){
                return integer;
            }
            return 0;
        }
        return 0;
    }

    private void foldAssign(){
        for(Stmt stmt: sootMethod.getBody().getStmts()){
            if(stmt instanceof JAssignStmt assignStmt){
                Value leftOp = assignStmt.getLeftOp();
                Value rightOp = assignStmt.getRightOp();
                if(leftOp instanceof Local local){
                    if(rightOp instanceof Constant constant){
                        Object value = ConstantResolve.resolve(constant);
                        if(value != null){
                            constantENV.put(local, value);
                        }
                    } else if(rightOp instanceof Local rightLocal){
                        if(constantENV.get(rightLocal) != null){
                            constantENV.put(local, constantENV.get(rightLocal));
                        }
                    } else {
                        constantENV.remove(local);
                    }
                }
            }
        }
    }

    private void filterStmts(){
        for(Stmt stmt: sootMethod.getBody().getStmts()){
            if(removeStmts.contains(stmt)){
                continue;
            }
            filteredStmts.add(stmt);
        }
    }

    public List<Stmt> getFilteredStmts() {
        return filteredStmts;
    }
}
