package com.fileManager.schedule.application;

import com.fileManager.schedule.domain.Schedule;
import java.util.UUID;

/** 발송 문서와 조회 시점의 문서 상태(제목, 카테고리 id — 미분류면 null, 최신 버전 단계). */
public record ScheduleView(Schedule schedule, String documentTitle, UUID categoryId, String stage) {}
