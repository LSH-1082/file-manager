package com.fileManager.schedule.application;

import com.fileManager.global.error.ApiException;
import com.fileManager.schedule.domain.ScheduleSettings;
import com.fileManager.schedule.domain.ScheduleSettingsRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일정 화면의 보기 설정(docs/api 8.31~8.32). 설정은 한 행이고 모두가 함께 쓴다(D-36). */
@Service
@Transactional
public class ScheduleSettingsService {
    /** 묶음 칸, 세로 띠. */
    private static final Set<String> LAYOUTS = Set.of("group", "band");

    private final ScheduleSettingsRepository settings;

    ScheduleSettingsService(ScheduleSettingsRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public ScheduleSettings get() {
        return settings.findCurrent().orElseThrow(ScheduleSettingsService::missing);
    }

    /** layout은 공백을 지우지 않고 대소문자를 구분해 정확히 같아야 한다. 다음 색 번호는 바꾸지 않는다. */
    public ScheduleSettings update(String layout) {
        if (!LAYOUTS.contains(layout)) throw ApiException.format();
        ScheduleSettings s = settings.findCurrentLocked().orElseThrow(ScheduleSettingsService::missing);
        s.changeLayout(layout);
        return s;
    }

    /** 설정 행은 V3 마이그레이션이 만든다. 없으면 DB가 잘못된 것이므로 500이 된다. */
    static IllegalStateException missing() {
        return new IllegalStateException("schedule_settings 행(id 1)이 없습니다.");
    }
}
