package com.fileManager.file.domain;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;

/**
 * 파일 본문 저장소 포트. 루트 아래 상대 경로(키, '/' 구분)로 다룬다. DB(stored_files.storage_key)에는 키만 둔다.
 * 쓰기·이동은 즉시 하고, 활성 트랜잭션이 롤백되면 되돌린다(되돌리기는 등록의 역순으로 한다). 삭제는 커밋 뒤에 한다.
 */
public interface FileStorage {
    /** 저장 루트의 절대 경로(설정을 해석한 값). */
    String rootPath();

    /** parent 아래에 이름이 겹치지 않는 폴더를 새로 만들고 그 이름을 돌려준다. 롤백되면 비어 있을 때 지운다. */
    String createDir(String parent, String desired, Set<String> reserved);

    /**
     * parent 안의 current 폴더 이름을 desired로 바꾼다. 바뀐 이름을 돌려주며 바꿀 필요가 없으면 current 그대로.
     * 대상 이름이 이미 있으면 ' (n)'을 붙인다. 롤백되면 원래 이름으로 되돌린다.
     */
    String renameDir(String parent, String current, String desired, Set<String> reserved);

    /** parent 안에서 desired와 겹치지 않는 이름(아직 없는 폴더)을 고른다. 만들지는 않는다. */
    String freeName(String parent, String desired, Set<String> reserved);

    /** from 폴더를 to로 옮긴다(to의 부모는 만든다). from이 없으면 to 폴더만 만든다. 롤백되면 되돌린다. */
    void moveDir(String fromKey, String toKey);

    /** dir 아래에 원래 이름으로 저장하고 키를 돌려준다. 같은 이름이 있으면 ' (n)'. 롤백되면 지운다. */
    String store(FileUpload upload, String dir, String originalName);

    /**
     * 같은 폴더 안에서 key의 파일 이름을 desiredName(PathNames.safe 적용)으로 바꾸고 새 키를 돌려준다.
     * 바꿀 필요가 없으면 key 그대로. 대상 이름이 이미 있으면 ' (n)'(확장자 앞). 대소문자만 다르면 임시 이름을 거친다.
     * 롤백되면 원래 이름으로 되돌린다.
     */
    String renameFile(String key, String desiredName);

    /** 키의 본문을 연다. 없으면 java.nio.file.NoSuchFileException. */
    InputStream open(String key) throws IOException;

    /** 커밋 뒤 파일을 지운다. 트랜잭션이 없으면 즉시. */
    void deleteAfterCommit(Collection<String> keys);

    /** 커밋 뒤 폴더를 안의 파일까지 통째로 지운다(루트 자체는 거부). 트랜잭션이 없으면 즉시. */
    void deleteDirAfterCommit(String dirKey);

    /** 커밋 뒤 폴더를 정리한다: 빈 하위 폴더를 지우고, 파일이 하나도 없으면 폴더 자체도 지운다. */
    void pruneDirAfterCommit(String dirKey);
}
