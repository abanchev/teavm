/*
 *  Copyright 2013 Alexey Andreev.
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
package org.teavm.classlib.java.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.teavm.junit.EachTestCompiledSeparately;
import org.teavm.junit.TeaVMTest;

@TeaVMTest
@EachTestCompiledSeparately
public class ObjectTest {
    @Test
    public void objectCreated() {
        Object a = new Object();
        assertNotNull(a);
    }

    @Test
    public void differentInstancesNotEqual() {
        Object a = new Object();
        Object b = new Object();
        assertNotEquals(a, b);
    }

    @Test
    public void sameInstancesAreEqual() {
        Object a = new Object();
        Object b = a;
        assertEquals(a, b);
    }

    @Test
    public void multipleGetClassCallsReturnSameValue() {
        Object a = new Object();
        assertSame(a.getClass(), a.getClass());
    }

    @Test
    public void sameClassesAreEqual() {
        Object a = new Object();
        Object b = new Object();
        assertSame(a.getClass(), b.getClass());
    }

    @Test
    public void properInstanceDetected() {
        assertTrue(Object.class.isInstance(new Object()));
    }

    @Test
    public void toStringWorks() {
        assertTrue(new Object().toString().startsWith("java.lang.Object@"));
        assertTrue(new Object[2].toString().startsWith("[Ljava.lang.Object;@"));
        assertTrue(new byte[3].toString().startsWith("[B@"));
    }

    @Test
    public void waitWorks() throws InterruptedException {
        long start = System.currentTimeMillis();
        final Object lock = new Object();
        synchronized (lock) {
            lock.wait(110);
        }
        long end = System.currentTimeMillis();
        assertTrue(end - start > 100);
    }

    @Test
    public void monitorSurvivesGarbageCollection() {
        assertNotNull(Thread.currentThread());
        Object[] locks = { new Object(), new int[1], new Object[1] };
        for (Object lock : locks) {
            int identity = System.identityHashCode(lock);
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));
                System.gc();
                byte[][] allocations = new byte[64][];
                for (int i = 0; i < allocations.length; i++) {
                    allocations[i] = new byte[64];
                    allocations[i][0] = (byte) i;
                }
                assertTrue(Thread.holdsLock(lock), "collection must preserve monitor ownership");
                assertEquals(identity, System.identityHashCode(lock));
                assertEquals(63, allocations[63][0]);
            }
            assertEquals(identity, System.identityHashCode(lock));
        }
    }

    @Test
    public void repeatedLocksPreserveIdentityAndOwnership() {
        Object[] locks = { new Object(), new int[1], new Object[1] };
        int[] identities = new int[locks.length];
        for (int i = 0; i < locks.length; i++) {
            identities[i] = System.identityHashCode(locks[i]);
        }
        for (int pass = 0; pass < 20; pass++) {
            for (int i = 0; i < locks.length; i++) {
                Object lock = locks[i];
                assertFalse(Thread.holdsLock(lock));
                synchronized (lock) {
                    synchronized (lock) {
                        assertTrue(Thread.holdsLock(lock));
                        assertEquals(identities[i], System.identityHashCode(lock));
                    }
                    synchronized (locks[(i + 1) % locks.length]) {
                        assertTrue(Thread.holdsLock(lock));
                    }
                    assertTrue(Thread.holdsLock(lock));
                }
                assertFalse(Thread.holdsLock(lock));
                assertEquals(identities[i], System.identityHashCode(lock));
            }
            // A detached reusable monitor must remain valid across relocation.
            System.gc();
        }
    }

    @Test
    public void repeatedWaitNotifyPreservesReentrantLock() throws InterruptedException {
        Object lock = new Object();
        boolean[] ready = new boolean[1];
        int identity = System.identityHashCode(lock);
        for (int i = 0; i < 4; i++) {
            ready[0] = false;
            Thread notifier = new Thread(() -> {
                synchronized (lock) {
                    ready[0] = true;
                    lock.notifyAll();
                }
            });
            synchronized (lock) {
                synchronized (lock) {
                    notifier.start();
                    while (!ready[0]) {
                        lock.wait();
                    }
                    assertTrue(Thread.holdsLock(lock));
                    assertEquals(identity, System.identityHashCode(lock));
                }
                assertTrue(Thread.holdsLock(lock));
            }
            notifier.join();
            assertFalse(Thread.holdsLock(lock));
            assertEquals(identity, System.identityHashCode(lock));
        }
    }
}
