package com.fileManager.global.util;

public final class Texts {
    private Texts() {}

    /** 앞뒤 공백 제거(JS String.prototype.trim과 같은 집합: NBSP, BOM, 줄 구분자 등 포함). null은 그대로. */
    public static String trim(String s) {
        if (s == null) return null;
        int a = 0, b = s.length();
        while (a < b && isJsSpace(s.charAt(a))) a++;
        while (b > a && isJsSpace(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    private static boolean isJsSpace(char c) {
        return (c >= 9 && c <= 13) || c == ' ' || c == ' ' || c == ' '
                || (c >= ' ' && c <= ' ') || c == ' ' || c == ' '
                || c == ' ' || c == ' ' || c == '　' || c == '﻿';
    }
}
