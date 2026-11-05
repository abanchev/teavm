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
package org.teavm.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

/** Failing null, bound and cast checks, with the collector running while their exceptions are allocated. */
@TeaVMTest
@EachTestCompiledSeparately
public class CheckInstructionExceptionTest {
    private static final int GC_ROUNDS = 200000;
    private static Object sink;

    private static final class Holder {
        final int value;
        final int[] data;

        Holder(int value) {
            this.value = value;
            data = new int[] { value, value + 1 };
        }
    }

    private abstract static class Reader {
        abstract int read(int[] array, int index);
    }

    private static final class Plain extends Reader {
        @Override
        int read(int[] array, int index) {
            return array[index];
        }
    }

    private static final class Offset extends Reader {
        @Override
        int read(int[] array, int index) {
            return array[index + 1] + array[index];
        }
    }

    private static final Reader[] READERS = { new Plain(), new Offset() };

    @Test
    public void boundCheckInCallWithoutFrameReachesCallersHandler() {
        var array = new int[] { 1, 2, 3 };
        assertEquals(2, READERS[0].read(array, 1));
        assertEquals(5, READERS[1].read(array, 1));
        for (var reader : READERS) {
            try {
                reader.read(array, 7);
                fail();
            } catch (ArrayIndexOutOfBoundsException e) {
                // expected
            }
            try {
                reader.read(array, -2);
                fail();
            } catch (ArrayIndexOutOfBoundsException e) {
                // expected
            }
        }
    }

    @Test
    public void nullCheckInCallWithoutFrameReachesCallersHandler() {
        for (var reader : READERS) {
            try {
                reader.read(null, 0);
                fail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

    private static int castLength(Object value, int extra) {
        return ((int[]) value).length + extra;
    }

    @Test
    public void castFailureReachesCallersHandler() {
        assertEquals(5, castLength(new int[3], 2));
        try {
            castLength("string", 2);
            fail();
        } catch (ClassCastException e) {
            // expected
        }
    }

    // The check fails before this method has stored any root, so the collector must find its slots clear.
    private static int failsBeforeFirstRoot(int[] array, int index) {
        var value = array[index];
        var holder = new Holder(value);
        sink = holder;
        var other = new Holder(value + 1);
        sink = other;
        return holder.value + other.value;
    }

    @Test
    public void collectionDuringThrowBeforeFirstRoot() {
        var array = new int[4];
        var caught = 0;
        for (var i = 0; i < GC_ROUNDS; ++i) {
            try {
                failsBeforeFirstRoot(array, 4 + (i & 1023));
            } catch (ArrayIndexOutOfBoundsException e) {
                ++caught;
            }
        }
        assertEquals(GC_ROUNDS, caught);
        assertEquals(1, failsBeforeFirstRoot(array, 0));
    }

    // The locals are dead once the check throws, so their slots are not refreshed before it.
    private static int failsWithLiveLocals(Holder kept, int[] array, int index) {
        var first = new Holder(index);
        var second = new Holder(index + 1);
        var read = array[index];
        return read + first.data[1] + second.data[0] + kept.value;
    }

    @Test
    public void collectionDuringThrowKeepsCallersObjects() {
        var kept = new Holder(1000);
        var array = new int[4];
        var caught = 0;
        var total = 0;
        for (var i = 0; i < GC_ROUNDS / 4; ++i) {
            var index = i & 7;
            try {
                total += failsWithLiveLocals(kept, array, index);
            } catch (ArrayIndexOutOfBoundsException e) {
                ++caught;
            }
            assertEquals(1000, kept.value);
            assertEquals(1001, kept.data[1]);
        }
        assertEquals(GC_ROUNDS / 8, caught);
        assertTrue(total > 0);
    }

    @Test
    public void localsLiveInOwnHandlerSurviveCollection() {
        var array = new int[4];
        for (var i = 0; i < GC_ROUNDS / 4; ++i) {
            var live = new Holder(i);
            var other = new Holder(i * 2);
            try {
                sink = new int[8];
                array[live.value + 4] = 1;
                fail();
            } catch (ArrayIndexOutOfBoundsException e) {
                assertEquals(i, live.value);
                assertEquals(i + 1, live.data[1]);
                assertEquals(i * 2, other.value);
                assertEquals(i * 2 + 1, other.data[1]);
            }
        }
    }

    @Test
    public void synchronizedMethodReleasesMonitorOnFailedCheck() {
        var lock = new Object();
        var array = new int[1];
        for (var i = 0; i < 1000; ++i) {
            try {
                synchronized (lock) {
                    array[i + 1] = 1;
                }
                fail();
            } catch (ArrayIndexOutOfBoundsException e) {
                synchronized (lock) {
                    assertSame(lock, lock);
                }
            }
        }
    }

    @Test
    public void stackTraceOfExplicitlyThrownException() {
        try {
            throw new ArrayIndexOutOfBoundsException();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertNotNull(e.getStackTrace());
        }
    }

    // No fail() in the try bodies below: code it reaches throws exceptions of its own, which hid this crash for
    // null checks. The analysis must learn from the failing check alone that the exception reaches the handler.
    @Test
    public void stackTraceOfFailedBoundCheck() {
        var thrown = false;
        try {
            READERS[0].read(new int[1], 3);
        } catch (ArrayIndexOutOfBoundsException e) {
            thrown = true;
            for (var element : e.getStackTrace()) {
                assertNotNull(element.getMethodName());
            }
        }
        assertTrue(thrown);
    }

    @Test
    public void stackTraceOfFailedNullCheck() {
        var thrown = false;
        try {
            READERS[0].read(null, 0);
        } catch (NullPointerException e) {
            thrown = true;
            for (var element : e.getStackTrace()) {
                assertNotNull(element.getMethodName());
            }
        }
        assertTrue(thrown);
    }

    @Test
    public void broadHandlerCallsVirtualMethodsOfCheckException() {
        var thrown = false;
        try {
            READERS[0].read(new int[1], 3);
        } catch (RuntimeException e) {
            thrown = true;
            assertNotNull(e.getStackTrace());
            assertNotNull(e.toString());
            assertEquals(null, e.getCause());
        }
        assertTrue(thrown);
    }

    @Test
    public void stackTraceOfFailedCast() {
        try {
            castLength("string", 2);
            fail();
        } catch (ClassCastException e) {
            for (var element : e.getStackTrace()) {
                assertNotNull(element.getMethodName());
            }
        }
    }

    @Test
    public void stackTracesSurviveCollection() {
        var array = new int[4];
        for (var i = 0; i < GC_ROUNDS / 4; ++i) {
            try {
                READERS[i & 1].read(array, 4 + (i & 1023));
            } catch (ArrayIndexOutOfBoundsException e) {
                sink = new int[8];
                for (var element : e.getStackTrace()) {
                    assertNotNull(element.getMethodName());
                }
            }
        }
    }

    @Test
    public void longAndDoubleArraysKeepTheirData() {
        var longs = new long[] { 1L, Long.MAX_VALUE, -5L };
        var doubles = new double[] { 1.5, -2.5 };
        var bytes = new byte[] { 1, -2, 3 };
        assertEquals(Long.MAX_VALUE, longs[1]);
        assertEquals(-5L, longs[2]);
        assertEquals(-2.5, doubles[1], 0);
        assertEquals(-2, bytes[1]);
        longs[0] = 7L;
        assertEquals(7L, longs.clone()[0]);
    }
}
