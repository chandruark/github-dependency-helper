package com.example.demo.github.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class GithubApiConfig {

    private final GithubPropertyConfig githubPropertyConfig;

    @Bean
    public GitHub getGithubClient() throws IOException {
        try {
            final GitHub github = new GitHubBuilder()
                    .withOAuthToken(githubPropertyConfig.getToken())
                    .build();
            log.trace("Configured github client: {}", github);
            return github;
        } catch (IOException e) {
            log.error("Error occurred while configuring github api client.", e);
            throw e;
        }
    }
}
