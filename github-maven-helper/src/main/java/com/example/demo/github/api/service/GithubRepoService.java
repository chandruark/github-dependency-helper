package com.example.demo.github.api.service;

import com.example.demo.github.api.model.AllServiceResponse;
import com.example.demo.github.api.model.DependentServiceResponse;
import com.example.demo.github.api.model.PomModelWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.example.demo.github.api.config.Constants.LIFECYCLE_PROD;
import static com.example.demo.github.api.config.Constants.SINCE_YEAR;
import static com.example.demo.github.api.config.Constants.SINCE_MONTH;
import static com.example.demo.github.api.config.Constants.SINCE_DATE;

@Service
@AllArgsConstructor
@Slf4j
public class GithubRepoService {

    private static final String PATH_SEPARATOR = "/";

    private final GitHub gitHubClient;

    public DependentServiceResponse getDependentServices(final String requestedGroupId, final String requestedArtifactId, final String repoNameContains) {
        Calendar cal = Calendar.getInstance();
        cal.set(SINCE_YEAR, SINCE_MONTH, SINCE_DATE);
        final Date since = cal.getTime();

        DependentServiceResponse dependentServiceResponse = new DependentServiceResponse();

        final Map<String, GHRepository> ghOrganizationRepositories;
        try {
            final GHOrganization ghOrganization = gitHubClient.getOrganization("baas-devops-hdfc");
            ghOrganizationRepositories = ghOrganization.getRepositories();
        } catch (Exception ex) {
            log.error("Error occurred while fetching repo list from github api.", ex);
            return dependentServiceResponse;
        }

        log.trace("requestedGroupId: {}", requestedGroupId);
        log.trace("requestedArtifactId: {}", requestedArtifactId);

        // TODO: parallelStream() => ExecutorService with future
        ghOrganizationRepositories.values().parallelStream().filter(ghRepository -> {
            if (StringUtils.isBlank(repoNameContains)) {
                return true;
            }
            return ghRepository.getName().contains(repoNameContains);
        }).forEach(ghRepository -> {
            log.trace("repo name: {}", ghRepository.getName());

            final String pathFromRoot = "";

            final List<Model> pomModelList = fetchPomModelsFromDirTree(ghRepository, pathFromRoot);

            log.trace("repoName: {}, pomModelList: {}", ghRepository.getName(), pomModelList);

            // TODO: parallelStream() => ExecutorService with future
                pomModelList.parallelStream().forEach(pomModel -> {

                final List<String> versionsList = new ArrayList<>();
                final boolean isDependencyAvailable = isDependencyAvailable(pomModel, requestedGroupId, requestedArtifactId, versionsList);

                if(isDependencyAvailable) {
                    log.trace("isDependencyAvailable: {}", isDependencyAvailable);
                    log.trace("versionUsedForRequestedArtifact: {}", versionsList);

                    final DependentServiceResponse.DependentServiceDetails dependentServiceDetails = new DependentServiceResponse.DependentServiceDetails();

                    GHCommitQueryBuilder queryBuilder = ghRepository.queryCommits().since(since);
                    PagedIterable<GHCommit> commits = queryBuilder.list();
                    try {
                        dependentServiceDetails.setCommits(String.valueOf(commits.toList().size()));
                    } catch (IOException e) {
                        dependentServiceDetails.setCommits(StringUtils.EMPTY);
                    }

                    dependentServiceDetails.setRepoName(ghRepository.getName());
                    dependentServiceDetails.getRequestedArtifactVersionsUsed().addAll(versionsList);
                    dependentServiceDetails.setUrl(ghRepository.getHtmlUrl().toString());
                    dependentServiceDetails.setOwner(ghRepository.getOwnerName());
                    dependentServiceDetails.setLifeCycle(LIFECYCLE_PROD);
                    dependentServiceDetails.setLanguage(ghRepository.getLanguage());
                    dependentServiceDetails.setDescription(ghRepository.getDescription());

                    dependentServiceResponse.getDependentServiceDetailsList().add(dependentServiceDetails);
                }
            });
        });

        log.trace("repoMap: {}", dependentServiceResponse);

        return dependentServiceResponse;

    }

    private List<Model> fetchPomModelsFromDirTree(final GHRepository ghRepository, final String pathFromRoot) {

        log.trace("fetchPomModelsFromDirTree(): repoName: {}, pathFromRoot: {}", ghRepository.getName(), pathFromRoot);

        List<Model> pomModelList = new ArrayList<>();

        boolean isApplicableSubDirAvailable;


        final List<GHContent> ghContentList;
        try {
            ghContentList = ghRepository.getDirectoryContent(pathFromRoot, ghRepository.getDefaultBranch());
        } catch (IOException e) {
            log.warn("Error occurred while reading directory content from repo: {}", ghRepository.getName());
            return Collections.emptyList();
        }

        final Model pomModelFromRoot = ghContentList.stream().filter(ghContent -> "file".equals(ghContent.getType())).filter(ghContent -> "pom.xml".equals(ghContent.getName())).findFirst().map(ghContent -> {
            final PomModelWrapper pomModelWrapper = fetchPomModelWrapper(ghRepository, pathFromRoot + PATH_SEPARATOR + "pom.xml");
            return pomModelWrapper.getPomModel();
        }).orElse(null);

        if (Objects.isNull(pomModelFromRoot)) {
            return Collections.emptyList();
        }

        pomModelList.add(pomModelFromRoot);

        final boolean isSrcDirAvailable = ghContentList.stream().filter(ghContent -> {
            log.trace("repo name: {}, pathFromRoot: {}, content type: {}", ghRepository.getName(), pathFromRoot, ghContent.getType());
            return "dir".equals(ghContent.getType());
        }).anyMatch(ghContent -> {
            log.trace("repo name: {}, pathFromRoot: {}, content name: {}", ghRepository.getName(), pathFromRoot, ghContent.getName());
            return "src".equals(ghContent.getName());
        });

        final boolean isSubDirAvailable = ghContentList.stream().anyMatch(ghContent -> {
            log.trace("repo name: {}, pathFromRoot: {}, content type: {}", ghRepository.getName(), pathFromRoot, ghContent.getType());
            return "dir".equals(ghContent.getType());
        });

        isApplicableSubDirAvailable = isSubDirAvailable && (!isSrcDirAvailable);

        log.trace("repo name: {}, pathFromRoot: {}, isApplicableSubDirAvailable: {}, isSubDirAvailable: {}, isSrcDirAvailable: {}", ghRepository.getName(), pathFromRoot, isApplicableSubDirAvailable, isSubDirAvailable, isSrcDirAvailable);

        if (isApplicableSubDirAvailable) {
            ghContentList.stream().filter(ghContent -> {
                log.trace("repo name: {}, content type: {}", ghRepository.getName(), ghContent.getType());
                return "dir".equals(ghContent.getType());
            }).filter(ghContent -> {
                log.trace("repo name: {}, content name: {}", ghRepository.getName(), ghContent.getName());
                // filter non-hidden dir
                return !ghContent.getName().startsWith(".");
            }).forEach(ghContent -> {
                final String appendedPath = pathFromRoot + PATH_SEPARATOR + ghContent.getName();
                final List<Model> modelsFromChildDir = fetchPomModelsFromDirTree(ghRepository, appendedPath);
                pomModelList.addAll(modelsFromChildDir);
            });
        }


        return pomModelList;
    }

    private boolean isDependencyAvailable(final Model pomModel, final String requestedGroupId, final String requestedArtifactId, final List<String> dependencyList) {
//      final String version = "1.0.7-67.0"; // version value can be ${property_name}
        String version = "";
        final boolean isRequestedDependencyAvailable = pomModel.getDependencies().stream().filter(dependency -> dependency.getGroupId().equals(requestedGroupId)).anyMatch(dependency -> dependency.getArtifactId().equals(requestedArtifactId));

        findRequestedDependencyAvailable(pomModel, requestedArtifactId, dependencyList, isRequestedDependencyAvailable);
        log.trace("isRequestedDependencyAvailable: under Version {}", version);

        final boolean isRequestedDependencyAvailableInPlugins = Objects.nonNull(pomModel.getBuild()) && pomModel.getBuild().getPlugins().stream().filter(plugin -> plugin.getGroupId().equals("org.apache.maven.plugins")).filter(plugin -> plugin.getArtifactId().equals("maven-dependency-plugin")).flatMap(plugin -> plugin.getExecutions().stream()).filter(pluginExecution -> {
            final Object configuration = pluginExecution.getConfiguration();
            if (Objects.isNull(configuration) || !TypeUtils.isInstance(configuration, Xpp3Dom.class)) {
                return false;
            }
            final Xpp3Dom pluginExecutionConfiguration = (Xpp3Dom) configuration;
            final Xpp3Dom[] artifactItems = pluginExecutionConfiguration.getChildren("artifactItems");
            return ArrayUtils.isNotEmpty(artifactItems);
        }).flatMap(pluginExecution -> Arrays.stream(((Xpp3Dom) pluginExecution.getConfiguration()).getChildren("artifactItems"))).filter(xpp3Dom -> ArrayUtils.isNotEmpty(xpp3Dom.getChildren("artifactItem"))).flatMap(xpp3Dom -> Arrays.stream(xpp3Dom.getChildren("artifactItem"))).filter(xpp3Dom -> {
            final Xpp3Dom[] groupIds = xpp3Dom.getChildren("groupId");
            return ArrayUtils.isNotEmpty(groupIds) && requestedGroupId.equals(groupIds[0].getValue());
        }).anyMatch(xpp3Dom -> {
            final Xpp3Dom[] artifactIds = xpp3Dom.getChildren("artifactId");
            return ArrayUtils.isNotEmpty(artifactIds) && requestedArtifactId.equals(artifactIds[0].getValue());
        });

        findRequestedDependencyAvailable(pomModel, requestedArtifactId, dependencyList, isRequestedDependencyAvailableInPlugins);
        log.trace("isRequestedDependencyAvailableInPlugins: Version {}", version);

        log.trace("isRequestedDependencyAvailable: {}", isRequestedDependencyAvailable);
        log.trace("isRequestedDependencyAvailableInPlugins: {}", isRequestedDependencyAvailableInPlugins);

        return isRequestedDependencyAvailable || isRequestedDependencyAvailableInPlugins;
    }

    private void findRequestedDependencyAvailable(Model pomModel, String requestedArtifactId, List<String> dependencyList, boolean isRequestedDependencyAvailable) {
        if (isRequestedDependencyAvailable) {

            if(StringUtils.isNotEmpty(pomModel.getProperties().getProperty(requestedArtifactId.concat("-version")))){
                dependencyList.add(pomModel.getProperties().getProperty(requestedArtifactId.concat("-version")));
            }
            else if(StringUtils.isNotEmpty(pomModel.getProperties().getProperty(requestedArtifactId.concat(".version")))){
                dependencyList.add(pomModel.getProperties().getProperty(requestedArtifactId.concat(".version")));
            }
            else if(StringUtils.isNotEmpty(pomModel.getProperties().getProperty(requestedArtifactId.replace("-",".").concat(".version")))){
                dependencyList.add(pomModel.getProperties().getProperty(requestedArtifactId.replace("-",".").concat(".version")));
            }
        }
    }

    private PomModelWrapper fetchPomModelWrapper(final GHRepository ghRepository, final String pomFilePathFromRoot) {
        final String defaultBranch = ghRepository.getDefaultBranch();
        try {

            final GHContent fileContent = ghRepository.getFileContent(pomFilePathFromRoot, defaultBranch);

            log.trace("pomFilePathFromRoot: {}, fileContent: {}", pomFilePathFromRoot, fileContent);

            final MavenXpp3Reader reader = new MavenXpp3Reader();
            final PomModelWrapper pomModelWrapper = new PomModelWrapper(reader.read(fileContent.read()));
            log.trace("pomModel: {}", pomModelWrapper.getPomModel());
            return pomModelWrapper;
        } catch (FileNotFoundException fnx) {
            log.warn("pom.xml file not found. repo: {}, pomFilePathFromRoot: {}, defaultBranch: {}", ghRepository.getName(), pomFilePathFromRoot, defaultBranch);
            return new PomModelWrapper(null);
        } catch (XmlPullParserException | IOException e) {
            log.error("Error occurred while reading pom.xml from repo: {}", ghRepository.getName(), e);
            return new PomModelWrapper(null);
        }
    }

    public List<String> getAllServices(String repoNameContains) {
        final Map<String, GHRepository> ghOrganizationRepositories;
        try {
            final GHOrganization ghOrganization = gitHubClient.getOrganization("baas-devops-hdfc");
            ghOrganizationRepositories = ghOrganization.getRepositories();

            return ghOrganizationRepositories.values().stream().filter(ghRepository -> {
                if (StringUtils.isBlank(repoNameContains)) {
                    return true;
                }
                return ghRepository.getName().contains(repoNameContains);
            }).map(GHRepository::getName).collect(Collectors.toList());
        } catch (Exception ex) {
            log.error("Error occurred while fetching repo list from github api.", ex);
            return Collections.emptyList();
        }
    }

    public List<String> getAllOrganizations(String organizationName) {
        final Map<String, GHOrganization> ghOrganizationRepositories;
        try {
            ghOrganizationRepositories = gitHubClient.getMyOrganizations();

            return ghOrganizationRepositories.keySet().stream()
                    .filter(name -> {
                        if (StringUtils.isBlank(organizationName))
                            return true;

                       return name.contains(organizationName);
                    })
                    .collect(Collectors.toList());

        } catch (Exception ex) {
            log.error("Error occurred while fetching repo list from github api.", ex);
            return Collections.emptyList();
        }
    }
}