/*
 *  Copyright 2016 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.model.lowlevel;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Set;
import org.teavm.model.Instruction;
import org.teavm.model.MethodReference;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.BinaryInstruction;
import org.teavm.model.instructions.BinaryOperation;
import org.teavm.model.instructions.BoundCheckInstruction;
import org.teavm.model.instructions.CastInstruction;
import org.teavm.model.instructions.CloneArrayInstruction;
import org.teavm.model.instructions.ConstructArrayInstruction;
import org.teavm.model.instructions.ConstructInstruction;
import org.teavm.model.instructions.ConstructMultiArrayInstruction;
import org.teavm.model.instructions.InitClassInstruction;
import org.teavm.model.instructions.InvokeInstruction;
import org.teavm.model.instructions.MonitorEnterInstruction;
import org.teavm.model.instructions.MonitorExitInstruction;
import org.teavm.model.instructions.NullCheckInstruction;
import org.teavm.model.instructions.RaiseInstruction;
import org.teavm.runtime.ExceptionHandling;

public final class ExceptionHandlingUtil {
    private static final MethodReference FILL_STACK_TRACE = new MethodReference(ExceptionHandling.class,
            "fillStackTrace", StackTraceElement[].class);
    private static final Set<String> BUFFER_TYPES = Set.of(ByteBuffer.class.getName(), ShortBuffer.class.getName(),
            CharBuffer.class.getName(), IntBuffer.class.getName(), LongBuffer.class.getName(),
            FloatBuffer.class.getName(), DoubleBuffer.class.getName());

    private ExceptionHandlingUtil() {
    }

    public static boolean isCallInstruction(Characteristics characteristics, Instruction insn) {
        if (insn instanceof InitClassInstruction || insn instanceof ConstructInstruction
                || insn instanceof ConstructArrayInstruction || insn instanceof ConstructMultiArrayInstruction
                || insn instanceof CloneArrayInstruction || insn instanceof RaiseInstruction
                || insn instanceof MonitorEnterInstruction || insn instanceof MonitorExitInstruction
                || insn instanceof NullCheckInstruction || insn instanceof BoundCheckInstruction) {
            return true;
        } else if (insn instanceof CastInstruction) {
            return !((CastInstruction) insn).isWeak();
        } else if (insn instanceof BinaryInstruction) {
            var op = ((BinaryInstruction) insn).getOperation();
            return op == BinaryOperation.DIVIDE_CHECKED || op == BinaryOperation.MODULO_CHECKED;
        } else if (insn instanceof InvokeInstruction) {
            var method = ((InvokeInstruction) insn).getMethod();
            return isManagedMethodCall(characteristics, method) || takesBuffer(method);
        }
        return false;
    }

    // The C backend gets the address of a buffer argument with a managed call.
    private static boolean takesBuffer(MethodReference method) {
        for (var type : method.getParameterTypes()) {
            if (type instanceof ValueType.Object && BUFFER_TYPES.contains(((ValueType.Object) type).getClassName())) {
                return true;
            }
        }
        return false;
    }

    /** True for an instruction that calls nothing unless it fails, and then only to throw. */
    public static boolean isCheckInstruction(Instruction insn) {
        if (insn instanceof NullCheckInstruction || insn instanceof BoundCheckInstruction) {
            return true;
        }
        return insn instanceof CastInstruction && !((CastInstruction) insn).isWeak();
    }

    public static boolean isManagedMethodCall(Characteristics characteristics, MethodReference method) {
        if (characteristics.isManaged(method) || method.equals(FILL_STACK_TRACE)) {
            return true;
        }
        return method.getClassName().equals(ExceptionHandling.class.getName())
                && method.getName().startsWith("throw");
    }
}
