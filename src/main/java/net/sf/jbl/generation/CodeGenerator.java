package net.sf.jbl.generation;

import net.sf.jbl.introspection.Member;
import net.sf.jbl.introspection.Pool;
import net.sf.jbl.introspection.members.TryCatch;
import net.sf.jbl.introspection.members.attributes.Code;
import net.sf.jbl.introspection.metadata.SignatureReader;
import net.sf.jbl.util.ByteStream;

import java.util.*;

import static net.sf.jbl.introspection.Opcode.*;

public class CodeGenerator {

    public LinkedList<Instruction> instructions = new LinkedList<Instruction>();
    Pool<TryCatch> exceptionPool = new Pool<TryCatch>();
    Member method = null;
    private int length;
    private boolean sorted = false;

    public CodeGenerator(Code code, Member member) {
        this(code);
        method = member;
    }

    public CodeGenerator(Code code) {
        this(code.getCodePool(), 0);
        exceptionPool = code.getExceptionPool();
    }

    public CodeGenerator(byte[] bytes, int pos) {
        length = bytes.length;
        ByteStream in = new ByteStream(bytes);
        boolean wide = false;
        for (int i = 0; i != bytes.length; i++) {
            int opcode = in.readByte() & 0xFF;
            int address = i + pos;

            switch (opcode) {
                case WIDE:
                    wide = true;
                    continue;
                case ALOAD:
                case AASTORE:
                case BIPUSH:
                case DLOAD:
                case DSTORE:
                case FLOAD:
                case FSTORE:
                case ILOAD:
                case ISTORE:
                case LSTORE:
                case NEWARRAY:
                case RET:
                case LDC:
                    instructions.add(new Instruction(opcode, wide, address, in.read(wide ? 2 : 1)));
                    i += wide ? 2 : 1;
                    wide = false;
                    continue;
                case ANEWARRAY:
                case IINC:
                case SIPUSH:
                case GETFIELD:
                case GETSTATIC:
                case PUTFIELD:
                case PUTSTATIC:
                case INVOKESTATIC:
                case INVOKESPECIAL:
                case INVOKEVIRTUAL:
                case CHECKCAST:
                case LDC_W:
                case LDC2_W:
                case INSTANCEOF:
                case NEW:
                case INVOKEINTERFACE:
                    instructions.add(new Instruction(opcode, address, in.read(2)));
                    i += 2;
                    continue;
                case LOOKUPSWITCH:
                    LookupSwitch look = new LookupSwitch(address, in);
                    instructions.add(look);
                    i += look.trueLen;
                    continue;
                case TABLESWITCH:
                    TableSwitch table = new TableSwitch(address, in);
                    instructions.add(table);
                    i += table.trueLen;
                    continue;
                case INVOKEDYNAMIC:
                    instructions.add(new Instruction(opcode, address, in.read(4)));
                    i += 4;
                    continue;
                default:
                    //TODO: readd
                  /*  if (JUMPS.contains(opcode)) {
                        instructions.add(new Branch(opcode, false, address, Bytes.toShort(in.read(2), 0)));
                        i += 2;
                    } else if (JUMPS_W.contains(opcode)) {
                        instructions.add(new Branch(opcode, true, address, Bytes.toInteger(in.read(4), 0)));
                        i += 4;
                    } else*/
                        instructions.add(new Instruction(opcode, address, new byte[]{}));
            }
        }
    }

    public byte[] synthesize() {
        sort();
        ByteStream out = new ByteStream();
        for (Instruction i : instructions) {
            out.write(i.getBytes());
        }
        return out.toByteArray();
    }

    public int size() {
        return length;
    }

    public CodeGenerator inject(int pc, byte... bytes) {
        length += bytes.length;
        sorted = false;
        //TODO: switch recalibration needs these to already be in, but sorting cannot run if they are in
        instructions.addAll(new CodeGenerator(bytes, pc - 1).instructions);
        expand(pc, bytes.length);
        return this;
    }

    public CodeGenerator injectEnd(byte... bytes) {
        return inject(size(), bytes);
    }

    public CodeGenerator injectStart(byte... bytes) {
        return inject(0, bytes);
    }

    protected int recalculateJump(int pc, int length, int address, int jump) {
    //TODO: Fixme: If length is negative, -155 (length) - -1 will result in -156, instead of the desired -154
        if ((address < pc && address + jump > pc) || (address > pc && address + jump < pc))
            if (length > 0)
                jump += length;
            else  {
                jump -= length;
            }
        return jump;
    }

    protected void expand(int pc, int len) {
        for (Instruction i : instructions) {
            if (i.address >= pc) {
                i.address += len;
            }

            if (i instanceof Branch) {
                Branch j = (Branch) i;
                j.jump = recalculateJump(pc, len, j.address, j.jump);
            } else if (i instanceof Switch) {
                Switch s = (Switch) i;
                int off = s.padding;
                s.padding = (4 - ((s.address + 1) % 4)) % 4;
                off = s.padding - off;
                System.out.println("Padding grew/shrunk by " + off + " bytes.");
                length += off;
                s.defaultJump = recalculateJump(pc, len, s.address, s.defaultJump) + off;
                for (Switch.Case c : s) {
                    c.target = recalculateJump(pc, len, s.address, c.target) + off;
                }
                if (off > 0)
                    expand(s.address + 1, off);  //If padding changed, expand offsets by the number it changed, with address + 1, as to not recalibrate the switch itself
            }
        }

        for (TryCatch e : exceptionPool) {
            if (e.getEndPC() >= pc) {
                e.setEndPC(e.getEndPC() + len);
            } else if (e.getStartPC() >= pc) {
                e.setStartPC(e.getStartPC() + len);
            }
            if (e.getHandlerPC() >= pc) {
                e.setHandlerPC(e.getHandlerPC() + len);
            }
        }
    }

    public CodeGenerator inject(int pc, Instruction... instrs) {  //TODO: Has to recalibrate
        sorted = false;
        for (int i = 0; i != instrs.length; i++) {
            length += instrs[i].trueLen + 1;
            instructions.add(instrs[i]);
        }
        return this;
    }

    public void sort() {
        if (!sorted) {
            Collections.sort(instructions, new Comparator<Instruction>() {
                public int compare(Instruction f, Instruction s) {
                    return f.address - s.address;
                }
            });
            sorted = true;
        }
    }

    public int computeStackSize() {
        sort();
        if (method == null) {
            throw new RuntimeException("Cannot compute max stack size without method descriptor");
        }

        SignatureReader reader = new SignatureReader(method.getDescriptor());
        int maxStackSize = reader.getAugmentingTypes().size();
        int currentStackSize = 0;

        for (int i = 0; i != instructions.size(); i++) {
            currentStackSize += STACK_GROWTH[instructions.get(i).opcode];
            if (currentStackSize > maxStackSize) {
                maxStackSize = currentStackSize;
            }
        }
        return maxStackSize;
    }

    public int computeMaxLocals() {
        throw new UnsupportedOperationException("Max local fetching not yet implemented!");
    }
}