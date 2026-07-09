package org.jetbrains.plugins.scala.reposearch.scalaDirective

import ScalaDirectiveDependencyCoordinatesCompletionProvider.{clean, toArtifactStringWithoutVersion}

import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.repository.search.completion.api.*
import org.jetbrains.plugins.scala.extensions.NonNullObjectExt
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.ArtifactIdSuffix
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.reposearch.common.ScalaDependencyCompletionProvider
import org.jetbrains.plugins.scala.reposearch.common.ScalaDependencyCompletionProvider.LookupText
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveDependencyCompletionProviderBase.DependencyCompletionParameters
import org.jetbrains.plugins.scalaDirective.lang.completion.lookups.ScalaDirectiveDependencyLookupItem
import org.jetbrains.plugins.scalaDirective.lang.completion.{ScalaDirectiveDependencyCompletionContributorBase, ScalaDirectiveDependencyCompletionProviderBase}

import javax.swing.Icon

final class ScalaDirectiveDependencyCoordinatesCompletionContributor extends ScalaDirectiveDependencyCompletionContributorBase {
  protected def provider: ScalaDirectiveDependencyCompletionProviderBase = new ScalaDirectiveDependencyCoordinatesCompletionProvider
}

final class ScalaDirectiveDependencyCoordinatesCompletionProvider
  extends ScalaDirectiveDependencyCompletionProviderBase
    with ScalaDependencyCompletionProvider[DependencyCompletionParameters] {
  override protected def addCompletions(params: DependencyCompletionParameters, resultSet: CompletionResultSet): Unit =
    params.currentTokenIdx match {
      case 0 => // group id
        val groupIdWithDummy = params.currentToken
        val groupId = clean(groupIdWithDummy)
        if (groupId.nonEmpty) {
          suggestCompletionOnGroupId(params, groupId)
        }
      case 1 => // artifact
        val groupId = params.tokens.head
        val artifactIdWithDummy = params.currentToken
        val artifactId = clean(artifactIdWithDummy)
        suggestCompletionOnArtifactId(params, groupId, artifactId)
      case _ =>
      // either `2`, a version, will be handled by ScalaDirectiveDependencyVersionCompletionProvider
      // or doesn't look like a dependency, do nothing
    }

  override protected def lookupStringOnGroupId(params: DependencyCompletionParameters, result: DependencyCompletionResult): Option[LookupText] =
    Option.when(DependencyUtil.isArtifactCompatible(params, result.getArtifactId)) {
      LookupText(toArtifactStringWithoutVersion(result))
    }

  override protected def lookupStringOnArtifactId(params: DependencyCompletionParameters, groupId: String, result: DependencyPartCompletionResult): Option[LookupText] =
    Option.when(DependencyUtil.isArtifactCompatible(params, result.getResult)) {
      LookupText(toArtifactStringWithoutVersion(groupId, result))
    }

  override protected def createLookupItem(params: DependencyCompletionParameters, lookupText: LookupText, icon: Icon): LookupElement =
    ScalaDirectiveDependencyLookupItem(lookupText.lookupString, params.valueKind, scheduleAutoPopupAfterInsert = true, icon = Some(icon))

  override protected def finishDependencySuggestions(params: DependencyCompletionParameters): Unit = params.resultSet.stopHere()
}

object ScalaDirectiveDependencyCoordinatesCompletionProvider {
  /**
   * group:artifact   -> group:artifact
   * group::artifact  -> group:artifact_3, group:artifact_2.12, etc.
   * group:::artifact -> group:artifact_3.3.0, group:artifact_2.12.15, group:artifact_2.13.0-RC3, etc.
   */
  private def patchArtifactId(artifactId: String): String =
    DependencyUtil.splitScalaArtifactIdSuffix(artifactId) match {
      case (baseArtifactId, ArtifactIdSuffix.ScalaVersion) => s":$baseArtifactId"
      case (baseArtifactId, ArtifactIdSuffix.FullScalaVersion) => s"::$baseArtifactId"
      case (id, ArtifactIdSuffix.Empty) => id
    }

  private def toArtifactStringWithoutVersion(result: DependencyCompletionResult): String = {
    val artifactId = patchArtifactId(result.getArtifactId)
    s"${result.getGroupId}:$artifactId:"
  }

  private def toArtifactStringWithoutVersion(groupId: String, artifactResult: DependencyPartCompletionResult): String = {
    val artifactId = patchArtifactId(artifactResult.getResult)
    s"$groupId:$artifactId:"
  }

  private def clean(text: String): String = {
    val idx = text.indexOf(DUMMY_IDENTIFIER_TRIMMED)
    text.pipeIf(idx >= 0)(_.substring(0, idx))
  }
}
