package com.bugpilot.controller;

import com.bugpilot.dto.ActivityResponse;
import com.bugpilot.dto.AnalyticsResponse;
import com.bugpilot.service.ActivityService;
import com.bugpilot.service.AnalyticsService;
import com.bugpilot.service.RepositoryService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/repositories/{id}")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ActivityService activityService;
    private final RepositoryService repositoryService;

    public AnalyticsController(AnalyticsService analyticsService,
                               ActivityService activityService,
                               RepositoryService repositoryService) {
        this.analyticsService = analyticsService;
        this.activityService = activityService;
        this.repositoryService = repositoryService;
    }

    @GetMapping("/analytics")
    public AnalyticsResponse getRepositoryAnalytics(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        repositoryService.getRepositoryEntityForUser(id, email);
        return analyticsService.getRepositoryAnalytics(id);
    }

    @GetMapping("/activities")
    public List<ActivityResponse> getRepositoryActivities(@PathVariable Long id, Principal principal) {
        String email = principal != null ? principal.getName() : null;
        repositoryService.getRepositoryEntityForUser(id, email);
        return activityService.getActivitiesByRepository(id);
    }
}
