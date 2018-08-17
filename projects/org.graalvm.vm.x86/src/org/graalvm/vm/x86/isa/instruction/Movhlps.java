package org.graalvm.vm.x86.isa.instruction;

import org.graalvm.vm.memory.vector.Vector128;
import org.graalvm.vm.x86.ArchitecturalState;
import org.graalvm.vm.x86.isa.AMD64Instruction;
import org.graalvm.vm.x86.isa.Operand;
import org.graalvm.vm.x86.isa.OperandDecoder;
import org.graalvm.vm.x86.node.ReadNode;
import org.graalvm.vm.x86.node.WriteNode;

import com.oracle.truffle.api.frame.VirtualFrame;

public class Movhlps extends AMD64Instruction {
    private final Operand operand1;
    private final Operand operand2;

    @Child private ReadNode readSrc;
    @Child private ReadNode readDst;
    @Child private WriteNode writeDst;

    protected Movhlps(long pc, byte[] instruction, Operand operand1, Operand operand2) {
        super(pc, instruction);
        this.operand1 = operand1;
        this.operand2 = operand2;

        setGPRReadOperands(operand2);
        setGPRWriteOperands(operand1);
    }

    public Movhlps(long pc, byte[] instruction, OperandDecoder operands) {
        this(pc, instruction, operands.getAVXOperand2(128), operands.getAVXOperand1(128));
    }

    @Override
    protected void createChildNodes() {
        ArchitecturalState state = getState();
        readSrc = operand2.createRead(state, next());
        readDst = operand1.createRead(state, next());
        writeDst = operand1.createWrite(state, next());
    }

    @Override
    public long executeInstruction(VirtualFrame frame) {
        Vector128 src = readSrc.executeI128(frame);
        Vector128 dst = readDst.executeI128(frame);
        long high = src.getI64(0);
        dst.setI64(1, high);
        writeDst.executeI128(frame, dst);
        return next();
    }

    @Override
    protected String[] disassemble() {
        return new String[]{"movhlps", operand1.toString(), operand2.toString()};
    }
}
