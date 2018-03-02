package org.graalvm.vm.x86.node.flow;

import java.util.Arrays;

import org.graalvm.vm.x86.CpuRuntimeException;
import org.graalvm.vm.x86.SymbolResolver;
import org.graalvm.vm.x86.isa.AMD64Instruction;
import org.graalvm.vm.x86.node.AMD64Node;
import org.graalvm.vm.x86.node.debug.PrintStateNode;
import org.graalvm.vm.x86.posix.ProcessExitException;

import com.everyware.posix.elf.Symbol;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class AMD64BasicBlock extends AMD64Node {
    @CompilationFinal private static boolean DEBUG = false;
    @CompilationFinal private static boolean PRINT_STATE = true;
    @CompilationFinal private static boolean PRINT_ONCE = false;

    @Child private PrintStateNode printState;
    @CompilationFinal private SymbolResolver symbolResolver;

    @Children private AMD64Instruction[] instructions;
    @CompilationFinal(dimensions = 1) private AMD64BasicBlock[] successors;

    private boolean visited = false;

    public long index;

    public AMD64BasicBlock(AMD64Instruction[] instructions) {
        assert instructions.length > 0;
        this.instructions = instructions;
    }

    public boolean contains(long address) {
        for (AMD64Instruction insn : instructions) {
            if (insn.getPC() == address) {
                return true;
            }
        }
        return false;
    }

    public void setSuccessors(AMD64BasicBlock[] successors) {
        this.successors = successors;
    }

    public AMD64BasicBlock[] getSuccessors() {
        return successors;
    }

    public AMD64BasicBlock getSuccessor(long pc) {
        if (successors == null) {
            return null;
        }
        for (AMD64BasicBlock block : successors) {
            if (block.getAddress() == pc) {
                return block;
            }
        }
        return null;
    }

    public long[] getBTA() {
        return instructions[instructions.length - 1].getBTA();
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public long getIndex() {
        return index;
    }

    public long getAddress() {
        return instructions[0].getPC();
    }

    public int getInstructionCount() {
        return instructions.length;
    }

    @TruffleBoundary
    private void trace(long pc, AMD64Instruction insn) {
        Symbol sym = symbolResolver.getSymbol(pc);
        String func = sym == null ? "" : sym.getName();
        if (PRINT_STATE) {
            System.out.println("----------------\nIN: " + func);
        } else if (sym != null) {
            System.out.printf("%s:\n", sym.getName());
        }
        System.out.printf("0x%08x:\t%s\n\n", pc, insn);
    }

    private void debug(VirtualFrame frame, long pc, AMD64Instruction insn) {
        if (DEBUG && (!PRINT_ONCE || !visited)) {
            if (symbolResolver == null) {
                CompilerDirectives.transferToInterpreter();
                symbolResolver = getContextReference().get().getSymbolResolver();
            }
            trace(pc, insn);
        }
        if (DEBUG && (!PRINT_ONCE || !visited) && PRINT_STATE) {
            if (printState == null) {
                CompilerDirectives.transferToInterpreter();
                printState = insert(new PrintStateNode());
            }
            if (!PRINT_ONCE || !visited) {
                printState.execute(frame, pc);
            }
        }
    }

    @ExplodeLoop
    public long execute(VirtualFrame frame) {
        long pc = getAddress();
        try {
            for (AMD64Instruction insn : instructions) {
                if (DEBUG) {
                    debug(frame, pc, insn);
                }
                pc = insn.executeInstruction(frame);
            }
        } catch (ProcessExitException e) {
            throw e;
        } catch (Throwable t) {
            CompilerDirectives.transferToInterpreter();
            throw new CpuRuntimeException(pc, t);
        }
        if (DEBUG) {
            visited = true;
        }
        return pc;
    }

    public AMD64Instruction getLastInstruction() {
        return instructions[instructions.length - 1];
    }

    public AMD64BasicBlock split(long address) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert instructions.length > 1;
        assert address != getAddress();
        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i].getPC() == address) {
                AMD64Instruction[] head = Arrays.copyOf(instructions, i);
                AMD64Instruction[] tail = new AMD64Instruction[instructions.length - i];
                System.arraycopy(instructions, i, tail, 0, tail.length);
                assert head.length + tail.length == instructions.length;
                assert head.length > 0;
                assert tail.length > 0;
                instructions = head;
                AMD64BasicBlock result = new AMD64BasicBlock(tail);
                result.setSuccessors(successors);
                successors = new AMD64BasicBlock[]{result};
                return result;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(String.format("%016x:\n", instructions[0].getPC()));
        for (AMD64Instruction insn : instructions) {
            buf.append(insn).append('\n');
        }
        return buf.toString();
    }
}