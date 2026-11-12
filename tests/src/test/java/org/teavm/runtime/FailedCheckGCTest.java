/*
 *  Copyright 2026 Anton Banchev.
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
package org.teavm.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.OnlyPlatform;
import org.teavm.junit.SkipJVM;
import org.teavm.junit.TeaVMTest;
import org.teavm.junit.TestPlatform;

/** The exceptions of failed checks, with the collector running while they are constructed. */
@TeaVMTest
@OnlyPlatform(TestPlatform.C)
@EachTestCompiledSeparately
public class FailedCheckGCTest {
    private static final int GARBAGE_COUNT = 2000;
    private static final int CHECK_GC_COUNT = 5;
    private static Object sink;
    private static int[] checkedArray = new int[1];
    private static int[] checkedNull;
    private static Object checkedNotString = new Object();
    private static int checkedQuotient = 1;
    private static int checkedZero;

    @Test
    @SkipJVM
    public void failedChecksSurviveGcDuringExceptionConstruction() {
        for (var i = 0; i < CHECK_GC_COUNT; ++i) {
            var caught = new Throwable[4];
            var canary = new WeakReference<>(new Object());
            // Only failed checks allocate here, so the GC that clears the canary ran inside one of them
            while (canary.get() != null) {
                try {
                    checkedArray[checkedArray.length] = 1;
                    fail();
                } catch (ArrayIndexOutOfBoundsException e) {
                    caught[0] = e;
                }
                try {
                    checkedNull[0] = 1;
                    fail();
                } catch (NullPointerException e) {
                    caught[1] = e;
                }
                try {
                    sink = (String) checkedNotString;
                    fail();
                } catch (ClassCastException e) {
                    caught[2] = e;
                }
                try {
                    checkedQuotient = checkedQuotient / checkedZero;
                    fail();
                } catch (ArithmeticException e) {
                    caught[3] = e;
                }
            }
            // A collected exception is now free memory, which these overwrite
            forceGc();
            var filler = overwriteFreeMemory();
            assertSame(ArrayIndexOutOfBoundsException.class, caught[0].getClass());
            assertSame(NullPointerException.class, caught[1].getClass());
            assertSame(ClassCastException.class, caught[2].getClass());
            assertSame(ArithmeticException.class, caught[3].getClass());
            for (var e : caught) {
                assertTrue(e.getStackTrace().length > 0);
            }
            sink = filler;
        }
    }

    // Allocates until a young object becomes unreachable, i.e. at least one GC has happened
    private static void forceGc() {
        var ref = new WeakReference<>(new Object());
        while (ref.get() != null) {
            sink = new int[64];
        }
        sink = null;
    }

    private static int[][] overwriteFreeMemory() {
        var result = new int[GARBAGE_COUNT][];
        for (var i = 0; i < result.length; ++i) {
            var array = new int[6];
            for (var j = 0; j < array.length; ++j) {
                array[j] = 0xDEADBEEF;
            }
            result[i] = array;
        }
        return result;
    }
}
