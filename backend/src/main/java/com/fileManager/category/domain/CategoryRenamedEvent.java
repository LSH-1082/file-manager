package com.fileManager.category.domain;

import java.util.UUID;

/** 카테고리가 수정됐다(이름이 그대로여도 발행. 같은 트랜잭션 안의 동기 이벤트). oldDir/newDir는 저장 루트 아래 카테고리 폴더 이름이고 같을 수 있다. */
public record CategoryRenamedEvent(UUID categoryId, String newName, String oldDir, String newDir) {}
