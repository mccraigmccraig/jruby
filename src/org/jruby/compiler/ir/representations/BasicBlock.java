package org.jruby.compiler.ir.representations;

import org.jruby.compiler.ir.operands.Label;
import org.jruby.compiler.ir.instructions.IR_Instr;

import java.util.List;

public class BasicBlock
{
    static private int _nextBBId = 1;

    int            _id;     // Basic Block id
    CFG            _cfg;    // CFG that this basic block belongs to
    Label          _label;  // All basic blocks have a starting label
    List<IR_Instr> _instrs; // List of non-label instructions
    boolean        _isLive;

    public BasicBlock(CFG c, Label l)
    {
        _instrs = new java.util.ArrayList<IR_Instr>();
        _label  = l;
        _isLive = true;
        _cfg = c;
        _id = _nextBBId;
        _nextBBId++;
    }

    public void addInstr(IR_Instr i)
    {
        _instrs.add(i);
    }

    public void insertInstr(IR_Instr i)
    {
        _instrs.add(0, i);
    }

    public String toString() { return "BB<" + _id + "," + _label + ">"; }
}
