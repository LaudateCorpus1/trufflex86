package org.graalvm.vm.x86.isa.instruction;

import org.graalvm.vm.memory.vector.Vector128;
import org.graalvm.vm.x86.ArchitecturalState;
import org.graalvm.vm.x86.isa.AMD64Instruction;
import org.graalvm.vm.x86.isa.Operand;
import org.graalvm.vm.x86.isa.OperandDecoder;
import org.graalvm.vm.x86.node.ReadNode;
import org.graalvm.vm.x86.node.WriteNode;

import com.everyware.util.io.Endianess;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class Punpckl extends AMD64Instruction {
    private final String name;
    private final Operand operand1;
    private final Operand operand2;

    @Child protected ReadNode readOp1;
    @Child protected ReadNode readOp2;
    @Child protected WriteNode writeDst;

    protected Punpckl(long pc, byte[] instruction, String name, Operand operand1, Operand operand2) {
        super(pc, instruction);
        this.name = name;
        this.operand1 = operand1;
        this.operand2 = operand2;
    }

    protected void createChildrenIfNecessary() {
        if (readOp1 == null) {
            CompilerDirectives.transferToInterpreter();
            ArchitecturalState state = getContextReference().get().getState();
            readOp1 = operand1.createRead(state, next());
            readOp2 = operand2.createRead(state, next());
            writeDst = operand1.createWrite(state, next());
        }
    }

    public static class Punpcklbw extends Punpckl {
        public Punpcklbw(long pc, byte[] instruction, OperandDecoder operands) {
            super(pc, instruction, "punpcklbw", operands.getAVXOperand2(128), operands.getAVXOperand1(128));
        }

        @Override
        public long executeInstruction(VirtualFrame frame) {
            createChildrenIfNecessary();
            Vector128 a = readOp1.executeI128(frame);
            Vector128 b = readOp2.executeI128(frame);
            long ha = a.getI64(0);
            long la = a.getI64(1);
            long lb = b.getI64(1);
            byte[] ba = new byte[4];
            byte[] bb = new byte[4];
            Endianess.set32bitBE(ba, 0, (int) la);
            Endianess.set32bitBE(bb, 0, (int) lb);
            byte[] merged = new byte[]{bb[3], ba[3], bb[2], ba[2], bb[1], ba[1], bb[0], ba[0]};
            long result = Endianess.get64bitBE(merged);
            Vector128 out = new Vector128(ha, result);
            writeDst.executeI128(frame, out);
            return next();
        }
    }

    public static class Punpcklwd extends Punpckl {
        public Punpcklwd(long pc, byte[] instruction, OperandDecoder operands) {
            super(pc, instruction, "punpcklwd", operands.getAVXOperand2(128), operands.getAVXOperand1(128));
        }

        @Override
        public long executeInstruction(VirtualFrame frame) {
            createChildrenIfNecessary();
            Vector128 a = readOp1.executeI128(frame);
            Vector128 b = readOp2.executeI128(frame);
            long ha = a.getI64(0);
            long la = a.getI64(1);
            long lb = b.getI64(1);
            byte[] ba = new byte[4];
            byte[] bb = new byte[4];
            Endianess.set32bitBE(ba, 0, (int) la);
            Endianess.set32bitBE(bb, 0, (int) lb);
            byte[] merged = new byte[]{bb[3], bb[2], ba[3], ba[2], bb[1], bb[0], ba[1], ba[0]};
            long result = Endianess.get64bitBE(merged);
            Vector128 out = new Vector128(ha, result);
            writeDst.executeI128(frame, out);
            return next();
        }
    }

    @Override
    protected String[] disassemble() {
        return new String[]{name, operand1.toString(), operand2.toString()};
    }
}