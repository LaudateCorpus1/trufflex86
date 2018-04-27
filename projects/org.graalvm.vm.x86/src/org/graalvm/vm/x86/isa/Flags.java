package org.graalvm.vm.x86.isa;

public class Flags {
    public static final long CF = 0;
    public static final long PF = 2;
    public static final long AF = 4;
    public static final long ZF = 6;
    public static final long SF = 7;
    public static final long TF = 8;
    public static final long IF = 9;
    public static final long DF = 10;
    public static final long OF = 11;
    // public static final long IOPL = 12 | 13;
    public static final long NT = 14;
    public static final long RF = 16;
    public static final long VM = 17;
    public static final long AC = 18;
    public static final long VIF = 19;
    public static final long VIP = 20;
    public static final long ID = 21;

    public static boolean getParity(byte value) {
        return (Integer.bitCount(Byte.toUnsignedInt(value)) & 0x01) == 0;
    }
}
