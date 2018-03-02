package org.graalvm.vm.x86.isa;

public class OperandDecoder {
    public static final int R8 = 0;
    public static final int R16 = 1;
    public static final int R32 = 2;
    public static final int R64 = 3;

    private final ModRM modrm;
    private final SIB sib;
    private final long displacement;
    private final AMD64RexPrefix rex;
    private final SegmentRegister segment;

    public OperandDecoder(ModRM modrm, SIB sib, long displacement) {
        this.modrm = modrm;
        this.sib = sib;
        this.displacement = displacement;
        this.rex = null;
        this.segment = null;
    }

    public OperandDecoder(ModRM modrm, SIB sib, long displacement, AMD64RexPrefix rex, SegmentRegister segment) {
        this.modrm = modrm;
        this.sib = sib;
        this.displacement = displacement;
        this.rex = rex;
        this.segment = segment;
    }

    public Operand getOperand1(int type) {
        if (rex != null) {
            // TODO!
            if (modrm.hasSIB()) {
                boolean hasDisplacement = modrm.hasDisplacement();
                if (modrm.hasSIB() && sib.base == 0b101) {
                    switch (modrm.getMod()) {
                        case 0b00:
                        case 0b10:
                        case 0b01:
                            hasDisplacement = true;
                    }
                }
                if (hasDisplacement) {
                    if (sib.index == 0b100 && !rex.b) { // rsp not used
                        if (modrm.getMod() == 0) { // base not used
                            return new MemoryOperand(segment, displacement);
                        } else {
                            return new MemoryOperand(segment, sib.getBase(rex.b), displacement);
                        }
                    } else if (sib.base == 0b101) {
                        switch (modrm.getMod()) {
                            case 0b00:
                                return new MemoryOperand(segment, null, sib.getIndex(rex.x), sib.getShift(), displacement);
                            case 0b01:
                            case 0b10:
                                return new MemoryOperand(segment, sib.getBase(rex.b), sib.getIndex(rex.x), sib.getShift(), displacement);
                            default:
                                throw new AssertionError("this should not have a SIB/displacement!");
                        }
                    } else {
                        if (sib.index == 0b100) { // no index
                            return new MemoryOperand(segment, sib.getBase(rex.b), displacement);
                        } else {
                            return new MemoryOperand(segment, sib.getBase(rex.b), sib.getIndex(rex.x), sib.getShift(), displacement);
                        }
                    }
                } else if (modrm.getMod() == 0 && modrm.getRM() == 0b101) { // base not used
                    return new MemoryOperand(segment, sib.getIndex(rex.x), sib.getShift());
                } else if (sib.index == 0b100) { // index not used
                    return new MemoryOperand(segment, sib.getBase(rex.b));
                } else if (modrm.getMod() == 0 && sib.base == 0b101) { // base not used
                    return new MemoryOperand(segment, null, sib.getIndex(rex.x), sib.getShift());
                } else {
                    return new MemoryOperand(segment, sib.getBase(rex.b), sib.getIndex(rex.x), sib.getShift());
                }
            } else if (modrm.hasDisplacement()) {
                RegisterOperand op = (RegisterOperand) modrm.getOperand1(ModRM.A64, ModRM.R64);
                Register reg = Register.RIP;
                if (op != null) {
                    reg = op.getRegister();
                    reg = getRegister(reg, rex.b);
                }
                return new MemoryOperand(segment, reg, displacement);
            } else {
                Operand op = modrm.getOperand1(ModRM.A64, type);
                if (op instanceof RegisterOperand) {
                    if (type == R8 && modrm.getMod() == 0b11) {
                        int id = modrm.getRM();
                        Register reg = Register.get(id + (rex.b ? 8 : 0)).getSize(1);
                        return new RegisterOperand(reg);
                    }
                    Register reg = ((RegisterOperand) op).getRegister();
                    return new RegisterOperand(getRegister(reg, rex.b));
                } else if (op instanceof MemoryOperand) {
                    MemoryOperand mem = (MemoryOperand) op;
                    Register base = mem.getBase();
                    assert mem.getIndex() == null;
                    assert mem.getDisplacement() == 0;
                    return new MemoryOperand(segment, getRegister(base, rex.b));
                } else {
                    return seg(op);
                }
            }
        }
        if (modrm.hasSIB()) {
            if (modrm.hasDisplacement()) {
                if (sib.base == 0b101 && modrm.getMod() != 0) {
                    return new MemoryOperand(segment, Register.RBP, sib.getIndex(), sib.getShift(), displacement);
                } else {
                    return new MemoryOperand(segment, sib.getBase(), sib.getIndex(), sib.getShift(), displacement);
                }
            } else {
                return new MemoryOperand(segment, sib.getBase(), sib.getIndex(), sib.getShift());
            }
        } else {
            if (modrm.hasDisplacement()) {
                RegisterOperand op = (RegisterOperand) modrm.getOperand1(ModRM.A64, type);
                Register reg = Register.RIP;
                if (op != null) {
                    reg = op.getRegister();
                }
                return new MemoryOperand(segment, reg, displacement);
            } else {
                return seg(modrm.getOperand1(ModRM.A64, type));
            }
        }
    }

    private Operand seg(Operand op) {
        if (op instanceof MemoryOperand) {
            return ((MemoryOperand) op).getInSegment(segment);
        } else {
            return op;
        }
    }

    private static Register getRegister(Register reg, boolean r) {
        if (r) {
            return Register.get(reg.getID() + 8).getSize(reg.getSize());
        } else {
            return reg;
        }
    }

    public Operand getOperand2(int type) {
        if (rex != null && rex.r) {
            Register reg = modrm.getOperand2(type);
            return new RegisterOperand(getRegister(reg, rex.r));
        } else {
            return new RegisterOperand(modrm.getOperand2(type));
        }
    }

    public Operand getAVXOperand1(int size) {
        Operand op = getOperand1(R64);
        if (op instanceof RegisterOperand) {
            return new AVXRegisterOperand(((RegisterOperand) op).getRegister().getID(), size);
        } else {
            return op;
        }
    }

    public Operand getAVXOperand2(int size) {
        Operand op = getOperand2(R64);
        if (op instanceof RegisterOperand) {
            return new AVXRegisterOperand(((RegisterOperand) op).getRegister().getID(), size);
        } else {
            return op;
        }
    }
}