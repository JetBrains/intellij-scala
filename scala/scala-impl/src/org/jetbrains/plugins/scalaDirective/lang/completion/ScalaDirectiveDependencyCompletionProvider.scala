package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet, CompletionSorter}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyDescriptor
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveDependencyCompletionProvider.{clean, toArtifactStringWithoutVersion}
import org.jetbrains.plugins.scalaDirective.lang.completion.lookups.{ScalaDirectiveDependencyLookupItem, ScalaDirectiveDependencyVersionLookupItem}
import org.jetbrains.plugins.scalaDirective.lang.completion.weigher.ScalaDirectiveDependencyVersionWeigher
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

import scala.jdk.CollectionConverters.IterableHasAsJava

final class ScalaDirectiveDependencyCompletionProvider extends CompletionProvider[CompletionParameters] {
  override def addCompletions(params: CompletionParameters, processingContext: ProcessingContext, resultSet: CompletionResultSet): Unit = {
    resultSet.restartCompletionWhenNothingMatches()

    val place = positionFromParameters(params)
    val (placeText, valueKind) = ScalaDirectiveValueKind.extract(place.getText)

    val tokens = placeText.split(':').filterNot(_.isBlank)
    val currentToken = tokens.indexWhere(_.contains(DUMMY_IDENTIFIER_TRIMMED))

    def findDependencies(groupId: String, artifactId: String, exactMatchGroupId: Boolean): Seq[LookupElement] = {
      val useCache = !params.isExtendedCompletion || ApplicationManager.getApplication.isUnitTestMode
      val packagesFuture = PackageSearchApiClient.searchByQuery(groupId, artifactId, useCache)
      val packages = ProgressIndicatorUtils.awaitWithCheckCanceled(packagesFuture)
        .pipeIf(exactMatchGroupId)(_.filter(_.groupId == groupId))

      packages.map(toArtifactStringWithoutVersion).distinct.map { lookupString =>
        ScalaDirectiveDependencyLookupItem(lookupString, valueKind)
      }
    }

    currentToken match {
      case 0 => // group id
        val groupIdWithDummy = tokens(currentToken)
        val groupId = clean(groupIdWithDummy)
        if (groupId.nonEmpty) {
          val lookupElements = findDependencies(groupId, artifactId = "", exactMatchGroupId = false).asJava
          resultSet.addAllElements(lookupElements)
        }
      case 1 => // artifact
        val groupId = tokens.head
        val artifactIdWithDummy = tokens(currentToken)
        val artifactId = clean(artifactIdWithDummy)

        val lookupElements = findDependencies(groupId, artifactId, exactMatchGroupId = true).asJava
        resultSet.addAllElements(lookupElements)
      case 2 => // version
        placeText match {
          case ScalaDirectiveDependencyDescriptor(descriptor) =>
            val onlyStableVersions = !params.isExtendedCompletion
            val versions = DependencyUtil.getDependencyVersions(descriptor, place, onlyStable = onlyStableVersions)
            val lookupElements =
              versions.map(version => ScalaDirectiveDependencyVersionLookupItem(version, descriptor, valueKind))

            val sorter = CompletionSorter.emptySorter()
              .weigh(new RealPrefixMatchingWeigher)
              .weigh(ScalaDirectiveDependencyVersionWeigher)

            resultSet
              .withRelevanceSorter(sorter)
              .addAllElements(lookupElements.asJava)
          case _ =>
        }
      case _ => // doesn't look like a dependency, do nothing
    }
  }
}

object ScalaDirectiveDependencyCompletionProvider {
  private[this] val CrossPublishedArtifact = "^(.+)_\\d+.*$".r

  private def toArtifactStringWithoutVersion(pkg: ApiPackage): String = {
    val artifactId = pkg.artifactId match {
      case CrossPublishedArtifact(artifactId) => s":$artifactId" // add extra ':' and remove version suffix
      case artifactId => artifactId
    }

    s"${pkg.groupId}:$artifactId:"
  }

  private def clean(text: String): String = {
    val idx = text.indexOf(DUMMY_IDENTIFIER_TRIMMED)
    text.pipeIf(idx >= 0)(_.substring(0, idx))
  }
}
