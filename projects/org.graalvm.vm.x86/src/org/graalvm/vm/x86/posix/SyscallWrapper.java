package org.graalvm.vm.x86.posix;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.vm.memory.VirtualMemory;
import org.graalvm.vm.x86.node.AMD64Node;

import com.everyware.posix.api.Errno;
import com.everyware.util.log.Levels;
import com.everyware.util.log.Trace;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

public class SyscallWrapper extends AMD64Node {
    private static final Logger log = Trace.create(SyscallWrapper.class);

    public static final int SYS_read = 0;
    public static final int SYS_write = 1;
    public static final int SYS_open = 2;
    public static final int SYS_close = 3;
    public static final int SYS_brk = 12;
    public static final int SYS_exit = 60;
    public static final int SYS_uname = 63;
    public static final int SYS_readlink = 89;
    public static final int SYS_arch_prctl = 158;
    public static final int SYS_exit_group = 231;
    public static final int SYS_tgkill = 234;

    private final PosixEnvironment posix;
    private final VirtualMemory memory;

    @Child private ArchPrctl prctl;

    public SyscallWrapper(PosixEnvironment posix, VirtualMemory memory) {
        this.posix = posix;
        this.memory = memory;
    }

    private long brk(long addr) {
        if (addr == 0) {
            long brk = memory.brk();
            if (posix.isStrace()) {
                log.log(Level.INFO, () -> String.format("brk(NULL) = 0x%016x", brk));
            }
            return brk;
        } else {
            long newbrk = addr;
            long brk = memory.brk(newbrk);
            if (posix.isStrace()) {
                log.log(Level.INFO, () -> String.format("brk(0x%016x) = 0x%016x", newbrk, brk));
            }
            return brk;
        }
    }

    public long executeI64(VirtualFrame frame, int nr, long a1, long a2, long a3, long a4, long a5, long a6, long a7) throws SyscallException {
        switch (nr) {
            case SYS_arch_prctl:
                if (prctl == null) {
                    CompilerDirectives.transferToInterpreter();
                    prctl = insert(new ArchPrctl());
                }
                return prctl.execute(frame, (int) a1, a2);
        }
        return executeWrapper(nr, a1, a2, a3, a4, a5, a6, a7);
    }

    @TruffleBoundary
    private long executeWrapper(int nr, long a1, long a2, long a3, long a4, long a5, long a6, long a7) throws SyscallException {
        log.log(Levels.DEBUG, () -> String.format("syscall %d: %d (%x), %d (%x), %d (%x), %d (%x), %d (%x), %d (%x), %d (%x)", nr, a1, a1, a2, a2, a3, a3, a4, a4, a5, a5, a6, a6, a7, a7));
        switch (nr) {
            case SYS_read:
                return posix.read((int) a1, a2, a3);
            case SYS_write:
                return posix.write((int) a1, a2, a3);
            case SYS_open:
                return posix.open(a1, (int) a2, (int) a3);
            case SYS_close:
                return posix.close((int) a1);
            case SYS_brk:
                return brk(a1);
            case SYS_exit:
            case SYS_exit_group: // TODO: implement difference
                throw new ProcessExitException((int) a1);
            case SYS_uname:
                return posix.uname(a1);
            case SYS_tgkill:
                throw new ProcessExitException(128 + (int) a3);
            default:
                throw new SyscallException(Errno.ENOSYS);
        }
    }
}