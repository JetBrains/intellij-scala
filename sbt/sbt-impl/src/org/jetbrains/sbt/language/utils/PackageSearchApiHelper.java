package org.jetbrains.sbt.language.utils;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jetbrains.idea.reposearch.SearchParameters;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PackageSearchApiHelper {
    private static String formatString(String str) {
        return str.replaceAll("^\"+|\"+$", "");
    }

    public static Promise<Integer> searchFullTextDependency(String groupId,
                                                            String artifactId,
                                                            DependencySearchService service,
                                                            SearchParameters searchParameters,
                                                            ConcurrentLinkedDeque<MavenRepositoryArtifactInfo> cld,
                                                            Boolean fillArtifact) {
        String finalGroupId = formatString(groupId);
        String finalArtifactId = formatString(artifactId);
        String textSearch = String.format("%s %s", finalGroupId, finalArtifactId);
        if (fillArtifact)
            return service.suggestPrefix(finalGroupId, finalArtifactId, searchParameters, repo -> {
                if (repo instanceof MavenRepositoryArtifactInfo tempRepo) {
                    if (tempRepo.getGroupId().equals(finalGroupId))
                        cld.add((MavenRepositoryArtifactInfo) repo);
                }
            });
        else
            return service.fulltextSearch(textSearch, searchParameters, repo -> {
                if (repo instanceof MavenRepositoryArtifactInfo) {
                    cld.add((MavenRepositoryArtifactInfo) repo);
                }
            });
    }

    public static SearchParameters createSearchParameters(CompletionParameters params) {
        return new SearchParameters(params.getInvocationCount() < 2, ApplicationManager.getApplication().isUnitTestMode());
    }

    public static void waitAndAdd(
            Promise<Integer> searchPromise,
            ConcurrentLinkedDeque<MavenRepositoryArtifactInfo> cld,
            scala.Function1<MavenRepositoryArtifactInfo, scala.Unit> handler) {

        // Return this dummy artifact info for testing purpose
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            handler.apply(new MavenRepositoryArtifactInfo("org.scalatest", "scalatest", Arrays.asList("3.0.8", "3.0.8-RC1", "3.0.8-RC2", "3.0.8-RC3", "3.0.8-RC4", "3.0.8-RC5")));
            return;
        }

        while (searchPromise.getState() == Promise.State.PENDING || !cld.isEmpty()) {
            ProgressManager.checkCanceled();
            MavenRepositoryArtifactInfo item = cld.poll();
            if (item != null) {
                handler.apply(item);
            }
        }
    }
}