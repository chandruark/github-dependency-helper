package com.example.demo.github.api.controller;

import com.example.demo.github.api.model.AllServiceResponse;
import com.example.demo.github.api.model.DependentServiceResponse;
import com.example.demo.github.api.service.GithubRepoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/dependency-graph")
@RequiredArgsConstructor
public class GithubRepoController {

    private final GithubRepoService githubRepoService;

    @GetMapping(value = "/dependent-services",
            produces = {"application/json"})
    ResponseEntity<DependentServiceResponse> getDependentServices(@RequestParam(value = "groupId", required = true)
                                                      @NotNull final String groupId,
                                                                  @RequestParam(value = "artifactId", required = true)
                                                      @NotNull final String artifactId,
                                                                  @RequestParam(value = "repoNameContains", required = false)
                                                      final String repoNameContains) {

        return ResponseEntity.ok(githubRepoService.getDependentServices(groupId, artifactId, repoNameContains));
    }

    @GetMapping(value = "/all-services",
            produces = {"application/json"})
    ResponseEntity<List<String>> getAllServices(@RequestParam(value = "repoNameContains", required = false)
                                                      final String repoNameContains) {

        return ResponseEntity.ok(githubRepoService.getAllServices(repoNameContains));
    }

    @GetMapping(value = "/organizations",
            produces = {"application/json"})
    ResponseEntity<List<String>> getAllOrganizations(@RequestParam(value = "organizationNameContains", required = false)
                                                final String organizationNameContains) {

        return ResponseEntity.ok(githubRepoService.getAllOrganizations(organizationNameContains));
    }
}
