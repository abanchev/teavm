/*
 *  Copyright 2026 TeaVM contributors.
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
package org.teavm.classlib.impl.unicode;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.teavm.parsing.ClasspathResourceProvider;
import org.teavm.parsing.resource.ResourceProvider;

/** cc3d local patches, not for upstream: the smaller Unicode tables answer as the full ones did. */
public class LocalUnicodeTablesTest {
    private final ResourceProvider resources = new ClasspathResourceProvider(getClass().getClassLoader());

    @Test
    public void asciiTypesMatchUnicodeData() {
        byte[] classes = UnicodeSupport.getClasses(resources);
        assertEquals("positive control: 'A' is an upper-case letter",
                Character.UPPERCASE_LETTER, classes['A']);
        assertEquals(128, UnicodeHelper.ASCII_TYPES.length());
        for (int c = 0; c < 128; c++) {
            assertEquals("U+" + Integer.toHexString(c), classes[c], UnicodeHelper.ASCII_TYPES.charAt(c) - '0');
        }
    }

    @Test
    public void lowerCaseMatchesFullTable() {
        assertSame(UnicodeSupport.getLowerCaseMapping(resources), 'A', 'a', 0x391, 0x3B1);
    }

    @Test
    public void upperCaseMatchesFullTable() {
        assertSame(UnicodeSupport.getUpperCaseMapping(resources), 0xE9, 0xC9, 0x3B1, 0x391);
    }

    @Test
    public void titleCaseMatchesFullTable() {
        assertSame(UnicodeSupport.getTitleCaseMapping(resources), 0x1C6, 0x1C5, -1, -1);
    }

    /** Every code point maps the same through the Latin-1 table as through the old BMP-wide one. */
    private static void assertSame(int[] raw, int controlIn, int controlOut, int farIn, int farOut) {
        int[] pairs = UnicodeHelper.decodeCaseMapping(UnicodeHelper.encodeCaseMapping(raw));
        CharMapping full = UnicodeHelper.createCharMapping(pairs, 65536);
        CharMapping small = UnicodeHelper.createCharMapping(pairs);
        assertEquals(UnicodeHelper.FAST_CASE_TABLE_SIZE, small.fastTable.length);
        assertEquals("positive control", controlOut, UnicodeHelper.mapChar(small, controlIn));
        if (farIn >= 0) {
            assertEquals("past the direct table", farOut, UnicodeHelper.mapChar(small, farIn));
        }
        for (int c = 0; c <= Character.MAX_CODE_POINT; c++) {
            int expected = UnicodeHelper.mapChar(full, c);
            int actual = UnicodeHelper.mapChar(small, c);
            if (expected != actual) {
                assertEquals("U+" + Integer.toHexString(c), expected, actual);
            }
        }
    }
}
