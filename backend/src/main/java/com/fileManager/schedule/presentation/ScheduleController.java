package com.fileManager.schedule.presentation;

import com.fileManager.schedule.application.ScheduleService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ScheduleController {
    private final ScheduleService service;

    ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping("/api/schedules")
    List<ScheduleResponse> list() {
        return service.list().stream().map(ScheduleResponse::from).toList();
    }

    @PostMapping("/api/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    ScheduleResponse create(@RequestBody JsonNode body) {
        ScheduleRequest in = ScheduleRequest.forCreate(body);
        return ScheduleResponse.from(service.create(in.documentId(), in.groupId(), in.startDate(),
                in.plannedSendDate(), in.memo()));
    }

    @PutMapping("/api/schedules/{id}")
    ScheduleResponse update(@PathVariable String id, @RequestBody JsonNode body) {
        ScheduleRequest in = ScheduleRequest.forUpdate(body);
        return ScheduleResponse.from(service.update(id, in.groupId(), in.startDate(), in.plannedSendDate(), in.memo()));
    }

    @DeleteMapping("/api/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        service.delete(id);
    }
}
