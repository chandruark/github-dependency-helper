package com.example.demo.github.api.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DependentServiceResponse {

    private List<DependentServiceDetails> dependentServiceDetailsList = new ArrayList<>() ;

    @Data
    public static class DependentServiceDetails{
        private String repoName;
        private List<String> requestedArtifactVersionsUsed = new ArrayList<>();
        private String url;
        private String owner;
        private String lifeCycle;
        private String description;
        private String stars;
        private String commits;
        private String language;
    }
}
