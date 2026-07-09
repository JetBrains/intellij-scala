package org.jetbrains.plugins.scala.reposearch

import com.intellij.openapi.util.registry.Registry
import com.intellij.repository.search.completion.api.*
import org.jetbrains.plugins.scala.packagesearch.util.DependencyCompletion
import org.jetbrains.plugins.scala.reposearch.common.ScalaDependencyCompletionContributor

internal class ScalaLocalDependencyCompletionContributor : ScalaDependencyCompletionContributor {
  override val source: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL

  override fun isEnabled(): Boolean = Registry.`is`("scala.dependency.completion.contributor.coursier")

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    val groups = DependencyCompletion.instance().getGroupIds(request.groupPrefix)
    return groups.map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    val artifacts = DependencyCompletion.instance().getArtifactIds(request.group, request.artifactPrefix)
    return artifacts.map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    val versions = DependencyCompletion.instance().getVersions(request.group, request.artifact, request.versionPrefix)
    return versions.map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    val parts = request.searchString.trim().split(":")
    // Not implemented. The expected call is for incomplete groupId or artifactId without other parts only
    // E.g.: foo<caret>
    if (parts.size != 1) return emptyList()

    val searchSubstring = parts.getOrNull(0).orEmpty()
    // Search below is pretty expensive, let's avoid calling it on empty requests
    if (searchSubstring.isEmpty()) return emptyList()

    // Track processed group ids to avoid multiple API calls for the same group id
    val processedGroupIds = mutableSetOf<String>()
    val dependencyCompletion = DependencyCompletion.instance()

    val strictMatchesOnGroupId = dependencyCompletion.getGroupIds(searchSubstring)
      .flatMap { groupId -> searchWithGroupId(groupId, "", dependencyCompletion, processedGroupIds) }

    // If the user wants to search for dependencies by artifact id or a part of group id that is not a prefix,
    // use the local ivy cache to fetch all available group ids and filter dependencies based on them
    val localGroupIds = dependencyCompletion.getLocalGroupIds("")

    // Find all local group ids that contain the search substring and search for available artifact ids for each of them
    val nonStrictMatchesOnGroupId = localGroupIds
      .filter { it.contains(searchSubstring) }
      .flatMap { groupId -> searchWithGroupId(groupId, "", dependencyCompletion, processedGroupIds) }

    // Search for artifact ids that start with the search substring and have one of the local group ids
    val strictMatchesOnArtifactId = localGroupIds
      .flatMap { groupId -> searchWithGroupId(groupId, searchSubstring, dependencyCompletion, processedGroupIds) }

    return strictMatchesOnGroupId + nonStrictMatchesOnGroupId + strictMatchesOnArtifactId
  }

  private fun searchWithGroupId(groupId: String, artifactIdPrefix: String, dependencyCompletion: DependencyCompletion, processedGroupIds: MutableSet<String>): List<DependencyCompletionResult> {
    if (!processedGroupIds.add(groupId)) return emptyList() // optimization

    val artifacts = dependencyCompletion.getArtifactIds(groupId, artifactIdPrefix)
    return artifacts.map { artifact ->
      // Do not fill version, as it is supposed to be discarded by the scala completion contributors anyway
      DependencyCompletionResult(groupId, artifact, "", source = source)
    }
  }
}
