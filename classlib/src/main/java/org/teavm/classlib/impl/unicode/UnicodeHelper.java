/*
 *  Copyright 2014 Alexey Andreev.
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

import java.util.Arrays;
import org.teavm.classlib.impl.Base46;
import org.teavm.classlib.impl.CharFlow;

public final class UnicodeHelper {
    private UnicodeHelper() {
    }

    // cc3d local patch, not for upstream: Character.getType for ASCII without decoding the full
    // category table (about 300 KB of heap). Character c has category ASCII_TYPES.charAt(c) - '0'.
    public static final String ASCII_TYPES = "????????????????????????????????<HHHJHHHEFHIHDHH9999999999HHIIIHH"
            + "11111111111111111111111111EHFKGK22222222222222222222222222EIFI?";

    // cc3d local patch, not for upstream: case mappings keep a direct table for Latin-1 only, not
    // for the whole BMP (2 x 256 KB of heap); other characters use the same pairs by binary search.
    static final int FAST_CASE_TABLE_SIZE = 256;

    /** Maps a code point through a case table: its direct table, else a floor search of the pairs. */
    public static int mapChar(CharMapping table, int codePoint) {
        if (codePoint < table.fastTable.length) {
            return codePoint + table.fastTable[codePoint];
        }

        var binSearchTable = table.binarySearchTable;
        int index = binarySearchTable(binSearchTable, codePoint);
        if (index < 0 || index * 2 >= binSearchTable.length) {
            return 0;
        }
        return codePoint + binSearchTable[index * 2 + 1];
    }

    private static int binarySearchTable(int[] data, int key) {
        int l = 0;
        int u = data.length / 2 - 1;
        while (true) {
            int i = (l + u) / 2;
            int e = data[i * 2];
            if (e == key) {
                return i;
            } else if (e > key) {
                u = i - 1;
                if (u < l) {
                    return i - 1;
                }
            } else {
                l = i + 1;
                if (l > u) {
                    return i;
                }
            }
        }
    }

    static char hexDigit(int value) {
        return value < 10 ? (char) ('0' + value) : (char) ('A' + value);
    }

    static int valueOfHexDigit(char digit) {
        return digit <= '9' ? digit - '0' : digit - 'A' + 10;
    }

    public static class Range {
        public final int start;
        public final int end;
        public final byte[] data;

        public Range(int start, int end, byte[] data) {
            this.start = start;
            this.end = end;
            this.data = data;
        }
    }

    public static String encodeIntPairsDiff(int[] data) {
        StringBuilder sb = new StringBuilder();
        Base46.encodeUnsigned(sb, data.length / 2);
        int lastKey = 0;
        int lastValue = 0;
        for (int i = 0; i < data.length; i += 2) {
            int key = data[i];
            int value = data[i + 1];
            Base46.encode(sb, key - lastKey);
            Base46.encode(sb, value - lastValue);
            lastKey = key;
            lastValue = value;
        }
        return sb.toString();
    }

    public static int[] decodeIntPairsDiff(String text) {
        CharFlow flow = new CharFlow(text.toCharArray());
        int sz = Base46.decodeUnsigned(flow);
        int[] data = new int[sz * 2];
        int j = 0;
        int lastKey = 0;
        int lastValue = 0;
        for (int i = 0; i < sz; i++) {
            lastKey += Base46.decode(flow);
            lastValue += Base46.decode(flow);
            data[j++] = lastKey;
            data[j++] = lastValue;
        }
        return data;
    }

    public static String encodeIntDiff(int[] data) {
        StringBuilder sb = new StringBuilder();
        Base46.encodeUnsigned(sb, data.length);
        int last = 0;
        for (int i = 0; i < data.length; i++) {
            int v = data[i];
            Base46.encode(sb, v - last);
            last = v;
        }
        return sb.toString();
    }

    public static String encodeCaseMapping(int[] data) {
        StringBuilder sb = new StringBuilder();
        int sz = data.length / 2;
        Base46.encodeUnsigned(sb, sz);
        int last = 0;
        for (int i = 0; i < sz; i++) {
            int v = data[i * 2];
            Base46.encodeUnsigned(sb, v - last);
            last = v;
            Base46.encode(sb, data[i * 2 + 1]);
        }
        return sb.toString();
    }

    public static int[] decodeIntDiff(String text) {
        CharFlow flow = new CharFlow(text.toCharArray());
        int sz = Base46.decodeUnsigned(flow);
        int[] data = new int[sz];
        int last = 0;
        for (int i = 0; i < sz; i++) {
            last += Base46.decode(flow);
            data[i] = last;
        }
        return data;
    }

    public static int[] decodeCaseMapping(String text) {
        CharFlow flow = new CharFlow(text.toCharArray());
        int sz = Base46.decodeUnsigned(flow);
        int[] data = new int[sz * 2];
        int last = 0;
        for (int i = 0; i < sz; i++) {
            last += Base46.decodeUnsigned(flow);
            data[i * 2] = last;
            data[i * 2 + 1] = Base46.decode(flow);
        }
        return data;
    }

    public static CharMapping createCharMapping(int[] data) {
        return createCharMapping(data, FAST_CASE_TABLE_SIZE);
    }

    static CharMapping createCharMapping(int[] data, int fastTableSize) {
        var result = new int[fastTableSize];
        var last = 0;
        var lastValue = 0;
        for (var i = 0; i < data.length; i += 2) {
            var key = data[i];
            var value = data[i + 1];
            if (key >= result.length) {
                if (key == last) {
                    break;
                }
                key = result.length;
            }
            Arrays.fill(result, last, key, lastValue);
            last = key;
            lastValue = value;
        }
        return new CharMapping(data, result);
    }

    public static char encodeByte(byte b) {
        if (b < '\"' - ' ') {
            return (char) (b + ' ');
        } else if (b < '\\' - ' ' - 1) {
            return (char) (b + ' ' + 1);
        } else {
            return (char) (b + ' ' + 2);
        }
    }

    public static byte decodeByte(char c) {
        if (c > '\\') {
            return (byte) (c - ' ' - 2);
        } else if (c > '"') {
            return (byte) (c - ' ' - 1);
        } else {
            return (byte) (c - ' ');
        }
    }

    public static String compressRle(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length;) {
            byte b = bytes[i];
            if (i < bytes.length - 1 && b == bytes[i + 1]) {
                int count = 0;
                while (count < 16384 && i + count < bytes.length && bytes[i + count] == b) {
                    ++count;
                }
                i += count;
                if (count < 80) {
                    sb.append(UnicodeHelper.encodeByte((byte) (b + 32)));
                    sb.append(UnicodeHelper.encodeByte((byte) count));
                } else {
                    sb.append(UnicodeHelper.encodeByte((byte) 64));
                    sb.append(UnicodeHelper.encodeByte(b));
                    for (int j = 0; j < 3; ++j) {
                        sb.append(UnicodeHelper.encodeByte((byte) (count & 0x3F)));
                        count /= 0x40;
                    }
                }
            } else {
                sb.append(UnicodeHelper.encodeByte(bytes[i++]));
            }
        }
        return sb.toString();
    }

    public static Range[] extractRle(String encoded) {
        Range[] ranges = new Range[16384];
        byte[] buffer = new byte[16384];
        int index = 0;
        int rangeIndex = 0;
        int codePoint = 0;
        for (int i = 0; i < encoded.length(); ++i) {
            byte b = decodeByte(encoded.charAt(i));
            int count;
            if (b == 64) {
                b = decodeByte(encoded.charAt(++i));
                count = 0;
                int pos = 1;
                for (int j = 0; j < 3; ++j) {
                    byte digit = decodeByte(encoded.charAt(++i));
                    count |= pos * digit;
                    pos *= 0x40;
                }
            } else if (b >= 32) {
                b -= 32;
                count = decodeByte(encoded.charAt(++i));
            } else {
                count = 1;
            }
            if (b != 0 || count < 128) {
                while (count > 0) {
                    if (index == buffer.length) {
                        ranges[rangeIndex++] = new Range(codePoint, codePoint + index, Arrays.copyOf(buffer, index));
                        codePoint += index;
                        index = 0;
                    }
                    int chunk = Math.min(count, buffer.length - index);
                    Arrays.fill(buffer, index, index + chunk, b);
                    index += chunk;
                    count -= chunk;
                }
            } else {
                if (index > 0) {
                    ranges[rangeIndex++] = new Range(codePoint, codePoint + index, Arrays.copyOf(buffer, index));
                }
                codePoint += index + count;
                index = 0;
            }
        }
        if (index > 0) {
            ranges[rangeIndex++] = new Range(codePoint, codePoint + index, Arrays.copyOf(buffer, index));
        }
        return Arrays.copyOf(ranges, rangeIndex);
    }
}
