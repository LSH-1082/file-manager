package com.fileManager.schedule.presentation;

import com.fileManager.schedule.application.ScheduleGroupService;
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
public class ScheduleGroupController {
    private final ScheduleGroupService service;

    ScheduleGroupController(ScheduleGroupService service) {
        this.service = service;
    }

    @GetMapping("/api/schedule-groups")
    List<ScheduleGroupResponse> list() {
        return service.list().stream().map(ScheduleGroupResponse::from).toList();
    }

    @PostMapping("/api/schedule-groups")
    @ResponseStatus(HttpStatus.CREATED)
    ScheduleGroupResponse create(@RequestBody JsonNode body) {
        ScheduleGroupRequest in = ScheduleGroupRequest.from(body);
        return ScheduleGroupResponse.from(service.create(in.name(), in.startDate(), in.endDate()));
    }

    @PutMapping("/api/schedule-groups/{id}")
    ScheduleGroupResponse update(@PathVariable String id, @RequestBody JsonNode body) {
        ScheduleGroupRequest in = ScheduleGroupRequest.from(body);
        return ScheduleGroupResponse.from(service.update(id, in.name(), in.startDate(), in.endDate()));
    }

    @DeleteMapping("/api/schedule-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        service.delete(id);
    }
}
