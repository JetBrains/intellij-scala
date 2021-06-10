package org.jetbrains.sbt.language.utils;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jetbrains.idea.reposearch.RepositoryArtifactData;
import org.jetbrains.idea.reposearch.SearchParameters;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PackageSearchApiHelper {
    public static Promise<Integer> searchGroupArtifact(String groupId,
                                                       String artifactId,
                                                       DependencySearchService service,
                                                       SearchParameters searchParameters,
                                                       ConcurrentLinkedDeque<MavenRepositoryArtifactInfo> cld) {
        if (artifactId == null) {
            return service.fulltextSearch(groupId, searchParameters, repo -> {
                if (repo instanceof MavenRepositoryArtifactInfo) {
                    cld.add((MavenRepositoryArtifactInfo) repo);
                }
            });
        }
        else {
            return service.suggestPrefix(groupId, artifactId, searchParameters, repo -> {
                if (repo instanceof MavenRepositoryArtifactInfo) {
                    cld.add((MavenRepositoryArtifactInfo) repo);
                }
            });
        }

    }

    public static Promise<Integer> searchFullTextDependency(String groupId,
                                                            String artifactId,
                                                            DependencySearchService service,
                                                            SearchParameters searchParameters,
                                                            ConcurrentLinkedDeque<MavenRepositoryArtifactInfo> cld) {
        String textSearch = "";
        if (!groupId.equals("")) textSearch = String.format("%s:%s", groupId, artifactId);
        else textSearch = artifactId;
        return service.fulltextSearch(textSearch, searchParameters, repo -> {
            if (repo instanceof MavenRepositoryArtifactInfo) {
                cld.add((MavenRepositoryArtifactInfo) repo);
            }
        });

    }

    public static SearchParameters createSearchParameters(CompletionParameters params) {
        return new SearchParameters(params.getInvocationCount() < 2, ApplicationManager.getApplication().isUnitTestMode());
    }
}