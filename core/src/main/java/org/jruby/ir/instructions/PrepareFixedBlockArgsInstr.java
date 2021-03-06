package org.jruby.ir.instructions;

import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.persistence.IRReaderDecoder;
import org.jruby.ir.transformations.inlining.CloneInfo;
import org.jruby.ir.transformations.inlining.SimpleCloneInfo;
import org.jruby.runtime.Block;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class PrepareFixedBlockArgsInstr extends PrepareBlockArgsInstr  {
    public PrepareFixedBlockArgsInstr() {
        super(Operation.PREPARE_FIXED_BLOCK_ARGS);
    }

    @Override
    public Instr clone(CloneInfo ii) {
        return ii instanceof SimpleCloneInfo ? new PrepareFixedBlockArgsInstr() : NopInstr.NOP;  // FIXME: Is this correct
    }

    public static PrepareFixedBlockArgsInstr decode(IRReaderDecoder d) {
        return new PrepareFixedBlockArgsInstr();
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.PrepareFixedBlockArgsInstr(this);
    }
}
