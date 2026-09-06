package com.bugpilot.dto;

import jakarta.validation.constraints.NotBlank;

public class RepositoryRequest {

    @NotBlank(message = "Owner is required")
    private String owner;

    @NotBlank(message = "Repository name is required")
    private String name;

    private String description;

    private String githubUrl;

    public RepositoryRequest() {
    }

    public RepositoryRequest(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public RepositoryRequest(String owner, String name, String githubUrl, String description) {
        this.owner = owner;
        this.name = name;
        this.githubUrl = githubUrl;
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getHtmlUrl() {
        return githubUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        if (this.githubUrl == null || this.githubUrl.isBlank()) {
            this.githubUrl = htmlUrl;
        }
    }
}
