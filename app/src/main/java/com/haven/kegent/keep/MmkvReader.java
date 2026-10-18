package com.haven.kegent.keep;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Read one string from an unencrypted MMKV file, applying its append/overwrite log. */
public final class MmkvReader {
    private MmkvReader() {}

    public static String string(byte[] bytes, String wanted) {
        if (bytes.length < 5) throw new IllegalArgumentException("Keep 账号缓存不完整");
        long size =
                (bytes[0] & 255L)
                        | ((bytes[1] & 255L) << 8)
                        | ((bytes[2] & 255L) << 16)
                        | ((bytes[3] & 255L) << 24);
        if (size < 1 || size > bytes.length - 4)
            throw new IllegalArgumentException("Keep 账号缓存格式不兼容");
        Cursor c = new Cursor(bytes, 4, (int) size + 4);
        c.number();
        byte[] selected = null;
        while (c.position < c.limit) {
            String key = new String(c.bytes(), StandardCharsets.UTF_8);
            byte[] value = c.bytes();
            if (wanted.equals(key)) selected = value.length == 0 ? null : value;
        }
        if (selected == null) return "";
        Cursor value = new Cursor(selected, 0, selected.length);
        String answer = new String(value.bytes(), StandardCharsets.UTF_8);
        if (value.position != value.limit) throw new IllegalArgumentException("Keep 账号缓存编码不兼容");
        return answer;
    }

    private static final class Cursor {
        final byte[] data;
        final int limit;
        int position;

        Cursor(byte[] data, int position, int limit) {
            this.data = data;
            this.position = position;
            this.limit = limit;
        }

        int number() {
            int n = 0, shift = 0;
            while (position < limit && shift < 32) {
                int b = data[position++] & 255;
                n |= (b & 127) << shift;
                if ((b & 128) == 0) return n;
                shift += 7;
            }
            throw new IllegalArgumentException("MMKV 数据截断");
        }

        byte[] bytes() {
            int n = number();
            if (n < 0 || n > limit - position) throw new IllegalArgumentException("MMKV 字段越界");
            byte[] b = Arrays.copyOfRange(data, position, position + n);
            position += n;
            return b;
        }
    }
}
