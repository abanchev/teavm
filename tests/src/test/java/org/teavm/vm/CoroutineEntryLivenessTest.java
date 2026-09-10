/*
 * Copyright 2026 Anton Banchev.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package org.teavm.vm;

import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;
import org.teavm.backend.lowlevel.transform.CoroutineTransformation;
import org.teavm.model.ClassHolderSource;
import org.teavm.model.MethodReference;
import org.teavm.model.Program;
import org.teavm.model.ValueType;
import org.teavm.model.instructions.ExitInstruction;
import org.teavm.model.instructions.InvocationType;
import org.teavm.model.instructions.InvokeInstruction;

public class CoroutineEntryLivenessTest {
    @Test
    public void suspendingCallInEntryUsesOriginalEntryLiveness() {
        Program program = new Program();
        program.createVariable();
        var result = program.createVariable();
        var block = program.createBasicBlock();
        var async = new MethodReference("Example", "suspend", ValueType.INTEGER);
        var invoke = new InvokeInstruction();
        invoke.setType(InvocationType.SPECIAL);
        invoke.setMethod(async);
        invoke.setReceiver(result);
        block.add(invoke);
        var exit = new ExitInstruction();
        exit.setValueToReturn(result);
        block.add(exit);
        ClassHolderSource source = name -> null;
        new CoroutineTransformation(source, Set.of(async), false)
                .apply(program, new MethodReference("Example", "caller", ValueType.INTEGER));
        assertTrue(program.basicBlockCount() > 1);
    }
}
