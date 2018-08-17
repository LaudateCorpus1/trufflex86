package org.graalvm.vm.x86.isa.test;

import org.graalvm.vm.x86.isa.instruction.Psrl.Psrld;
import org.graalvm.vm.x86.isa.instruction.Psrl.Psrlq;
import org.graalvm.vm.x86.test.InstructionTest;
import org.junit.Test;

public class PsrlTest extends InstructionTest {
    private static final byte[] MACHINECODE1 = {0x66, 0x41, 0x0f, 0x73, (byte) 0xd1, 0x20};
    private static final String ASSEMBLY1 = "psrlq\txmm9,0x20";

    private static final byte[] MACHINECODE2 = {0x66, 0x0f, 0x72, (byte) 0xd0, 0x1f};
    private static final String ASSEMBLY2 = "psrld\txmm0,0x1f";

    @Test
    public void test1() {
        check(MACHINECODE1, ASSEMBLY1, Psrlq.class);
    }

    @Test
    public void test2() {
        check(MACHINECODE2, ASSEMBLY2, Psrld.class);
    }
}
