package com.fileManager.file.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 저장소 폴더 규칙. 루트({@code fileManager.storage-root})는 설정·실행 인자로 바꾼다.
 * <pre>
 * {루트}/{카테고리}/{버전}/{제목} {버전}_{yyyyMMdd}_{작업자}.{확장자}  원본
 * {루트}/{카테고리}/{버전}/edit{n}/{제목} {버전}[ 수정대조표]_{yyyyMMdd}_{작업자}.{확장자}  수정본 n번과 그 수정대조표
 * {루트}/{카테고리}/{버전}/edit{n}/{제목} {버전} 검토본_{yyyyMMdd}_{검토자}.{확장자}  수정본 n번을 거절한 검토본
 * {루트}/{카테고리}/{버전}/{제목} {버전} 검토본_{yyyyMMdd}_{검토자}.{확장자}  원본을 거절한 검토본
 * {루트}/{카테고리}/_자료/{파일 이름}               카테고리 양식·참고자료
 * {루트}/_미분류/{문서 제목}/{버전}/…               카테고리가 없는 문서
 * </pre>
 * 같은 이름의 폴더·파일이 이미 있으면 ' (2)', ' (3)'을 붙인다. 파일 시스템에 쓸 수 없는 문자는 '_'로 바꾼다.
 * 예약 폴더 이름은 같은 위치의 다른 폴더가 쓰지 못한다.
 */
public final class StorageLayout {
    /** 카테고리가 없는 문서의 폴더. 루트 바로 아래에 둔다. */
    public static final String UNCLASSIFIED_DIR = "_미분류";
    /** 카테고리 폴더 안의 양식·참고자료 폴더. */
    public static final String RESOURCE_DIR = "_자료";
    /** 루트 바로 아래(카테고리 폴더 자리)에서 쓰면 안 되는 이름. */
    public static final Set<String> ROOT_RESERVED = Set.of(UNCLASSIFIED_DIR);
    /** 카테고리 폴더 안(버전 폴더 자리)에서 쓰면 안 되는 이름. */
    public static final Set<String> CATEGORY_RESERVED = Set.of(RESOURCE_DIR);

    /** 수정대조표 표시·저장 이름에 붙는 꼬리. */
    public static final String REVISION_SHEET_SUFFIX = " 수정대조표";

    /** 검토본 표시·저장 이름에 붙는 꼬리. */
    public static final String REVIEW_COPY_SUFFIX = " 검토본";

    /** 버전 파일의 종류: 본문, 수정대조표, 거절 때 올린 검토본. 이름 꼬리만 다르다. */
    public enum VersionFileKind {
        FILE(""), REVISION_SHEET(REVISION_SHEET_SUFFIX), REVIEW_COPY(REVIEW_COPY_SUFFIX);

        private final String suffix;

        VersionFileKind(String suffix) { this.suffix = suffix; }
    }

    private StorageLayout() {}

    /** 버전 파일의 표시 이름(stored_files.file_name)과 저장 이름(키의 마지막 조각, 다운로드 이름). */
    public record VersionFileName(String display, String stored) {}

    /** 수정본 n번(1부터)과 그 수정대조표를 두는 폴더 이름. */
    public static String editDir(int n) {
        return "edit" + n;
    }

    /** 업로드 이름의 확장자: 마지막 '.'부터(대소문자 유지). 점이 없거나 '.gitignore'처럼 맨 앞이면 빈 문자열. */
    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    /** 저장 이름의 일자: 올린 시각을 서버 시간대의 날짜로 바꾼 값. 업로드와 이름 변경이 같은 기준을 쓴다. */
    public static LocalDate uploadDate(Instant uploadedAt) {
        return LocalDate.ofInstant(uploadedAt, ZoneId.systemDefault());
    }

    /**
     * 버전 파일(원본·수정본·수정대조표·검토본)의 이름. stem = {문서 제목} {버전 라벨}(대조표·검토본은 뒤에 ' 수정대조표'·' 검토본').
     * 표시 이름은 stem + 확장자, 저장 이름은 stem_{yyyyMMdd}_{작업자} + 확장자다. 쓸 수 없는 문자·길이·충돌은
     * 저장소(PathNames.safe, ' (n)')가 처리하며 표시 이름은 원래 문자를 유지한다.
     */
    public static VersionFileName versionFileName(
            String title, String label, VersionFileKind kind, String ext, Instant uploadedAt, String worker) {
        String stem = title + " " + label + kind.suffix;
        String stored = stem + "_" + DateTimeFormatter.BASIC_ISO_DATE.format(uploadDate(uploadedAt))
                + "_" + (worker == null ? "" : worker) + ext;
        return new VersionFileName(stem + ext, stored);
    }

    /** 저장소 키(상대 경로)를 이어 붙인다. 빈 조각은 건너뛴다. */
    public static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (!b.isEmpty()) b.append('/');
            b.append(p);
        }
        return b.toString();
    }
}
