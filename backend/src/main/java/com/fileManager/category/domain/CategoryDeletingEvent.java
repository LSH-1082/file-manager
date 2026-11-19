package com.fileManager.category.domain;

import java.util.UUID;

/** 카테고리를 지우기 직전이다(같은 트랜잭션 안의 동기 이벤트). 받는 쪽이 자기 데이터를 정리하거나 옮긴다. */
public record CategoryDeletingEvent(UUID categoryId, String dirName) {}
