package org.graalvm.vm.memory;

public class MemoryAllocator {
    private static final boolean debug = true;

    private Block memory;
    private Block free;

    private long usedMemory;

    private final long memoryBase;

    private static class Block {
        public long base;
        public long size;
        public boolean free;
        public Block prev;
        public Block next;
        public Block prevBlock;
        public Block nextBlock;

        Block(long base, long size) {
            this.base = base;
            this.size = size;
            free = true;
        }

        boolean contains(long address) {
            if (size == 0xffffffffffffffffL) { // avoid overflow
                return Long.compareUnsigned(address, base) >= 0;
            } else {
                return Long.compareUnsigned(address, base) >= 0 && Long.compareUnsigned(address, base + size) < 0;
            }
        }

        @Override
        public String toString() {
            return String.format("Block[0x%016x, 0x%016x, free=%s]", base, size, free);
        }
    }

    public MemoryAllocator(long size) {
        this(0, size);
    }

    public MemoryAllocator(long base, long size) {
        memoryBase = base;
        memory = new Block(base, size);
        free = memory;
        usedMemory = 0;
    }

    private static void check(long addr) {
        if ((addr & ~VirtualMemory.PAGE_MASK) != 0) {
            throw new IllegalArgumentException(String.format("0x%016x is not aligned", addr));
        }
    }

    public long alloc(long size) {
        check(size);
        Block b = free;
        if (b == null) {
            throw new OutOfMemoryError();
        }
        while (Long.compareUnsigned(b.size, size) < 0) {
            if (b.next == null) {
                throw new OutOfMemoryError();
            }
            b = b.next;
        }
        assert b.free == true;
        check(b);
        if (b.size == size) {
            if (b.prev != null) {
                b.prev.next = b.next;
            } else {
                assert free == b;
                free = b.next;
                check(free);
            }
            if (b.next != null) {
                b.next.prev = b.prev;
            }
            check(b.prev);
            check(b.next);
        } else {
            Block split = new Block(b.base + size, b.size - size);
            split.prevBlock = b;
            split.nextBlock = b.nextBlock;
            b.nextBlock = split;
            if (split.nextBlock != null) {
                split.nextBlock.prevBlock = split;
            }
            split.next = b.next;
            split.prev = b.prev;
            if (split.next != null) {
                split.next.prev = split;
            }
            if (split.prev != null) {
                split.prev.next = split;
            }
            check(split);
            if (free == b) {
                free = split;
            }
        }
        b.size = size;
        b.free = false;
        usedMemory += size;

        if (debug) {
            check();
        }

        return b.base;
    }

    private Block find(long addr) {
        for (Block b = memory; b != null; b = b.nextBlock) {
            if (b.base == addr || b.contains(addr)) {
                return b;
            }
        }
        return null;
    }

    public long allocat(long addr, long size) {
        check(addr);
        check(size);
        Block blk = find(addr);
        if (blk == null) {
            throw new OutOfMemoryError();
        }
        if (blk.base == addr && blk.size == size) {
            if (!blk.free) {
                if (debug) {
                    check();
                }

                return addr;
            }

            blk.free = false;
            usedMemory += size;
            if (blk.prev != null) {
                blk.prev.next = blk.next;
            } else {
                assert free == blk;
                free = blk.next;
            }
            if (blk.next != null) {
                blk.next.prev = blk.prev;
            }
            check(blk.prev);
            check(blk.next);

            if (debug) {
                check();
            }

            return addr;
        } else if (blk.base == addr && Long.compareUnsigned(blk.size, size) > 0) {
            if (!blk.free) {
                if (debug) {
                    check();
                }

                return addr;
            }

            blk.free = false;
            usedMemory += size;
            Block split = new Block(blk.base + size, blk.size - size);
            if (blk.prev != null) {
                blk.prev.next = split;
            } else {
                assert free == blk;
                free = split;
            }
            split.prev = blk.prev;
            split.next = blk.next;
            if (split.next != null) {
                split.next.prev = split;
            }
            split.prevBlock = blk;
            split.nextBlock = blk.nextBlock;
            blk.nextBlock = split;
            if (split.nextBlock != null) {
                split.nextBlock.prevBlock = split;
            }
            check(split);
            blk.size = size;

            if (debug) {
                check();
            }

            return addr;
        } else {
            Block start = blk;
            long off = addr - start.base;
            long todo = size;
            if (start.free && off != 0) { // unsigned version of "off > 0"
                start.free = true;
                Block split = new Block(blk.base + off, blk.size - off);
                split.prevBlock = blk;
                split.nextBlock = blk.nextBlock;
                blk.nextBlock = split;
                if (split.nextBlock != null) {
                    split.nextBlock.prevBlock = split;
                }
                split.next = blk.next;
                split.prev = blk;
                blk.next = split;
                if (split.next != null) {
                    split.next.prev = split;
                }
                check(split);
                blk.size = off;
                start = split;
            } else if (off != 0) {
                if (Long.compareUnsigned(blk.size - off, todo) >= 0) {
                    if (debug) {
                        check();
                    }

                    return addr;
                } else {
                    todo -= blk.size - off;
                    blk = blk.nextBlock;
                }
            }
            blk = start;
            assert start.base == addr;
            while (todo != 0) {
                if (blk.free) {
                    if (blk.size == todo) {
                        blk.free = false;
                        usedMemory += blk.size;
                        if (blk.prev != null) {
                            blk.prev.next = blk.next;
                        } else {
                            assert free == blk;
                            free = blk.next;
                        }
                        if (blk.next != null) {
                            blk.next.prev = blk.prev;
                        }
                        check(blk);

                        if (debug) {
                            check();
                        }

                        return addr;
                    }
                    // block is larger than todo
                    if (Long.compareUnsigned(blk.size, todo) > 0) {
                        Block split = new Block(blk.base + todo, blk.size - todo);
                        blk.free = false;
                        blk.size = todo;
                        split.next = blk.next;
                        split.prev = blk.prev;
                        split.nextBlock = blk.nextBlock;
                        split.prevBlock = blk;
                        blk.nextBlock = split;
                        if (split.nextBlock != null) {
                            split.nextBlock.prevBlock = split;
                        }
                        if (blk.prev != null) {
                            blk.prev.next = split;
                        } else {
                            assert free == blk;
                            free = split;
                        }
                        if (split.next != null) {
                            split.next.prev = split;
                        }
                        check(split);
                        usedMemory += todo;

                        if (debug) {
                            check();
                        }

                        return addr;
                    } else { // block is too small
                        todo -= size;
                        blk.free = false;
                        if (blk.prev != null) {
                            blk.prev.next = blk.next;
                        } else {
                            assert free == blk;
                            free = blk.next;
                        }
                        if (blk.next != null) {
                            blk.next.prev = blk.prev;
                        }
                        check(blk);
                        blk = blk.nextBlock;
                        usedMemory += size;
                        continue;
                    }
                }
                blk.free = false;
                if (Long.compareUnsigned(blk.size, todo) >= 0) {

                    if (debug) {
                        check();
                    }

                    return addr;
                } else {
                    todo -= blk.size;
                    blk = blk.nextBlock;
                }
            }

            if (debug) {
                check();
            }

            return addr;
        }
    }

    public void free(long addr) {
        check(addr);
        Block blk = find(addr);
        assert blk.base == addr;
        assert !blk.free;
        blk.free = true;
        usedMemory -= blk.size;

        // find previous free block
        Block b = blk.prevBlock;
        while (b != null) {
            if (b.free) {
                blk.next = b.next;
                b.next = blk;
                if (blk.next != null) {
                    blk.next.prev = blk;
                }
                blk.prev = b;
                check(b);
                check(blk);

                if (debug) {
                    check();
                }

                return;
            } else {
                b = b.prevBlock;
            }
        }

        // no previous free block
        assert free == null || Long.compareUnsigned(blk.base, free.base) < 0;
        assert blk.free;
        blk.next = free;
        blk.prev = null;
        if (free != null) {
            free.prev = blk;
        }
        free = blk;
        check(free);

        if (debug) {
            check();
        }

        compact(blk);

        if (debug) {
            check();
        }
    }

    public void free(long addr, long size) {
        // TODO: fix all the bugs
        check(addr);
        check(size);
        long p = addr;
        long remaining = size;
        Block start = null;
        Block lastFree = null;
        for (Block b = memory; remaining > 0 && b != null; b = b.nextBlock) {
            if (b.free) {
                lastFree = b;
            }
            if (b.base == p) {
                start = b;
                if (Long.compareUnsigned(b.size, remaining) > 0) {
                    if (b.free) {
                        if (debug) {
                            check();
                        }
                        return;
                    }

                    Block split = new Block(b.base + remaining, b.size - remaining);
                    split.prevBlock = b;
                    split.nextBlock = b.nextBlock;
                    b.nextBlock = b;
                    b.size = remaining;
                    b.free = true;
                    if (lastFree != null) {
                        b.next = lastFree.next;
                        b.prev = lastFree;
                        lastFree.next = b;
                        if (b.next != null) {
                            b.next.prev = b;
                        }
                    } else {
                        b.prev = null;
                        b.next = free;
                        free = b;
                    }

                    usedMemory -= b.size;

                    check(b);
                    check(split);
                    compact(b);

                    if (debug) {
                        check();
                    }

                    return;
                } else if (b.size == size) {
                    b.free = true;
                    usedMemory -= b.size;
                    for (Block blk = b.prevBlock; blk != null; blk = blk.prevBlock) {
                        if (blk.free) {
                            b.next = blk.next;
                            blk.next = b;
                            b.prev = blk;
                            if (b.next != null) {
                                b.next.prev = b;
                            }
                            if (b.prev != null) {
                                b.prev.next = b;
                            }

                            check(b);
                            check(blk);

                            if (debug) {
                                check();
                            }

                            return;
                        }
                    }

                    // no previous free block
                    assert free == null || Long.compareUnsigned(b.base, free.base) < 0;
                    assert b.free;
                    b.next = free;
                    b.prev = null;
                    if (free != null) {
                        free.prev = b;
                    }
                    free = b;
                    check(free);

                    if (debug) {
                        check();
                    }

                    compact(b);

                    if (debug) {
                        check();
                    }
                    return;
                } else {
                    assert false;
                }
            } else if (b.contains(p)) {
                start = b;
                assert false;
            }
        }
        compact(start);
        usedMemory -= size - remaining;

        if (debug) {
            check();
        }
    }

    private void compact(Block block) {
        Block b = block;
        assert b.free;
        while (b.prevBlock != null && b.prevBlock.free) {
            b = b.prevBlock;
        }
        assert b.prev == null || free == b;
        assert b.free;
        while (b.nextBlock != null && b.nextBlock.free) {
            Block blk = b.nextBlock;
            b.size += blk.size;
            b.nextBlock = blk.nextBlock;
            if (b.nextBlock != null) {
                b.nextBlock.prevBlock = b;
            }
            b.next = blk.next;
            if (b.next != null) {
                b.next.prev = b;
            }
            check(b);
        }
    }

    public long getUsedMemory() {
        return usedMemory;
    }

    public String dump() {
        StringBuilder buf = new StringBuilder();
        for (Block b = memory; b != null; b = b.nextBlock) {
            buf.append(b).append("\n");
        }
        return buf.toString().trim();
    }

    private static void check(Block b) {
        if (b == null) {
            return;
        }
        assert !b.free || b.prev == null || Long.compareUnsigned(b.prev.base, b.base) < 0 : String.format("prev=0x%016x, this=0x%016x", b.prev.base, b.base);
        assert !b.free || b.next == null || Long.compareUnsigned(b.base, b.next.base) < 0 : String.format("this=0x%016x, next=0x%016x", b.base, b.next.base);
        assert !b.free || b.prev == null || b.prev.next == b;
        assert !b.free || b.next == null || b.next.prev == b;
        assert b.prevBlock == null || Long.compareUnsigned(b.prevBlock.base, b.base) < 0 : String.format("prev=0x%016x, this=0x%016x", b.prevBlock.base, b.base);
        assert b.nextBlock == null || Long.compareUnsigned(b.base, b.nextBlock.base) < 0 : String.format("this=0x%016x, next=0x%016x", b.base, b.nextBlock.base);
        assert b.prevBlock == null || b.prevBlock.nextBlock == b;
        assert b.nextBlock == null || b.nextBlock.prevBlock == b;
    }

    private static void __assert(boolean b) {
        if (!b) {
            throw new AssertionError();
        }
    }

    private static void __assert(boolean b, String msg) {
        if (!b) {
            throw new AssertionError(msg);
        }
    }

    // consistency checks which *don't* rely on -ea
    public void check() {
        long usedmem = 0;
        long freemem = 0;
        long nextaddr = memoryBase;
        Block lastblock = null;
        __assert(memory.prevBlock == null);
        __assert(free == null || free.prev == null);
        __assert(free == null || free.free);
        for (Block b = memory; b != null; b = b.nextBlock) {
            __assert(b.base == nextaddr, String.format("0x%016x vs 0x%016x", b.base, nextaddr));
            __assert(b.prevBlock == lastblock);
            __assert(!b.free || b.prev != null || free == b);
            lastblock = b;
            nextaddr += b.size;
            if (b.free) {
                freemem += b.size;
            } else {
                usedmem += b.size;
            }
            check(b);
        }
        long lastaddr = free.base;
        for (Block b = free.next; b != null; b = b.next) {
            check(b);
            __assert(Long.compareUnsigned(b.base, lastaddr) > 0,
                            String.format("last: 0x%016x, cur: 0x%016x", lastaddr, b.base));
            lastaddr = b.base;
        }
        long freeMemory = 0;
        for (Block b = free; b != null; b = b.next) {
            freeMemory += b.size;
        }
        __assert(usedmem == usedMemory, String.format("used memory: 0x%x vs 0x%x", usedmem, usedMemory));
        __assert(freemem == freeMemory, String.format("free memory: 0x%x vs 0x%x", freemem, freeMemory));
    }
}