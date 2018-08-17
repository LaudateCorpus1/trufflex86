package org.graalvm.vm.x86.isa.test;

import org.graalvm.vm.x86.isa.instruction.Sar.Sarb;
import org.graalvm.vm.x86.isa.instruction.Sar.Sarl;
import org.graalvm.vm.x86.test.InstructionTest;
import org.graalvm.vm.x86.test.runner.TestRunner;
import org.junit.Test;

public class SarTest extends InstructionTest {
    private static final byte[] MACHINECODE1 = {(byte) 0xd1, (byte) 0xf9};
    private static final String ASSEMBLY1 = "sar\tecx,0x1";

    private static final byte[] MACHINECODE2 = {(byte) 0xc0, (byte) 0xfa, 0x07};
    private static final String ASSEMBLY2 = "sar\tdl,0x7";

    @Test
    public void test1() {
        check(MACHINECODE1, ASSEMBLY1, Sarl.class);
    }

    @Test
    public void test2() {
        check(MACHINECODE2, ASSEMBLY2, Sarb.class);
    }

    @Test
    public void sar() throws Exception {
        String stdout = "0000:00:0000:0000\n" +
                        "0000:05:0000:0044\n" +
                        "1000:00:1000:0000\n" +
                        "1000:05:0080:0000\n" +
                        "ffff:05:ffff:0085\n" +
                        "8000:08:ff80:0080\n" +
                        "8001:0f:ffff:0084\n" +
                        "8001:10:ffff:0085\n" +
                        "ffff:0f:ffff:0085\n" +
                        "ffff:10:ffff:0085\n" +
                        "8000:01:c000:0084\n" +
                        "c000:01:e000:0084\n" +
                        "c000:02:f000:0084\n" +
                        "c000:02:f000:0084\n" +
                        "c000:04:fc00:0084\n" +
                        "c000:00:c000:0000\n" +
                        "c0de:00:c0de:0000\n" +
                        "efde:08:ffef:0081\n" +
                        "efde:04:fefd:0081\n" +
                        "0000:01:0000:0044\n" +
                        "1000:01:0800:0004\n" +
                        "0000:02:0000:0044\n" +
                        "1000:02:0400:0004\n" +
                        "c0de:10:ffff:0085\n" +
                        "c0de:18:ffff:0085\n" +
                        "c0de:20:c0de:0000\n" +
                        "c0de:30:ffff:0085\n" +
                        "c0de:38:ffff:0085\n" +
                        "c0de:40:c0de:0000\n" +
                        "dffffdea:1:effffef5:0084\n" +
                        "0ffffdea:1:07fffef5:0004\n" +
                        "0ffffde0:1:07fffef0:0004\n";
        TestRunner.run("sar.elf", new String[0], "", stdout, "", 0);
    }
}
