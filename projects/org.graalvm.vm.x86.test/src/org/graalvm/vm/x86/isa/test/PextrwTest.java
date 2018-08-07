package org.graalvm.vm.x86.isa.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.graalvm.vm.x86.isa.AMD64Instruction;
import org.graalvm.vm.x86.isa.AMD64InstructionDecoder;
import org.graalvm.vm.x86.isa.CodeReader;
import org.graalvm.vm.x86.isa.instruction.Pextrw;
import org.graalvm.vm.x86.test.CodeArrayReader;
import org.junit.Test;

public class PextrwTest {
    public static final byte[] MACHINECODE1 = {0x66, 0x0f, (byte) 0xc5, (byte) 0xc8, 0x00};
    public static final String ASSEMBLY1 = "pextrw\tecx,xmm0,0x0";

    @Test
    public void test1() {
        CodeReader reader = new CodeArrayReader(MACHINECODE1, 0);
        AMD64Instruction insn = AMD64InstructionDecoder.decode(0, reader);
        assertNotNull(insn);
        assertTrue(insn instanceof Pextrw);
        assertEquals(ASSEMBLY1, insn.toString());
        assertEquals(MACHINECODE1.length, reader.getPC());
    }
}
