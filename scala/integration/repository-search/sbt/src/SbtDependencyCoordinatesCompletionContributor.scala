package org.jetbrains.plugins.scala.reposearch.sbt

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.repository.search.completion.api.{DependencyCompletionResult, DependencyPartCompletionResult}
import org.jetbrains.plugins.scala.lang.completion.InsertionContextExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.packagesearch.codeInspection.DependencyVersionInspection.ArtifactIdSuffix
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scala.reposearch.common.ScalaDependencyCompletionProvider
import org.jetbrains.plugins.scala.reposearch.common.ScalaDependencyCompletionProvider.LookupText
import org.jetbrains.sbt.language.completion.SbtDependencyCompletionProviderBase.DependencyCompletionParameters
import org.jetbrains.sbt.language.completion.{SbtDependencyCompletionContributorBase, SbtDependencyCompletionProviderBase}

import javax.swing.Icon

final class SbtDependencyCoordinatesCompletionContributor extends SbtDependencyCompletionContributorBase {
  override protected def provider: SbtDependencyCompletionProviderBase = new SbtDependencyCoordinatesCompletionProvider
}

final class SbtDependencyCoordinatesCompletionProvider
  extends SbtDependencyCompletionProviderBase
    with ScalaDependencyCompletionProvider[DependencyCompletionParameters] {
  override protected def suggestGroupIdCompletions(params: DependencyCompletionParameters, withVersion: Boolean): Unit =
    extractText(params.place, trimDummy = true)(using params.completionParams).foreach { groupIdQuery =>
      suggestCompletionOnGroupId(params.copy(withGroupId = true, withEmptyVersion = withVersion), groupIdQuery)
    }

  override protected def suggestArtifactIdCompletions(params: DependencyCompletionParameters,
                                                      groupId: String, withVersion: Boolean): Unit =
    extractText(params.place, trimDummy = true)(using params.completionParams).foreach { artifactIdQuery =>
      suggestCompletionOnArtifactId(params.copy(withEmptyVersion = withVersion), groupId, artifactIdQuery)
    }

  // Will be handled by version completion provider
  override protected def suggestVersionCompletions(params: DependencyCompletionParameters, infix: ScInfixExpr): Unit = {}

  override protected def lookupStringOnGroupId(params: DependencyCompletionParameters, result: DependencyCompletionResult): Option[LookupText] =
    toLookupText(params, result.getGroupId, result.getArtifactId)

  override protected def lookupStringOnArtifactId(params: DependencyCompletionParameters, groupId: String, result: DependencyPartCompletionResult): Option[LookupText] =
    toLookupText(params, groupId, result.getResult)

  override protected def createLookupItem(params: DependencyCompletionParameters, lookupText: LookupText, icon: Icon): LookupElement =
    LookupElementBuilder.create(lookupText.lookupString)
      .withRenderer { (_, presentation) =>
        presentation.setItemText(lookupText.presentableText)
        presentation.setItemTextBold(true)
        presentation.setIcon(icon)
      }
      .withInsertHandler { (context, _) =>
        context.getDocument.replaceString(params.marker.getStartOffset, params.marker.getEndOffset, lookupText.presentableText)
        // move the caret before the closing quote in the artifactId/version string
        context.getEditor.getCaretModel.moveToOffset(params.marker.getStartOffset + lookupText.presentableText.length - 1)
        if (params.withEmptyVersion) {
          context.scheduleAutoPopup()
        }
      }

  override protected def finishDependencySuggestions(params: DependencyCompletionParameters): Unit =
    stopIfInsideString(params.resultSet, params.place)

  private def toLookupText(params: DependencyCompletionParameters, groupId: String, artifactId: String): Option[LookupText] =
    Option.when(DependencyUtil.isArtifactCompatible(params, artifactId)) {
      // sbt has no operator for full-version cross-publishing, so only major-version artifacts get the `%%` form
      val (delimiterLen, patchedArtifactId) = DependencyUtil.splitScalaArtifactIdSuffix(artifactId) match {
        case (baseArtifactId, ArtifactIdSuffix.ScalaVersion) => (2, baseArtifactId)
        case _ => (1, artifactId)
      }
      val lookupString = s"$groupId${":" * delimiterLen}$patchedArtifactId"
      val presentableText = {
        val groupIdPrefix = if (params.withGroupId) s"\"$groupId\" ${"%" * delimiterLen} " else ""
        val presentableArtifactText = s"\"$patchedArtifactId\""
        val versionSuffix = if (params.withEmptyVersion) " % \"\"" else ""
        groupIdPrefix + presentableArtifactText + versionSuffix
      }

      LookupText(lookupString, presentableText)
    }
}
