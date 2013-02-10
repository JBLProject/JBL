package tk.jblib.bytecode.generation;

import tk.jblib.bytecode.util.ByteStream;
import tk.jblib.bytecode.util.Bytes;

public class Instruction {
    int address, opcode, trueLen;
    byte[] args;
    boolean wide = false;

    public Instruction(int opcode, boolean wide, int address, byte[] args) {
        this.opcode = opcode;
        this.wide = wide;
        this.address = address;
        this.args = args;
        trueLen = args.length;
    }

    public Instruction(int opcode, int address, byte[] args) {
        this(opcode, false, address, args);
    }

    public Instruction() {
    }

    public byte[] getBytes() {
        return Bytes.prepend(getArguments(), (byte) opcode);
    }

    public byte[] getArguments() {
        return args != null ? args : new byte[] {};
    }

    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public int getAddress() {
        return address;
    }

    public boolean isWide() {
        return wide;
    }

    public int getLength() {
        return trueLen + 1;
    }

    public String toString() {
        return String.format("[Opcode @ %s of type %s with args %s]", address, opcode, Bytes.bytesToString(args));
    }
}