package com.fileManager.file.infrastructure;

import com.fileManager.global.util.Texts;
import java.util.regex.Pattern;

/** 카테고리·버전·파일 이름을 Windows/리눅스 모두에서 쓸 수 있는 폴더·파일 이름으로 바꾼다. */
public final class PathNames {
    private static final String FORBIDDEN = "<>:\"/\\|?*";
    private static final Pattern RESERVED = Pattern.compile("(?i)^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?$");
    private static final int MAX = 100;

    private PathNames() {}

    /** 금지 문자는 '_'로, 끝의 점·공백은 지우고, 비면 '_', 장치 이름(CON 등)이면 앞에 '_'. */
    public static String safe(String raw) {
        StringBuilder b = new StringBuilder();
        for (char c : (raw == null ? "" : raw).toCharArray()) {
            b.append(c < 32 || FORBIDDEN.indexOf(c) >= 0 ? '_' : c);
        }
        String s = stripEnd(Texts.trim(b.toString()));
        if (s.length() > MAX) {
            int dot = s.lastIndexOf('.');
            String ext = dot > 0 && s.length() - dot <= 10 ? s.substring(dot) : "";
            int cut = MAX - ext.length();
            if (Character.isHighSurrogate(s.charAt(cut - 1))) cut--; // 보충 문자 가운데서 자르지 않는다
            s = stripEnd(s.substring(0, cut)) + ext;
        }
        if (s.isEmpty()) s = "_";
        if (RESERVED.matcher(s).matches()) s = "_" + s;
        return s;
    }

    /** n번째 후보 이름. 1이면 그대로, 2부터 '이름 (n)' / 파일은 '이름 (n).확장자'. */
    public static String candidate(String name, int n, boolean file) {
        if (n == 1) return name;
        int dot = file ? name.lastIndexOf('.') : -1;
        return dot > 0 ? name.substring(0, dot) + " (" + n + ")" + name.substring(dot) : name + " (" + n + ")";
    }

    private static String stripEnd(String s) {
        int e = s.length();
        while (e > 0 && (s.charAt(e - 1) == '.' || s.charAt(e - 1) == ' ')) e--;
        return s.substring(0, e);
    }
}
