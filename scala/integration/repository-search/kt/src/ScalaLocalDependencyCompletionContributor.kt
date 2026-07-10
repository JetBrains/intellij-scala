package org.jetbrains.plugins.scala.reposearch

import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.repository.search.completion.api.*
import org.jetbrains.plugins.scala.packagesearch.util.DependencyCompletion
import org.jetbrains.plugins.scala.reposearch.common.ScalaDependencyCompletionContributor

internal class ScalaLocalDependencyCompletionContributor : ScalaDependencyCompletionContributor {
  override val source: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL

  override fun isEnabled(): Boolean = Registry.`is`("scala.dependency.completion.contributor.coursier")

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    val groups = coroutineToIndicator { DependencyCompletion.instance().getGroupIds(request.groupPrefix) }
    return groups.map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    val artifacts = coroutineToIndicator { DependencyCompletion.instance().getArtifactIds(request.group, request.artifactPrefix) }
    return artifacts.map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    val versions = coroutineToIndicator { DependencyCompletion.instance().getVersions(request.group, request.artifact, request.versionPrefix) }
    return versions.map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    val parts = request.searchString.trim().split(":")
    // Not implemented. The expected call is for incomplete groupId or artifactId without other parts only
    // E.g.: foo<caret>
    if (parts.size != 1) return emptyList()

    val searchSubstring = parts.first()
    // Search below is pretty expensive, let's avoid calling it on empty requests
    if (searchSubstring.isEmpty()) return emptyList()

    // Track processed group ids to avoid multiple API calls for the same group id
    val processedGroupIds = mutableSetOf<String>()
    val dependencyCompletion = DependencyCompletion.instance()
    val results = mutableListOf<DependencyCompletionResult>()

    // Group ids that start with the search substring: collect all of their artifacts
    val strictGroupIds = coroutineToIndicator { dependencyCompletion.getGroupIds(searchSubstring) }
    collectMatches(strictGroupIds, "", dependencyCompletion, processedGroupIds, results)

    // If the user wants to search for dependencies by artifact id or a part of group id that is not a prefix,
    // use the local ivy cache to fetch all available group ids and filter dependencies based on them
    val localGroupIds = coroutineToIndicator { dependencyCompletion.getLocalGroupIds("") }

    // Find all local group ids that contain the search substring and collect all of their artifacts
    val nonStrictGroupIds = localGroupIds.filter { it.contains(searchSubstring, ignoreCase = true) }
    collectMatches(nonStrictGroupIds, "", dependencyCompletion, processedGroupIds, results)

    // Collect artifact ids that start with the search substring across all local group ids
    collectMatches(localGroupIds, searchSubstring, dependencyCompletion, processedGroupIds, results)

    return results
  }

  /**
   * Appends completion results for the given [groupIds], querying each group id at most once (see [processedGroupIds]).
   *
   * Each coursier completion call is run via [coroutineToIndicator]: it checks the current coroutine's job
   * for cancellation on entry and installs a job-tied `ProgressIndicator` so that the blocking wait inside
   * [DependencyCompletion] is interrupted if the request becomes obsolete (e.g., the user kept typing).
   */
  private suspend fun collectMatches(
    groupIds: List<String>,
    artifactIdPrefix: String,
    dependencyCompletion: DependencyCompletion,
    processedGroupIds: MutableSet<String>,
    results: MutableList<DependencyCompletionResult>,
  ) {
    for (groupId in groupIds) {
      if (!processedGroupIds.add(groupId)) continue // avoid querying the same group id twice

      val artifacts = coroutineToIndicator { dependencyCompletion.getArtifactIds(groupId, artifactIdPrefix) }
      for (artifact in artifacts) {
        // Do not fill version, as it is supposed to be discarded by the scala completion contributors anyway
        results.add(DependencyCompletionResult(groupId, artifact, "", source = source))
      }
    }
  }
}
