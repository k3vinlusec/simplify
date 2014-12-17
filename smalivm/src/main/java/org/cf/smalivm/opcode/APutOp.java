package org.cf.smalivm.opcode;

import java.lang.reflect.Array;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.cf.smalivm.context.MethodState;
import org.cf.smalivm.type.UnknownValue;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction23x;

public class APutOp extends MethodStateOp {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(APutOp.class.getSimpleName());

    static APutOp create(Instruction instruction, int address) {
        String opName = instruction.getOpcode().name;
        int childAddress = address + instruction.getCodeUnits();

        Instruction23x instr = (Instruction23x) instruction;
        int valueRegister = instr.getRegisterA();
        int arrayRegister = instr.getRegisterB();
        int indexRegister = instr.getRegisterC();

        return new APutOp(address, opName, childAddress, valueRegister, arrayRegister, indexRegister);
    }

    private final int arrayRegister;
    private final int indexRegister;

    private final int valueRegister;

    public APutOp(int address, String opName, int childAddress, int valueRegister, int arrayRegister, int indexRegister) {
        super(address, opName, childAddress);

        this.valueRegister = valueRegister;
        this.arrayRegister = arrayRegister;
        this.indexRegister = indexRegister;
    }

    @Override
    public int[] execute(MethodState mState) {
        Object value = mState.readRegister(valueRegister);
        Object array = mState.readRegister(arrayRegister);
        Object indexValue = mState.readRegister(indexRegister);

        // TODO: Adding unknown elements to arrays is very pessimistic. If the value is not known, the entire array
        // becomes unknown. It's much better (though much harder) to keep track of individual unknown elements. This
        // requires more robust and complex array handling. After this thing moves past proof of concept, please fix,
        // future me.
        if (array instanceof UnknownValue) {
            // Do nothing. :(
        } else {
            if ((value instanceof UnknownValue) || (indexValue instanceof UnknownValue)) {
                String type = array.getClass().getName();
                array = new UnknownValue(type);
            } else {
                // Properly class cast - avoid LocalInstance
                // as it never needs conversions as it is only
		// ever used for aput-object calls
                if (value instanceof Number) {
		    // long and boolean are both properly read
		    if (getName().endsWith("-wide")) {
			value = (long) value;
		    } else if (getName().endsWith("-boolean")) {
			value = ((int) value == 1 ? true : false);
		    } else {
			// First properly convert to a true Integer before continuing
			// to avoid class-cast exceptions
			value = getIntegerObject(value);
			if (getName().endsWith("-byte")) {
			    value = ((Integer) value).byteValue();
			} else if (getName().endsWith("-char")) {
			    value = Character.valueOf((char) ((Integer) value).intValue());
			} else if (getName().endsWith("-short")) {
			    value = ((Integer) value).shortValue();
			}
		    }
		}

                int index = (int) indexValue;
                Array.set(array, index, value);
            }
        }

        // Let the optimizer know the array was modified.
        mState.assignRegister(arrayRegister, array);

        return getPossibleChildren();
    }

    /*
     * Function for ensuring we can get the integer object
     * and prevent class-cast exceptions.
     *
     * TODO: If this needs to be used elsewhere it should
     * likely get refacotred into a helper instance of
     * something
     */
    private Integer getIntegerObject(Object obj) {
	Integer returnable = null;

        // Check if we need to actually do anything but cast
        if (obj instanceof Integer) {
            returnable = (Integer) obj;
        } else {
            // Perform proper casting for the Primatives
	    if (obj instanceof Short) {
		returnable = ((Short) obj).intValue();
	    } else if (obj instanceof Byte) {
		returnable = ((Byte) obj).intValue();
	    } else if (obj instanceof Character) {
		returnable = (int) ((Character) obj).charValue();
	    } else if (obj instanceof Short) {
		returnable = ((Short) obj).intValue();
	    } else {
		// Attempt to save us if all else fails
		returnable = ((Integer) obj).intValue();
	    }
	}

	return returnable;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getName());
        sb.append(" r").append(valueRegister).append(", r").append(arrayRegister).append(", r").append(indexRegister);

        return sb.toString();
    }

}
