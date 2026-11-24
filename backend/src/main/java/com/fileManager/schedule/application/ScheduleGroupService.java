package com.fileManager.schedule.application;

import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.Ids;
import com.fileManager.global.util.IsoTimes;
import com.fileManager.global.util.Texts;
import com.fileManager.schedule.domain.ScheduleGroup;
import com.fileManager.schedule.domain.ScheduleGroupRepository;
import com.fileManager.schedule.domain.ScheduleSettings;
import com.fileManager.schedule.domain.ScheduleSettingsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일정(기간 묶음, docs/api 8.27~8.30). */
@Service
@Transactional
public class ScheduleGroupService {
    private static final String NOT_FOUND = "일정을 찾을 수 없습니다.";
    private static final String NAME_EMPTY = "일정 이름을 입력하세요.";
    private static final String NAME_TOO_LONG = "일정 이름은 20자까지 쓸 수 있습니다.";
    private static final String START_EMPTY = "시작일을 입력하세요.";
    private static final String END_EMPTY = "종료일을 입력하세요.";
    private static final String PERIOD = "종료일은 시작일과 같거나 뒤여야 합니다.";
    private static final int NAME_MAX = 20;

    private final ScheduleGroupRepository groups;
    private final ScheduleSettingsRepository settings;

    ScheduleGroupService(ScheduleGroupRepository groups, ScheduleSettingsRepository settings) {
        this.groups = groups;
        this.settings = settings;
    }

    /** 검사를 통과한 이름(trim한 값)과 기간. */
    private record Input(String name, LocalDate start, LocalDate end) {}

    @Transactional(readOnly = true)
    public List<ScheduleGroup> list() {
        return groups.findAllByOrderByStartDateAscEndDateAscCreatedAtAscIdAsc();
    }

    /** 검사를 모두 통과한 뒤에만 설정 행을 잠그고 색 번호를 쓴다. 거절된 요청은 번호를 쓰지 않는다(D-32). */
    public ScheduleGroup create(String name, String start, String end) {
        Input in = validate(name, start, end);
        ScheduleSettings s = settings.findCurrentLocked().orElseThrow(ScheduleSettingsService::missing);
        return groups.save(ScheduleGroup.create(in.name(), in.start(), in.end(), s.takeColorNo(), IsoTimes.now()));
    }

    /** 대상 확인(404)을 입력 검사(400)보다 먼저 한다(8.29). 소속 발송 문서의 날짜와 비교하지 않는다(D-34). */
    public ScheduleGroup update(String idRaw, String name, String start, String end) {
        UUID id = Ids.parse(idRaw, NOT_FOUND);
        ScheduleGroup g = groups.findLockedById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        Input in = validate(name, start, end);
        g.update(in.name(), in.start(), in.end(), IsoTimes.now());
        groups.flush();
        return g;
    }

    /** 없는 id도 UUID가 아닌 값도 조용히 끝난다(204). 소속 발송 문서의 group_id는 DB가 비운다(fk_schedules_group ON DELETE SET NULL). */
    public void delete(String idRaw) {
        UUID id = Ids.parseOrNull(idRaw);
        if (id == null) return;
        groups.findLockedById(id).ifPresent(groups::delete);
    }

    /** 8.27~8.30 공통 표 ①~⑥ 순서로 처음 걸린 것 하나를 알린다. 날짜는 trim하지 않는다. */
    private static Input validate(String name, String start, String end) {
        String n = Texts.trim(name);
        if (n == null || n.isEmpty()) throw ApiException.badRequest(NAME_EMPTY);
        if (n.length() > NAME_MAX) throw ApiException.badRequest(NAME_TOO_LONG);
        if (start == null || start.isEmpty()) throw ApiException.badRequest(START_EMPTY);
        if (end == null || end.isEmpty()) throw ApiException.badRequest(END_EMPTY);
        LocalDate s = ScheduleDates.parse(start);
        LocalDate e = ScheduleDates.parse(end);
        if (s == null || e == null) throw ApiException.badRequest(ScheduleDates.FORMAT_MESSAGE);
        if (s.isAfter(e)) throw ApiException.badRequest(PERIOD);
        return new Input(n, s, e);
    }
}
