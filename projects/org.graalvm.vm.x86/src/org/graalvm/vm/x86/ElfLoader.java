package org.graalvm.vm.x86;

import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

import org.graalvm.vm.memory.ByteMemory;
import org.graalvm.vm.memory.Memory;
import org.graalvm.vm.memory.MemoryPage;
import org.graalvm.vm.memory.VirtualMemory;
import org.graalvm.vm.x86.posix.PosixEnvironment;

import com.everyware.posix.api.BytePosixPointer;
import com.everyware.posix.api.Errno;
import com.everyware.posix.api.Posix;
import com.everyware.posix.api.PosixException;
import com.everyware.posix.api.io.Fcntl;
import com.everyware.posix.api.io.Stat;
import com.everyware.posix.elf.Elf;
import com.everyware.posix.elf.ProgramHeader;
import com.everyware.posix.elf.Symbol;
import com.everyware.posix.elf.SymbolTable;
import com.everyware.posix.libc.CString;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

//@formatter:off
/*
  ------------------------------------------------------------- 0x7fff6c845000
  0x7fff6c844ff8: 0x0000000000000000
         _  4fec: './stackdump\0'                      <------+
   env  /   4fe2: 'ENVVAR2=2\0'                               |    <----+
        \_  4fd8: 'ENVVAR1=1\0'                               |   <---+ |
        /   4fd4: 'two\0'                                     |       | |     <----+
  args |    4fd0: 'one\0'                                     |       | |    <---+ |
        \_  4fcb: 'zero\0'                                    |       | |   <--+ | |
            3020: random gap padded to 16B boundary           |       | |      | | |
 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -|       | |      | | |
            3019: 'x86_64\0'                        <-+       |       | |      | | |
  auxv      3009: random data: ed99b6...2adcc7        | <-+   |       | |      | | |
  data      3000: zero padding to align stack         |   |   |       | |      | | |
 . . . . . . . . . . . . . . . . . . . . . . . . . . .|. .|. .|       | |      | | |
            2ff0: AT_NULL(0)=0                        |   |   |       | |      | | |
            2fe0: AT_PLATFORM(15)=0x7fff6c843019    --+   |   |       | |      | | |
            2fd0: AT_EXECFN(31)=0x7fff6c844fec      ------|---+       | |      | | |
            2fc0: AT_RANDOM(25)=0x7fff6c843009      ------+           | |      | | |
   ELF      2fb0: AT_SECURE(23)=0                                     | |      | | |
 auxiliary  2fa0: AT_EGID(14)=1000                                    | |      | | |
  vector:   2f90: AT_GID(13)=1000                                     | |      | | |
 (id,val)   2f80: AT_EUID(12)=1000                                    | |      | | |
   pairs    2f70: AT_UID(11)=1000                                     | |      | | |
            2f60: AT_ENTRY(9)=0x4010c0                                | |      | | |
            2f50: AT_FLAGS(8)=0                                       | |      | | |
            2f40: AT_BASE(7)=0x7ff6c1122000                           | |      | | |
            2f30: AT_PHNUM(5)=9                                       | |      | | |
            2f20: AT_PHENT(4)=56                                      | |      | | |
            2f10: AT_PHDR(3)=0x400040                                 | |      | | |
            2f00: AT_CLKTCK(17)=100                                   | |      | | |
            2ef0: AT_PAGESZ(6)=4096                                   | |      | | |
            2ee0: AT_HWCAP(16)=0xbfebfbff                             | |      | | |
            2ed0: AT_SYSINFO_EHDR(33)=0x7fff6c86b000                  | |      | | |
 . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .        | |      | | |
            2ec8: environ[2]=(nil)                                    | |      | | |
            2ec0: environ[1]=0x7fff6c844fe2         ------------------|-+      | | |
            2eb8: environ[0]=0x7fff6c844fd8         ------------------+        | | |
            2eb0: argv[3]=(nil)                                                | | |
            2ea8: argv[2]=0x7fff6c844fd4            ---------------------------|-|-+
            2ea0: argv[1]=0x7fff6c844fd0            ---------------------------|-+
            2e98: argv[0]=0x7fff6c844fcb            ---------------------------+
  0x7fff6c842e90: argc=3
*/
//@formatter:on
public class ElfLoader {
    public static final String PLATFORM = "x86_64";
    public static final int RANDOM_SIZE = 16;
    public static final int PAGE_SIZE = 4096;
    public static final int HWCAP = 0;
    public static final int HWCAP2 = 0;

    public static final int AT_NULL = 0;
    public static final int AT_PHDR = 3;
    public static final int AT_PHENT = 4;
    public static final int AT_PHNUM = 5;
    public static final int AT_PAGESZ = 6;
    public static final int AT_BASE = 7;
    public static final int AT_FLAGS = 8;
    public static final int AT_ENTRY = 9;
    public static final int AT_UID = 11;
    public static final int AT_EUID = 12;
    public static final int AT_GID = 13;
    public static final int AT_EGID = 14;
    public static final int AT_PLATFORM = 15;
    public static final int AT_HWCAP = 16;
    public static final int AT_DCACHEBSIZE = 19;
    public static final int AT_ICACHEBSIZE = 20;
    public static final int AT_UCACHEBSIZE = 21;
    public static final int AT_IGNOREPPC = 22;
    public static final int AT_SECURE = 23;
    public static final int AT_BASE_PLATFORM = 24;
    public static final int AT_RANDOM = 25;
    public static final int AT_HWCAP2 = 26;
    public static final int AT_EXECFN = 31;

    private Elf elf;
    private long load_addr;
    private long load_bias;
    private long base;
    private long entry;

    private long sp;
    private long pc;
    private boolean amd64;
    private PosixEnvironment posix;
    private VirtualMemory memory;
    private NavigableMap<Long, Symbol> symbols;

    private String progname;
    private String[] args;
    private String[] env;

    private int ptrsz;
    private long brk;

    public ElfLoader() {
        progname = "";
        args = new String[0];
        env = new String[0];
        brk = 0;
    }

    public void setSP(long sp) {
        this.sp = sp;
    }

    public long getSP() {
        return sp;
    }

    public long getPC() {
        return pc;
    }

    public boolean isAMD64() {
        return amd64;
    }

    public void setPosixEnvironment(PosixEnvironment env) {
        posix = env;
    }

    public void setVirtualMemory(VirtualMemory memory) {
        this.memory = memory;
    }

    public NavigableMap<Long, Symbol> getSymbols() {
        return symbols;
    }

    private static long pad(long addr) {
        long offset = addr % PAGE_SIZE;
        return PAGE_SIZE - offset;
    }

    private static long getLowAddress(Elf elf) {
        long lo = -1;
        for (ProgramHeader hdr : elf.getProgramHeaders()) {
            if (hdr.getType() != Elf.PT_LOAD) {
                continue;
            }
            long a = hdr.getVirtualAddress() - hdr.getOffset();
            if (Long.compareUnsigned(a, lo) < 0) {
                lo = a;
            }
        }
        return lo;
    }

    public void load(byte[] data, String filename) throws IOException {
        elf = new Elf(data);
        amd64 = elf.ei_class == Elf.ELFCLASS64;
        ptrsz = elf.ei_class == Elf.ELFCLASS64 ? 8 : 4;
        base = 0;
        load_bias = 0;
        load_addr = getLowAddress(elf);

        symbols = new TreeMap<>();

        Memory elfhdrmem = new ByteMemory(data, false);
        MemoryPage elfhdrpage = new MemoryPage(elfhdrmem, load_addr, elf.e_phoff + elf.e_phnum * elf.e_phentsize, filename);
        memory.add(elfhdrpage);

        for (ProgramHeader hdr : elf.getProgramHeaders()) {
            if (hdr.getType() == Elf.PT_LOAD) {
                byte[] segment = new byte[(int) hdr.getMemorySize()];
                hdr.load(segment);
                MemoryPage p = new MemoryPage(new ByteMemory(segment, false), load_bias + hdr.getVirtualAddress(), memory.roundToPageSize(segment.length), filename);
                p.r = hdr.getFlag(Elf.PF_R);
                p.w = hdr.getFlag(Elf.PF_W);
                p.x = hdr.getFlag(Elf.PF_X);
                memory.add(p);
                long end = load_bias + hdr.getVirtualAddress() + segment.length;
                if (brk < load_bias + hdr.getVirtualAddress() + hdr.getMemorySize()) {
                    brk = end;
                }
            }
        }

        SymbolTable symtab = elf.getSymbolTable();
        if (symtab != null) {
            for (Symbol sym : symtab.getSymbols()) {
                if (sym.getSectionIndex() != Symbol.SHN_UNDEF) {
                    symbols.put(sym.getValue() + load_bias, sym.offset(load_bias));
                }
            }
        }

        entry = load_bias + elf.getEntryPoint();
        pc = entry;

        Optional<ProgramHeader> interp = elf.getProgramHeaders().stream().filter((x) -> x.getType() == Elf.PT_INTERP).findAny();
        if (interp.isPresent()) {
            base = 0xf8000000L;
            ProgramHeader phinterp = interp.get();
            byte[] segment = new byte[(int) phinterp.getFileSize()];
            phinterp.load(segment);
            String interpreter = CString.str(segment, 0);
            byte[] interpbin;
            try {
                interpbin = loadFile(interpreter);
            } catch (PosixException e) {
                throw new IOException(Errno.toString(e.getErrno()));
            }
            Elf interpelf = new Elf(interpbin);
            if (elf.ei_class != interpelf.ei_class) {
                throw new IOException("invalid interpreter ELFCLASS");
            }

            if (interpelf.e_type == Elf.ET_DYN) {
                long low = getLowAddress(interpelf);
                base -= low;
            } else {
                base = 0;
            }

            for (ProgramHeader hdr : interpelf.getProgramHeaders()) {
                if (hdr.getType() == Elf.PT_LOAD) {
                    // round size to page size
                    long size = hdr.getMemorySize();
                    long offset = base + hdr.getVirtualAddress();
                    long end = offset + size;
                    long pageEnd = memory.roundToPageSize(end);
                    size += pageEnd - end;

                    segment = new byte[(int) size];
                    hdr.load(segment);
                    MemoryPage p = new MemoryPage(new ByteMemory(segment, false), base + hdr.getVirtualAddress(), memory.roundToPageSize(segment.length), interpreter);
                    p.r = hdr.getFlag(Elf.PF_R);
                    p.w = hdr.getFlag(Elf.PF_W);
                    p.x = hdr.getFlag(Elf.PF_X);
                    memory.add(p);
                }
            }

            pc = base + interpelf.e_entry;

            symtab = interpelf.getSymbolTable();
            if (symtab != null) {
                for (Symbol sym : symtab.getSymbols()) {
                    if (sym.getSectionIndex() != Symbol.SHN_UNDEF) {
                        symbols.put(sym.getValue() + base, sym.offset(base));
                    }
                }
            }
        }

        long pad = pad(brk);
        MemoryPage padding = new MemoryPage(new ByteMemory(pad, false), brk, pad, "[heap]");
        memory.add(padding);
        brk += pad;
        assert brk % PAGE_SIZE == 0 : String.format("unaligned: 0x%016X", brk);

        memory.setBrk(brk);

        if (elf.ei_class == Elf.ELFCLASS32) {
            buildArgs32();
        } else if (elf.ei_class == Elf.ELFCLASS64) {
            buildArgs64();
        }
    }

    private static int align16B(int x) {
        if ((x & 0xf) != 0) {
            return x + 0x10 - (x & 0xf);
        } else {
            return x;
        }
    }

    private static long str(VirtualMemory mem, long addr, String s) {
        long ptr = addr;
        for (byte b : s.getBytes()) {
            mem.setI8(ptr, b);
            ptr++;
        }
        mem.setI8(ptr, (byte) 0);
        return ptr + 1;
    }

    private static long setPair32(VirtualMemory mem, long address, int type, int value) {
        long ptr = address;
        mem.setI32(ptr, type);
        ptr += 4;
        mem.setI32(ptr, value);
        return ptr + 4;
    }

    private static long setPair64(VirtualMemory mem, long address, int type, long value) {
        long ptr = address;
        mem.setI64(ptr, type);
        ptr += 8;
        mem.setI64(ptr, value);
        return ptr + 8;
    }

    private void buildArgs32() {
        assert ptrsz == 4;

        int stringSize = progname.length() + 1;
        for (String arg : args) {
            stringSize += arg.length() + 1;
        }
        for (String var : env) {
            stringSize += var.length() + 1;
        }

        int auxvcnt = 24;
        int auxvDataSize = PLATFORM.length() + 1 + RANDOM_SIZE;
        int pointersSize = (args.length + env.length + 3 + (auxvcnt * 2)) * ptrsz;

        stringSize = align16B(stringSize);
        auxvDataSize = align16B(auxvDataSize);
        pointersSize = align16B(pointersSize);

        int size = stringSize + auxvDataSize + pointersSize;

        VirtualMemory mem = memory;
        long r1 = sp;

        r1 -= size;
        sp = r1;

        long ptr = r1 + pointersSize + auxvDataSize + stringSize;

        // strings
        ptr = r1 + pointersSize + auxvDataSize;
        long[] ptrArgs = new long[args.length];
        long[] ptrEnv = new long[env.length];

        for (int i = 0; i < args.length; i++) {
            ptrArgs[i] = ptr;
            ptr = str(mem, ptr, args[i]);
        }

        for (int i = 0; i < env.length; i++) {
            ptrEnv[i] = ptr;
            ptr = str(mem, ptr, env[i]);
        }

        long ptrExecfn = ptr;
        ptr = str(mem, ptr, progname);
        assert ptr - (r1 + pointersSize + auxvDataSize) <= stringSize;

        // auxv data
        ptr = r1 + pointersSize;
        long ptrRandom = ptr;
        Random random = new Random();
        for (int i = 0; i < RANDOM_SIZE / 4; i++) {
            mem.setI32(ptr, random.nextInt());
            ptr += 4;
        }
        long ptrPlatform = ptr;
        for (byte b : PLATFORM.getBytes()) {
            mem.setI8(ptr, b);
            ptr++;
        }
        mem.setI8(ptr, (byte) 0);
        ptr++;

        assert ptr - (r1 + pointersSize) < auxvDataSize;

        // pointers
        ptr = r1;

        // argc
        mem.setI32(ptr, args.length);
        ptr += ptrsz;

        // argv
        for (int i = 0; i < args.length; i++) {
            mem.setI32(ptr, (int) ptrArgs[i]);
            ptr += ptrsz;
        }
        // (nil)
        mem.setI32(ptr, 0);
        ptr += ptrsz;

        // env
        for (int i = 0; i < env.length; i++) {
            mem.setI32(ptr, (int) ptrEnv[i]);
            ptr += ptrsz;
        }
        // (nil)
        mem.setI32(ptr, 0);
        ptr += ptrsz;

        // auxv
        // ptr = setPair(mem, ptr, AT_IGNOREPPC, AT_IGNOREPPC);
        // ptr = setPair(mem, ptr, AT_IGNOREPPC, AT_IGNOREPPC);
        // ptr = setPair(mem, ptr, AT_DCACHEBSIZE, Power.DCACHE_LINE_SIZE);
        // ptr = setPair(mem, ptr, AT_ICACHEBSIZE, Power.ICACHE_LINE_SIZE);
        // ptr = setPair(mem, ptr, AT_UCACHEBSIZE, 0);
        ptr = setPair32(mem, ptr, AT_PHDR, (int) (load_addr + elf.e_phoff));
        ptr = setPair32(mem, ptr, AT_PHENT, elf.e_phentsize);
        ptr = setPair32(mem, ptr, AT_PHNUM, elf.e_phnum);
        ptr = setPair32(mem, ptr, AT_PAGESZ, PAGE_SIZE);
        ptr = setPair32(mem, ptr, AT_BASE, (int) base);
        ptr = setPair32(mem, ptr, AT_FLAGS, 0);
        ptr = setPair32(mem, ptr, AT_ENTRY, (int) entry);
        ptr = setPair32(mem, ptr, AT_UID, (int) posix.getuid());
        ptr = setPair32(mem, ptr, AT_EUID, (int) posix.getuid()); // TODO
        ptr = setPair32(mem, ptr, AT_GID, (int) posix.getgid());
        ptr = setPair32(mem, ptr, AT_EGID, (int) posix.getgid()); // TODO
        ptr = setPair32(mem, ptr, AT_PLATFORM, (int) ptrPlatform);
        ptr = setPair32(mem, ptr, AT_HWCAP, HWCAP);
        ptr = setPair32(mem, ptr, AT_SECURE, 0);
        // ptr = setPair32(mem, ptr, AT_BASE_PLATFORM, (int) ptrPlatform);
        ptr = setPair32(mem, ptr, AT_RANDOM, (int) ptrRandom);
        ptr = setPair32(mem, ptr, AT_HWCAP2, HWCAP2);
        ptr = setPair32(mem, ptr, AT_EXECFN, (int) ptrExecfn);
        ptr = setPair32(mem, ptr, AT_NULL, 0);

        assert ptr - r1 <= pointersSize;
    }

    private void buildArgs64() {
        assert ptrsz == 8;

        int stringSize = progname.length() + 1;
        for (String arg : args) {
            stringSize += arg.length() + 1;
        }
        for (String var : env) {
            stringSize += var.length() + 1;
        }

        int auxvcnt = 24;
        int auxvDataSize = PLATFORM.length() + 1 + RANDOM_SIZE;
        int pointersSize = (args.length + env.length + 3 + (auxvcnt * 2)) * ptrsz;

        stringSize = align16B(stringSize);
        auxvDataSize = align16B(auxvDataSize);
        pointersSize = align16B(pointersSize);

        int size = stringSize + auxvDataSize + pointersSize;

        VirtualMemory mem = memory;
        long r1 = sp;

        r1 -= size;
        sp = r1;

        long ptr = r1 + pointersSize + auxvDataSize + stringSize;

        // strings
        ptr = r1 + pointersSize + auxvDataSize;
        long[] ptrArgs = new long[args.length];
        long[] ptrEnv = new long[env.length];

        for (int i = 0; i < args.length; i++) {
            ptrArgs[i] = ptr;
            ptr = str(mem, ptr, args[i]);
        }

        for (int i = 0; i < env.length; i++) {
            ptrEnv[i] = ptr;
            ptr = str(mem, ptr, env[i]);
        }

        long ptrExecfn = ptr;
        ptr = str(mem, ptr, progname);
        assert ptr - (r1 + pointersSize + auxvDataSize) <= stringSize;

        // auxv data
        ptr = r1 + pointersSize;
        long ptrRandom = ptr;
        Random random = new Random();
        for (int i = 0; i < RANDOM_SIZE / 4; i++) {
            mem.setI32(ptr, random.nextInt());
            ptr += 4;
        }
        long ptrPlatform = ptr;
        for (byte b : PLATFORM.getBytes()) {
            mem.setI8(ptr, b);
            ptr++;
        }
        mem.setI8(ptr, (byte) 0);
        ptr++;

        assert ptr - (r1 + pointersSize) < auxvDataSize;

        // pointers
        ptr = r1;

        // argc
        mem.setI64(ptr, args.length);
        ptr += ptrsz;

        // argv
        for (int i = 0; i < args.length; i++) {
            mem.setI64(ptr, ptrArgs[i]);
            ptr += ptrsz;
        }
        // (nil)
        mem.setI64(ptr, 0);
        ptr += ptrsz;

        // env
        for (int i = 0; i < env.length; i++) {
            mem.setI64(ptr, ptrEnv[i]);
            ptr += ptrsz;
        }
        // (nil)
        mem.setI64(ptr, 0);
        ptr += ptrsz;

        // auxv
        // ptr = setPair(mem, ptr, AT_IGNOREPPC, AT_IGNOREPPC);
        // ptr = setPair(mem, ptr, AT_IGNOREPPC, AT_IGNOREPPC);
        // ptr = setPair(mem, ptr, AT_DCACHEBSIZE, Power.DCACHE_LINE_SIZE);
        // ptr = setPair(mem, ptr, AT_ICACHEBSIZE, Power.ICACHE_LINE_SIZE);
        // ptr = setPair(mem, ptr, AT_UCACHEBSIZE, 0);
        ptr = setPair64(mem, ptr, AT_PHDR, load_addr + elf.e_phoff);
        ptr = setPair64(mem, ptr, AT_PHENT, elf.e_phentsize);
        ptr = setPair64(mem, ptr, AT_PHNUM, elf.e_phnum);
        ptr = setPair64(mem, ptr, AT_PAGESZ, PAGE_SIZE);
        ptr = setPair64(mem, ptr, AT_BASE, base);
        ptr = setPair64(mem, ptr, AT_FLAGS, 0);
        ptr = setPair64(mem, ptr, AT_ENTRY, entry);
        ptr = setPair64(mem, ptr, AT_UID, posix.getuid());
        ptr = setPair64(mem, ptr, AT_EUID, posix.getuid()); // TODO
        ptr = setPair64(mem, ptr, AT_GID, posix.getgid());
        ptr = setPair64(mem, ptr, AT_EGID, posix.getgid()); // TODO
        ptr = setPair64(mem, ptr, AT_PLATFORM, ptrPlatform);
        ptr = setPair64(mem, ptr, AT_HWCAP, HWCAP);
        ptr = setPair64(mem, ptr, AT_SECURE, 0);
        // ptr = setPair64(mem, ptr, AT_BASE_PLATFORM, ptrPlatform);
        ptr = setPair64(mem, ptr, AT_RANDOM, ptrRandom);
        ptr = setPair64(mem, ptr, AT_HWCAP2, HWCAP2);
        ptr = setPair64(mem, ptr, AT_EXECFN, ptrExecfn);
        ptr = setPair64(mem, ptr, AT_NULL, 0);

        assert ptr - r1 <= pointersSize;
    }

    public void setProgramName(String progname) {
        this.progname = progname;
    }

    public void setArguments(String... args) {
        this.args = args;
    }

    public void setEnvironment(Map<String, String> environ) {
        env = environ.entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private byte[] loadFile(String path) throws PosixException {
        @SuppressWarnings("hiding")
        Posix posix = this.posix.getPosix();
        int fd = posix.open(path, Fcntl.O_RDONLY, 0);
        Stat stat = new Stat();
        byte[] data = null;
        try {
            posix.fstat(fd, stat);
            data = new byte[(int) stat.st_size];
            int read = posix.read(fd, new BytePosixPointer(data), data.length);
            if (read != data.length) {
                throw new PosixException(Errno.EIO);
            }
        } finally {
            posix.close(fd);
        }
        return data;
    }

    @TruffleBoundary
    public void load(String filename) throws IOException {
        try {
            byte[] file = loadFile(filename);
            load(file, filename);
        } catch (PosixException e) {
            throw new IOException(filename + ": " + Errno.toString(e.getErrno()));
        }
    }
}