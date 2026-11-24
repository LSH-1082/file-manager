package com.fileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 소스의 package·import 줄만 읽어 4계층 의존 규칙을 확인한다(라이브러리 없음). */
class ArchitectureTest {
    private static final String BASE = "com.fileManager";
    private static final String OLD_PACKAGE = "com." + "docmgr"; // 이 파일에 옛 이름이 글자 그대로 남지 않게 나눠 쓴다
    private static final String OLD_PATH = "com/" + "docmgr";
    private static final Set<String> DOMAINS = Set.of("category", "document", "file", "resource", "schedule");
    private static final Set<String> LAYERS = Set.of("presentation", "application", "domain", "infrastructure");
    private static final Set<String> GLOBALS = Set.of("config", "error", "util");
    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.*]+)\\s*;");
    private static final List<String> BANNED_JDBC = List.of("JdbcTemplate", "JdbcClient", "NamedParameterJdbcTemplate");

    private record Source(Path path, String pkg, List<String> imports) {}

    private static List<Path> javaFiles(String dir) throws IOException {
        try (Stream<Path> s = Files.walk(Path.of(dir))) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static Source read(Path p) throws IOException {
        String pkg = "";
        List<String> imports = new ArrayList<>();
        for (String line : Files.readAllLines(p)) {
            String t = line.trim();
            Matcher m = PACKAGE.matcher(t);
            if (m.find()) pkg = m.group(1);
            m = IMPORT.matcher(t);
            if (m.find()) imports.add(m.group(1));
        }
        return new Source(p, pkg, imports);
    }

    /** com.fileManager.{도메인}.{계층}... 이면 {도메인, 계층}, 아니면 null. */
    private static String[] layerOf(String name) {
        String[] parts = name.split("\\.");
        if (parts.length >= 4 && name.startsWith(BASE + ".") && DOMAINS.contains(parts[2]) && LAYERS.contains(parts[3])) {
            return new String[] {parts[2], parts[3]};
        }
        return null;
    }

    private static boolean uses(String imp, String prefix) { return imp.startsWith(prefix); }

    @Test
    void architectureRulesHold() throws IOException {
        List<String> violations = new ArrayList<>();

        // (a) 옛 패키지 잔재(main·test 모두)
        for (String dir : List.of("src/main/java", "src/test/java")) {
            for (Path p : javaFiles(dir)) {
                if (Files.readString(p).contains(OLD_PACKAGE)) violations.add("(a) 옛 패키지 문자열: " + p);
            }
        }
        for (String dir : List.of("src/main/java/" + OLD_PATH, "src/test/java/" + OLD_PATH)) {
            if (Files.exists(Path.of(dir))) violations.add("(a) 디렉터리가 남음: " + dir);
        }
        for (Path p : javaFiles("src/test/java")) {
            if (!read(p).pkg().startsWith(BASE)) violations.add("(a) 테스트 패키지: " + p);
        }

        // (h) 문자열로도 확인(주석·정규화 이름 포함)
        for (Path p : javaFiles("src/main/java")) {
            String text = Files.readString(p);
            for (String b : BANNED_JDBC) {
                if (text.contains(b)) violations.add("(h) " + b + ": " + p);
            }
        }

        List<Source> sources = new ArrayList<>();
        for (Path p : javaFiles("src/main/java/com/fileManager")) sources.add(read(p));

        for (Source s : sources) {
            String name = s.path().getFileName().toString();
            String[] me = layerOf(s.pkg());
            String[] parts = s.pkg().split("\\.");

            // (b) 패키지 모양
            boolean okPackage = s.pkg().equals(BASE)
                    || me != null && parts.length == 4
                    || s.pkg().startsWith(BASE + ".global.") && parts.length == 4 && GLOBALS.contains(parts[3]);
            if (!okPackage) violations.add("(b) 허용되지 않는 패키지 " + s.pkg() + ": " + name);

            boolean inGlobal = s.pkg().startsWith(BASE + ".global");
            for (String imp : s.imports()) {
                String[] other = layerOf(imp);
                String where = name + " -> " + imp;

                // (h) 직접 SQL 도구
                for (String b : BANNED_JDBC) {
                    if (imp.endsWith("." + b)) violations.add("(h) " + where);
                }

                if (me != null) {
                    String layer = me[1];
                    if (layer.equals("domain")) {
                        // (c)
                        if (uses(imp, "org.springframework.") || uses(imp, "org.hibernate.")
                                || uses(imp, "java.nio.file.") || uses(imp, "jakarta.servlet.")) {
                            violations.add("(c) domain의 금지 import: " + where);
                        }
                        if (other != null && !other[1].equals("domain")) violations.add("(c) domain -> 다른 계층: " + where);
                    }
                    if (layer.equals("application") && other != null
                            && (other[1].equals("presentation") || other[1].equals("infrastructure"))) {
                        violations.add("(d) " + where);
                    }
                    if (layer.equals("presentation") && other != null) {
                        if (other[1].equals("infrastructure")) violations.add("(e) " + where);
                        if (other[1].equals("presentation") && !other[0].equals(me[0])
                                && !imp.equals(BASE + ".file.presentation.MultipartUploads")) {
                            violations.add("(e) 다른 도메인 presentation: " + where);
                        }
                    }
                    // (f) 도메인 간 방향: file < category < document < schedule, resource
                    //     (document·resource·schedule에서 resource와 schedule는 서로 모르고, document는 둘 다 모른다)
                    if (other != null && !other[0].equals(me[0])) {
                        boolean bad = switch (me[0]) {
                            case "file" -> true;
                            case "schedule" -> other[0].equals("resource");
                            default -> other[0].equals("document") || other[0].equals("resource")
                                    || other[0].equals("schedule");
                        };
                        if (bad) violations.add("(f) " + where);
                    }
                }

                // (g) global
                if (inGlobal) {
                    if (other != null) violations.add("(g) global -> 도메인: " + where);
                    // ApiErrorHandler(@RestControllerAdvice)는 웹 오류 변환기라 error 패키지에서 Spring Web을 쓰는 유일한 예외다
                    boolean webAdvice = name.equals("ApiErrorHandler.java");
                    if ((s.pkg().endsWith(".error") || s.pkg().endsWith(".util")) && !webAdvice
                            && uses(imp, "org.springframework.")) {
                        violations.add("(g) global의 Spring import: " + where);
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), "아키텍처 위반 " + violations.size() + "건:\n" + String.join("\n", violations));
    }
}
