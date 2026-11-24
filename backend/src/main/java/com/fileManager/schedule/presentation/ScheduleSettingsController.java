package com.fileManager.schedule.presentation;

import com.fileManager.schedule.application.ScheduleSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ScheduleSettingsController {
    private final ScheduleSettingsService service;

    ScheduleSettingsController(ScheduleSettingsService service) {
        this.service = service;
    }

    @GetMapping("/api/schedule-settings")
    ScheduleSettingsResponse get() {
        return ScheduleSettingsResponse.from(service.get());
    }

    @PutMapping("/api/schedule-settings")
    ScheduleSettingsResponse update(@RequestBody JsonNode body) {
        ScheduleSettingsRequest in = ScheduleSettingsRequest.from(body);
        return ScheduleSettingsResponse.from(service.update(in.layout()));
    }
}
