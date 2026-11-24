package com.fileManager.schedule.application;

import com.fileManager.category.domain.Category;
import com.fileManager.document.domain.Document;
import com.fileManager.document.domain.DocumentRepository;
import com.fileManager.global.error.ApiException;
import com.fileManager.global.util.Ids;
import com.fileManager.global.util.IsoTimes;
import com.fileManager.global.util.Texts;
import com.fileManager.schedule.domain.Schedule;
import com.fileManager.schedule.domain.ScheduleGroup;
import com.fileManager.schedule.domain.ScheduleGroupRepository;
import com.fileManager.schedule.domain.ScheduleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발송 문서(docs/api 8.23~8.26). 응답에 담을 문서 상태(제목·카테고리·단계)는 조회 시점에 문서에서 읽는다. */
@Service
@Transactional
public class ScheduleService {
    private static final String DOC_NOT_FOUND = "문서를 찾을 수 없습니다.";
    private static final String NOT_FOUND = "발송 문서를 찾을 수 없습니다.";
    private static final String GROUP_NOT_FOUND = "일정을 찾을 수 없습니다.";
    private static final String START_EMPTY = "시작일을 입력하세요.";
    private static final String PLANNED_EMPTY = "발송 예정일을 입력하세요.";
    private static final String PERIOD = "발송 예정일은 시작일과 같거나 뒤여야 합니다.";
    private static final String MEMO_TOO_LONG = "메모는 20자까지 쓸 수 있습니다.";
    private static final int MEMO_MAX = 20;

    private final ScheduleRepository schedules;
    private final ScheduleGroupRepository groups;
    private final DocumentRepository documents;

    ScheduleService(ScheduleRepository schedules, ScheduleGroupRepository groups, DocumentRepository documents) {
        this.schedules = schedules;
        this.groups = groups;
        this.documents = documents;
    }

    /** 검사를 통과한 날짜·메모. memo는 trim한 값이고 비었으면 null. */
    private record Input(LocalDate start, LocalDate planned, String memo) {}

    /** readOnly로 두지 않는다(DocumentService.list와 같이 문서 공유 잠금 쿼리를 쓴다). */
    public List<ScheduleView> list() {
        List<Schedule> all = schedules.findAllByOrderByStartDateAscPlannedSendDateAscCreatedAtAscIdAsc();
        if (all.isEmpty()) return List.of();
        Map<UUID, Document> docs = documents.findAllLockedForList().stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        List<ScheduleView> out = new ArrayList<>(all.size());
        for (Schedule s : all) {
            Document d = docs.get(s.getDocumentId());
            if (d == null) continue; // FK상 생기지 않지만 생기면 건너뛴다
            out.add(view(s, d));
        }
        return out;
    }

    /**
     * 검사 순서는 날짜·메모 → 문서 → 일정이다(8.24). groupIdRaw가 null이면 일정과 관계없는 문서다.
     * 일정 행은 공유 잠금으로 확인해 저장 전에 지워지지 않게 한다(잠금 순서 documents -> schedule_groups, docs/db 5절).
     * 날짜를 일정 기간과 비교하지 않는다(D-34).
     */
    public ScheduleView create(String documentIdRaw, String groupIdRaw, String start, String planned, String memo) {
        Input in = validate(start, planned, memo);
        UUID documentId = Ids.parse(documentIdRaw, DOC_NOT_FOUND);
        Document d = documents.findLockedById(documentId).orElseThrow(() -> ApiException.notFound(DOC_NOT_FOUND));
        UUID groupId = groupIdRaw == null ? null : lockedGroupId(groupIdRaw);
        Schedule s = schedules.save(
                Schedule.create(d.getId(), groupId, in.start(), in.planned(), in.memo(), IsoTimes.now()));
        return view(s, d);
    }

    /** UUID가 아니거나(빈 문자열 포함) 없는 일정이면 404. */
    private UUID lockedGroupId(String raw) {
        UUID id = Ids.parse(raw, GROUP_NOT_FOUND);
        return groups.findSharedById(id).map(ScheduleGroup::getId)
                .orElseThrow(() -> ApiException.notFound(GROUP_NOT_FOUND));
    }

    /** 대상 확인(404)을 입력 검사(400)보다, 입력 검사를 일정 확인(404)보다 먼저 한다(8.25). groupIdRaw가 null이면 일정과 관계없는 문서가 된다. */
    public ScheduleView update(String idRaw, String groupIdRaw, String start, String planned, String memo) {
        UUID id = Ids.parse(idRaw, NOT_FOUND);
        Schedule found = schedules.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        Document d = documents.findLockedById(found.getDocumentId())
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        // 문서를 잠근 뒤 다시 읽는다. 그사이 문서가 지워져 CASCADE됐으면 없다.
        Schedule s = schedules.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        Input in = validate(start, planned, memo);
        UUID groupId = groupIdRaw == null ? null : lockedGroupId(groupIdRaw);
        s.update(groupId, in.start(), in.planned(), in.memo(), IsoTimes.now());
        schedules.flush();
        return view(s, d);
    }

    /** 없는 id도 UUID가 아닌 값도 조용히 끝난다(204). 문서는 잠그지 않는다(db 6.3 deleteSchedule). */
    public void delete(String idRaw) {
        UUID id = Ids.parseOrNull(idRaw);
        if (id == null) return;
        schedules.deleteById(id);
    }

    /** 8.23~8.26 공통 표 ①~⑤ 순서로 처음 걸린 것 하나를 알린다. 날짜는 trim하지 않는다. */
    private static Input validate(String start, String planned, String memo) {
        if (start == null || start.isEmpty()) throw ApiException.badRequest(START_EMPTY);
        if (planned == null || planned.isEmpty()) throw ApiException.badRequest(PLANNED_EMPTY);
        LocalDate s = ScheduleDates.parse(start);
        LocalDate p = ScheduleDates.parse(planned);
        if (s == null || p == null) throw ApiException.badRequest(ScheduleDates.FORMAT_MESSAGE);
        if (s.isAfter(p)) throw ApiException.badRequest(PERIOD);
        String m = Texts.trim(memo);
        if (m != null && m.length() > MEMO_MAX) throw ApiException.badRequest(MEMO_TOO_LONG);
        return new Input(s, p, m == null || m.isEmpty() ? null : m);
    }

    private static ScheduleView view(Schedule s, Document d) {
        Category c = d.getCategory();
        return new ScheduleView(s, d.getTitle(), c == null ? null : c.getId(), d.latest().stage());
    }
}
