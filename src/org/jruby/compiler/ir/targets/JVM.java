package org.jruby.compiler.ir.targets;

import com.kenai.constantine.Constant;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyObject;
import org.jruby.compiler.ir.CompilerTarget;
import org.jruby.compiler.ir.IR_Builder;
import org.jruby.compiler.ir.IR_Class;
import org.jruby.compiler.ir.IR_Method;
import org.jruby.compiler.ir.IR_Scope;
import org.jruby.compiler.ir.IR_Script;
import org.jruby.compiler.ir.instructions.BEQ_Instr;
import org.jruby.compiler.ir.instructions.CALL_Instr;
import org.jruby.compiler.ir.instructions.COPY_Instr;
import org.jruby.compiler.ir.instructions.DEFINE_CLASS_METHOD_Instr;
import org.jruby.compiler.ir.instructions.DEFINE_INSTANCE_METHOD_Instr;
import org.jruby.compiler.ir.instructions.GET_FIELD_Instr;
import org.jruby.compiler.ir.instructions.IR_Instr;
import org.jruby.compiler.ir.instructions.JUMP_Instr;
import org.jruby.compiler.ir.instructions.LABEL_Instr;
import org.jruby.compiler.ir.instructions.PUT_FIELD_Instr;
import org.jruby.compiler.ir.instructions.RECV_ARG_Instr;
import org.jruby.compiler.ir.instructions.RETURN_Instr;
import org.jruby.compiler.ir.operands.FieldRef;
import org.jruby.compiler.ir.operands.Fixnum;
import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.operands.Operand;
import org.jruby.compiler.ir.operands.Variable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.TraceClassVisitor;
import static org.objectweb.asm.Opcodes.*;
import static org.jruby.util.CodegenUtils.*;
import static org.objectweb.asm.commons.GeneratorAdapter.*;

// This class represents JVM as the target of compilation
// and outputs bytecode
public class JVM implements CompilerTarget {
    private static final boolean DEBUG = true;
    
    Stack<ClassData> clsStack = new Stack();
    List<ClassData> clsAccum = new ArrayList();
    IR_Script script;

    private static class ClassData {
        public ClassData(ClassVisitor cls) {
            this.cls = cls;
        }

        public GeneratorAdapter method() {
            return methodData().method;
        }

        public MethodData methodData() {
            return methodStack.peek();
        }

        public void pushmethod(String name) {
            methodStack.push(new MethodData(new GeneratorAdapter(
                    ACC_PUBLIC | ACC_STATIC,
                    Method.getMethod("org.jruby.runtime.builtin.IRubyObject " + name + " (org.jruby.runtime.ThreadContext, org.jruby.runtime.builtin.IRubyObject)"),
                    null,
                    null,
                    cls)));
        }

        public void popmethod() {
            method().endMethod();
            methodStack.pop();
        }
        
        public ClassVisitor cls;
        Stack<MethodData> methodStack = new Stack();
        public Set<String> fieldSet = new HashSet<String>();
    }

    private static class MethodData {
        public MethodData(GeneratorAdapter method) {
            this.method = method;
        }
        public GeneratorAdapter method;
        public Map<Variable, Integer> varMap = new HashMap<Variable, Integer>();
        public Map<Label, org.objectweb.asm.Label> labelMap = new HashMap<Label, org.objectweb.asm.Label>();
    }

    public static void main(String[] args) {
        IR_Scope scope = IR_Builder.buildFromMain(args);

        System.out.println("INTERMEDIATE REPRESENTATION:");
        System.out.println(scope);

        JVM jvm = new JVM();
        System.out.println("\nGENERATED BYTECODE:");
        jvm.codegen(scope);
    }

    public JVM() {
    }

    public ClassVisitor cls() {
        return clsData().cls;
    }

    public ClassData clsData() {
        return clsStack.peek();
    }

    public void pushclass() {
        if (DEBUG) {
            PrintWriter pw = new PrintWriter(System.out);
            clsStack.push(new ClassData(new TraceClassVisitor(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS), pw)));
            pw.flush();
        } else {
            clsStack.push(new ClassData(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS)));
        }
    }

    public void popclass() {
        clsStack.pop();
    }

    public GeneratorAdapter method() {
        return clsData().method();
    }

    public void pushmethod(String name) {
        clsData().pushmethod(name);
    }

    public void popmethod() {
        clsData().popmethod();
    }

    public void codegen(IR_Scope scope) {
        if (scope instanceof IR_Script) {
            codegen((IR_Script)scope);
        }
    }

    public void codegen(IR_Script script) {
        this.script = script;
        emit(script.getRootClass());
    }

    public void emit(IR_Class cls) {
        pushclass();
        cls().visit(RubyInstanceConfig.JAVA_VERSION, ACC_PUBLIC + ACC_SUPER, cls._name, null, p(RubyObject.class), null);
        cls().visitSource(script.getFileName().toString(), null);

        // root-level logic
        pushmethod("__class__");
        for (IR_Instr instr: cls.getInstrs()) {
            emit(instr);
        }
        popmethod();

        // root-level methods
        for (IR_Method method : cls._methods) {
            emit(method);
        }

        // root-level classes
        for (IR_Class cls2 : cls._classes) {
            emit(cls2);
        }

        cls().visitEnd();
        popclass();
    }

    public void emit(IR_Method method) {
        pushmethod(method._name);
        for (IR_Instr instr: method.getInstrs()) {
            emit(instr);
        }
        popmethod();
    }

    public void emit(IR_Instr instr) {
        switch (instr._op) {
        case BEQ:
            emitBEQ((BEQ_Instr)instr); break;
        case CALL:
            emitCALL((CALL_Instr)instr); break;
        case COPY:
            emitCOPY((COPY_Instr)instr); break;
        case DEF_INST_METH:
            emitDEF_INST_METH((DEFINE_INSTANCE_METHOD_Instr)instr); break;
        case JUMP:
            emitJUMP((JUMP_Instr)instr); break;
        case LABEL:
            emitLABEL((LABEL_Instr)instr); break;
        case PUT_FIELD:
            emitPUT_FIELD((PUT_FIELD_Instr)instr); break;
        case GET_FIELD:
            emitGET_FIELD((GET_FIELD_Instr)instr); break;
        case RECV_ARG:
            emitRECV_ARG((RECV_ARG_Instr)instr); break;
        case RETURN:
            emitRETURN((RETURN_Instr) instr); break;
        default:
            System.err.println("unsupported: " + instr._op);
        }
    }

    public void emit(Constant constant) {
        if (constant instanceof Fixnum) {
            method().push(((Fixnum)constant)._value);
        }
    }

    public void emit(Operand operand) {
        if (operand.isConstant()) {
            emit((Constant)operand);
        } else if (operand instanceof Variable) {
            emit((Variable)operand);
        }
    }

    public void emit(Variable variable) {
        int index = getVariableIndex(variable);
        method().loadLocal(index);
    }

    public void emitBEQ(BEQ_Instr beq) {
        Operand[] args = beq.getOperands();
        emit(args[0]);
        emit(args[1]);
        method().ifCmp(Type.getType(Object.class), EQ, getLabel(beq.getJumpTarget()));
    }

    public void emitCOPY(COPY_Instr copy) {
        int index = getVariableIndex(copy._result);
        emit(copy.getOperands()[0]);
        method().storeLocal(index);
    }

    public void emitCALL(CALL_Instr call) {
        for (Operand operand : call.getCallArgs()) {
            emit(operand);
        }
        method().invokeVirtual(Type.getType(Object.class), Method.getMethod("Object " + call.getMethodAddr() + " ()"));
    }

    public void emitDEF_INST_METH(DEFINE_INSTANCE_METHOD_Instr instr) {
        IR_Method irMethod = instr._method;
        GeneratorAdapter adapter = new GeneratorAdapter(ACC_PUBLIC, Method.getMethod("void " + irMethod._name + " ()"), null, null, cls());
        adapter.loadThis();
        adapter.loadArgs();
        adapter.invokeStatic(Type.getType(Object.class), Method.getMethod("Object __ruby__" + irMethod._name + " (Object)"));
        adapter.returnValue();
        adapter.endMethod();
    }

    public void emitDEF_CLS_METH(DEFINE_CLASS_METHOD_Instr instr) {
        IR_Method irMethod = instr._method;
        GeneratorAdapter adapter = new GeneratorAdapter(ACC_PUBLIC | ACC_STATIC, Method.getMethod("void " + irMethod._name + " ()"), null, null, cls());
        adapter.returnValue();
        adapter.endMethod();
    }

    public void emitJUMP(JUMP_Instr jump) {
        method().goTo(getLabel(jump._target));
    }

    public void emitLABEL(LABEL_Instr lbl) {
        method().mark(getLabel(lbl._lbl));
    }

    public void emitPUT_FIELD(PUT_FIELD_Instr putField) {
        String field = ((FieldRef)putField.getOperands()[1])._refName;
        declareField(field);
        emit(putField.getOperands()[0]);
        emit(putField.getOperands()[2]);
        method().putField(Type.getType(Object.class), field, Type.getType(Object.class));
    }

    public void emitGET_FIELD(GET_FIELD_Instr putField) {
        String field = ((FieldRef)putField.getOperands()[1])._refName;
        declareField(field);
        emit(putField.getOperands()[0]);
        method().getField(Type.getType(Object.class), field, Type.getType(Object.class));
    }

    public void emitRETURN(RETURN_Instr ret) {
        emit(ret.getOperands()[0]);
        method().returnValue();
    }

    public void emitRECV_ARG(RECV_ARG_Instr recvArg) {
        int index = getVariableIndex(recvArg._result);
        // TODO: need to get this back into the method signature...now is too late...
    }

    private int getVariableIndex(Variable variable) {
        Integer index = clsStack.peek().methodStack.peek().varMap.get(variable);
        if (index == null) {
            index = method().newLocal(Type.getType(Object.class));
            clsStack.peek().methodStack.peek().varMap.put(variable, index);
        }
        return index;
    }

    private org.objectweb.asm.Label getLabel(Label label) {
        org.objectweb.asm.Label asmLabel = clsData().methodData().labelMap.get(label);
        if (asmLabel == null) {
            asmLabel = method().newLabel();
            clsData().methodData().labelMap.put(label, asmLabel);
        }
        return asmLabel;
    }

    private void declareField(String field) {
        if (!clsData().fieldSet.contains(field)) {
            cls().visitField(ACC_PROTECTED, field, ci(Object.class), null, null);
            clsData().fieldSet.add(field);
        }
    }
}
