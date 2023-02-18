package com.example.demo.github.api.service;

import com.example.demo.github.api.model.PomModelWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class GithubRepoServiceOld {

    private static final String PATH_SEPARATOR = "/";

    private final GitHub gitHubClient;

    public List<String> getDependentServices(final String requestedGroupId, final String requestedArtifactId) {
        final Map<String, GHRepository> ghOrganizationRepositories;
        try {
            GitHub github = new GitHubBuilder().withOAuthToken("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").build();
            log.trace("github: {}", github);
//            final GHContentSearchBuilder filename = github.searchContent().q("org:baas-devops-hdfc+filename:pom.xml").filename("pom.xml");
//            filename.list();
            final GHOrganization ghOrganization = github.getOrganization("baas-devops-hdfc");
            ghOrganizationRepositories = ghOrganization.getRepositories();
        } catch (Exception ex) {
            log.error("Error occurred while calling github api.", ex);
            return Collections.emptyList();
        }

//            final String requestedGroupId = "com.backbase.hdfc";
//            final String requestedArtifactId = "obp-clients";

        log.trace("requestedGroupId: {}", requestedGroupId);
        log.trace("requestedArtifactId: {}", requestedArtifactId);

        final List<String> repoList = ghOrganizationRepositories.values().parallelStream()
                .filter(ghRepository -> {
                    log.trace("repo name: {}", ghRepository.getName());
                    final Model pomModel;
                    try {

                        /*
                         * TODO: this will not work for modelbank-bb-identity where nested pom.xml files are present
                         *
                         */

                        final GHContent fileContent = ghRepository.getFileContent("pom.xml", "develop");

                        log.trace("fileContent: {}", fileContent);

                        final MavenXpp3Reader reader = new MavenXpp3Reader();
                        pomModel = reader.read(fileContent.read());
                        log.trace("pomModel: {}", pomModel);
                    } catch (FileNotFoundException fnx) {
                        log.trace("pom.xml file not found. repo: {}", ghRepository.getName());
                        return false;
                    } catch (XmlPullParserException | IOException e) {
                        log.error("Error occurred while reading pom.xml from repo: {}", ghRepository.getName(), e);
                        return false;
                    }

                    final boolean isDependencyAvailable = isDependencyAvailable(pomModel, requestedGroupId, requestedArtifactId);
                    log.trace("isDependencyAvailable: {}", isDependencyAvailable);
                    return isDependencyAvailable;
                })
                .map(GHRepository::getName)
                .collect(Collectors.toList());

        log.trace("repoList: {}", repoList);

        return repoList;

    }

    public List<String> getDependentServicesV2(final String requestedGroupId, final String requestedArtifactId) {
        final Map<String, GHRepository> ghOrganizationRepositories;
        try {
            GitHub github = new GitHubBuilder().withOAuthToken("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").build();
            log.trace("github: {}", github);
//            final GHContentSearchBuilder filename = github.searchContent().q("org:baas-devops-hdfc+filename:pom.xml").filename("pom.xml");
//            filename.list();
            final GHOrganization ghOrganization = github.getOrganization("baas-devops-hdfc");
            ghOrganizationRepositories = ghOrganization.getRepositories();
        } catch (Exception ex) {
            log.error("Error occurred while calling github api.", ex);
            return Collections.emptyList();
        }

//            final String requestedGroupId = "com.backbase.hdfc";
//            final String requestedArtifactId = "obp-clients";

        log.trace("requestedGroupId: {}", requestedGroupId);
        log.trace("requestedArtifactId: {}", requestedArtifactId);

        final List<String> repoList = ghOrganizationRepositories.values().parallelStream()
                .filter(ghRepository -> {
                    log.trace("repo name: {}", ghRepository.getName());

                    final List<GHContent> ghContentList;
                    try {
                        ghContentList = ghRepository.getDirectoryContent("", ghRepository.getDefaultBranch());
                    } catch (IOException e) {
                        log.warn("Error occurred while reading directory content from repo: {}", ghRepository.getName());
                        return false;
                    }

                    return ghContentList.stream()
                            .filter(ghContent -> {
                                log.trace("content type: {}", ghContent.getType());
                                return "file".equals(ghContent.getType());
                            })
                            .filter(ghContent -> {
                                log.trace("content name: {}", ghContent.getName());
                                return "pom.xml".equals(ghContent.getName());
                            })
                            .map(ghContent -> {
                                try {
                                    final GHContent fileContent = ghRepository.getFileContent("pom.xml", "develop");

                                    log.trace("fileContent: {}", fileContent);

                                    final MavenXpp3Reader reader = new MavenXpp3Reader();
                                    final PomModelWrapper pomModelWrapper = new PomModelWrapper(reader.read(fileContent.read()));
                                    log.trace("pomModel: {}", pomModelWrapper.getPomModel());
                                    return pomModelWrapper;
                                } catch (FileNotFoundException fnx) {
                                    log.warn("pom.xml file not found. repo: {}", ghRepository.getName());
                                    return new PomModelWrapper(null);
                                } catch (XmlPullParserException | IOException e) {
                                    log.error("Error occurred while reading pom.xml from repo: {}", ghRepository.getName(), e);
                                    return new PomModelWrapper(null);
                                }
                            })
                            .map(PomModelWrapper::getPomModel)
                            .filter(Objects::nonNull)
                            .anyMatch(pomModel -> {
                                final boolean isDependencyAvailable = isDependencyAvailable(pomModel, requestedGroupId, requestedArtifactId);
                                log.trace("isDependencyAvailable: {}", isDependencyAvailable);
                                return isDependencyAvailable;
                            });
                })
                .map(GHRepository::getName)
                .collect(Collectors.toList());

        log.trace("repoList: {}", repoList);

        return repoList;

    }

    private boolean isDependencyAvailable(final Model pomModel, final String requestedGroupId, final String requestedArtifactId) {
//      final String version = "1.0.7-67.0"; // version value can be ${property_name}

        final boolean isRequestedDependencyAvailable = pomModel.getDependencies().parallelStream()
                .filter(dependency -> dependency.getGroupId().equals(requestedGroupId))
                .anyMatch(dependency -> dependency.getArtifactId().equals(requestedArtifactId));

        final boolean isRequestedDependencyAvailableInPlugins =
                Objects.nonNull(pomModel.getBuild())
                        && pomModel.getBuild().getPlugins().stream()
                        .filter(plugin -> plugin.getGroupId().equals("org.apache.maven.plugins"))
                        .filter(plugin -> plugin.getArtifactId().equals("maven-dependency-plugin"))
                        .flatMap(plugin -> plugin.getExecutions().stream())
                        .filter(pluginExecution -> {
                            final Object configuration = pluginExecution.getConfiguration();
                            if (Objects.isNull(configuration) || !TypeUtils.isInstance(configuration, Xpp3Dom.class)) {
                                return false;
                            }
                            final Xpp3Dom pluginExecutionConfiguration = (Xpp3Dom) configuration;
                            final Xpp3Dom[] artifactItems = pluginExecutionConfiguration.getChildren("artifactItems");
                            return ArrayUtils.isNotEmpty(artifactItems);
                        })
                        .flatMap(pluginExecution -> Arrays.stream(((Xpp3Dom) pluginExecution.getConfiguration()).getChildren("artifactItems")))
                        .filter(xpp3Dom -> ArrayUtils.isNotEmpty(xpp3Dom.getChildren("artifactItem")))
                        .flatMap(xpp3Dom -> Arrays.stream(xpp3Dom.getChildren("artifactItem")))
                        .filter(xpp3Dom -> {
                            final Xpp3Dom[] groupIds = xpp3Dom.getChildren("groupId");
                            return ArrayUtils.isNotEmpty(groupIds)
                                    && requestedGroupId.equals(groupIds[0].getValue());
                        })
                        .anyMatch(xpp3Dom -> {
                            final Xpp3Dom[] artifactIds = xpp3Dom.getChildren("artifactId");
                            return ArrayUtils.isNotEmpty(artifactIds)
                                    && requestedArtifactId.equals(artifactIds[0].getValue());
                        });

        log.trace("isRequestedDependencyAvailable: {}", isRequestedDependencyAvailable);
        log.trace("isRequestedDependencyAvailableInPlugins: {}", isRequestedDependencyAvailableInPlugins);

        return isRequestedDependencyAvailable || isRequestedDependencyAvailableInPlugins;
    }

}