package com.bugpilot.service;

import com.bugpilot.dto.ActivityResponse;
import com.bugpilot.entity.Activity;
import com.bugpilot.exception.ResourceNotFoundException;
import com.bugpilot.repository.ActivityRepository;
import com.bugpilot.repository.RepoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final RepoRepository repoRepository;

    public ActivityService(ActivityRepository activityRepository, RepoRepository repoRepository) {
        this.activityRepository = activityRepository;
        this.repoRepository = repoRepository;
    }

    public List<ActivityResponse> getActivitiesByRepository(Long repositoryId) {
        if (!repoRepository.existsById(repositoryId)) {
            throw new ResourceNotFoundException("Repository", repositoryId);
        }

        return activityRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public ActivityResponse mapToResponse(Activity activity) {
        ActivityResponse res = new ActivityResponse();
        res.setId(activity.getId());
        if (activity.getRepository() != null) {
            res.setRepositoryId(activity.getRepository().getId());
        }
        res.setActivityType(activity.getActivityType());
        res.setDescription(activity.getDescription());
        res.setActor(activity.getActor());
        res.setCreatedAt(activity.getCreatedAt());
        return res;
    }
}
