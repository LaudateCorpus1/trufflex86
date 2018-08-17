package org.graalvm.vm.x86.isa.test;

import org.graalvm.vm.x86.isa.instruction.Shld;
import org.graalvm.vm.x86.test.InstructionTest;
import org.graalvm.vm.x86.test.runner.TestRunner;
import org.junit.Test;

public class ShldTest extends InstructionTest {
    private static final byte[] MACHINECODE1 = {0x4c, 0x0f, (byte) 0xa5, (byte) 0xc8};
    private static final String ASSEMBLY1 = "shld\trax,r9,cl";

    @Test
    public void test1() {
        check(MACHINECODE1, ASSEMBLY1, Shld.class);
    }

    @Test
    public void shld() throws Exception {
        String stdout = "0000:0000:00:0000:0000\n" +
                        "0000:0000:05:0000:0044\n" +
                        "0000:1000:00:0000:0000\n" +
                        "0000:1000:05:0002:0000\n" +
                        "0000:ffff:05:001f:0000\n" +
                        "0000:8000:08:0080:0000\n" +
                        "0000:8001:0f:4000:0004\n" +
                        "0000:8001:10:8001:0880\n" +
                        "0000:ffff:0f:7fff:0004\n" +
                        "0000:ffff:10:ffff:0884\n" +
                        "0001:8000:01:0003:0004\n" +
                        "0001:c000:01:0003:0004\n" +
                        "0001:c000:02:0007:0000\n" +
                        "1001:c000:02:4007:0000\n" +
                        "1001:c000:04:001c:0001\n" +
                        "1001:c000:00:1001:0000\n" +
                        "beef:c0de:00:beef:0000\n" +
                        "babe:efde:08:beef:0080\n" +
                        "babe:efde:04:abee:0085\n" +
                        "4000:0000:01:8000:0884\n" +
                        "8000:1000:01:0000:0845\n" +
                        "4000:0000:02:0000:0045\n" +
                        "8000:1000:02:0000:0844\n" +
                        "beef:c0de:10:c0de:0085\n" +
                        "beef:c0de:18:ffff:0085\n" +
                        "beef:c0de:20:beef:0000\n" +
                        "beef:c0de:30:c0de:0085\n" +
                        "beef:c0de:38:ffff:0085\n" +
                        "beef:c0de:40:beef:0000\n";
        TestRunner.run("shld.elf", new String[0], "", stdout, "", 0);
    }
}
