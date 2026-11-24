package com.fileManager.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 일정 화면 설정. 항상 한 행(id 1)이고 V3 마이그레이션이 만든다. 다음에 등록할 일정의 색 번호도 여기에 둔다. */
@Entity
@Table(name = "schedule_settings")
public class ScheduleSettings {
    /** 일정 배경색 개수. 색 번호는 0~5다(D-32). */
    private static final int COLOR_COUNT = 6;

    @Id
    private Integer id;

    @Column(nullable = false, length = 10)
    private String layout;

    @Column(name = "next_color_no", nullable = false)
    private int nextColorNo;

    protected ScheduleSettings() {}

    public void changeLayout(String layout) {
        this.layout = layout;
    }

    /** 지금 색 번호를 돌려주고 다음 번호로 넘긴다(5 다음은 0). */
    public int takeColorNo() {
        int taken = nextColorNo;
        nextColorNo = (taken + 1) % COLOR_COUNT;
        return taken;
    }

    public String getLayout() { return layout; }
}
