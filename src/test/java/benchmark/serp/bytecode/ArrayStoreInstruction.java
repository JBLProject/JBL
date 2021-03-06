package benchmark.serp.bytecode;

import benchmark.serp.bytecode.visitor.*;

/**
 * Store a value from the stack into an array.
 *
 * @author Abe White
 */
public class ArrayStoreInstruction extends ArrayInstruction {
    private static final Class[][] _mappings = new Class[][] {
        { boolean.class, int.class },
        { void.class, int.class },
    };

    ArrayStoreInstruction(Code owner) {
        super(owner);
    }

    ArrayStoreInstruction(Code owner, int opcode) {
        super(owner, opcode);
    }

    public int getLogicalStackChange() {
        switch (getOpcode()) {
        case Constants.NOP:
            return 0;
        default:
            return -3;
        }
    }

    public int getStackChange() {
        switch (getOpcode()) {
        case Constants.DASTORE:
        case Constants.LASTORE:
            return -4;
        case Constants.NOP:
            return 0;
        default:
            return -3;
        }
    }

    public String getTypeName() {
        switch (getOpcode()) {
        case Constants.IASTORE:
            return int.class.getName();
        case Constants.LASTORE:
            return long.class.getName();
        case Constants.FASTORE:
            return float.class.getName();
        case Constants.DASTORE:
            return double.class.getName();
        case Constants.AASTORE:
            return Object.class.getName();
        case Constants.BASTORE:
            return byte.class.getName();
        case Constants.CASTORE:
            return char.class.getName();
        case Constants.SASTORE:
            return short.class.getName();
        default:
            return null;
        }
    }

    public TypedInstruction setType(String type) {
        type = mapType(type, _mappings, true);
        if (type == null)
            return (TypedInstruction) setOpcode(Constants.NOP);

        switch (type.charAt(0)) {
        case 'i':
            return (TypedInstruction) setOpcode(Constants.IASTORE);
        case 'l':
            return (TypedInstruction) setOpcode(Constants.LASTORE);
        case 'f':
            return (TypedInstruction) setOpcode(Constants.FASTORE);
        case 'd':
            return (TypedInstruction) setOpcode(Constants.DASTORE);
        case 'b':
            return (TypedInstruction) setOpcode(Constants.BASTORE);
        case 'c':
            return (TypedInstruction) setOpcode(Constants.CASTORE);
        case 's':
            return (TypedInstruction) setOpcode(Constants.SASTORE);
        default:
            return (TypedInstruction) setOpcode(Constants.AASTORE);
        }
    }

    public void acceptVisit(BCVisitor visit) {
        visit.enterArrayStoreInstruction(this);
        visit.exitArrayStoreInstruction(this);
    }
}
