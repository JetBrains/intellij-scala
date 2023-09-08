package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.completion.{CompletionParameters, CompletionProvider, CompletionResultSet, CompletionSorter}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.LatestScalaVersions
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.packagesearch.api.PackageSearchApiClient
import org.jetbrains.plugins.scala.packagesearch.model.ApiPackage
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyDescriptor
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveDependencyCompletionProvider._
import org.jetbrains.plugins.scalaDirective.lang.completion.lookups.{ScalaDirectiveDependencyLookupItem, ScalaDirectiveDependencyVersionLookupItem}
import org.jetbrains.plugins.scalaDirective.lang.completion.weigher.ScalaDirectiveDependencyVersionWeigher
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

import scala.jdk.CollectionConverters.IterableHasAsJava

final class ScalaDirectiveDependencyCompletionProvider extends CompletionProvider[CompletionParameters] {
  override def addCompletions(params: CompletionParameters, processingContext: ProcessingContext, resultSet: CompletionResultSet): Unit = {
    resultSet.restartCompletionOnAnyPrefixChange()

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
          val lookupElements = findDependencies(groupId, artifactId = "", exactMatchGroupId = false)
          resultSet.addAllAndStop(lookupElements)
        }
      case 1 => // artifact
        val groupId = tokens.head
        val artifactIdWithDummy = tokens(currentToken)
        val artifactId = clean(artifactIdWithDummy)

        val lookupElements = findDependencies(groupId, artifactId, exactMatchGroupId = true)
        resultSet.addAllAndStop(lookupElements)
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
              .addAllAndStop(lookupElements)
          case _ =>
        }
      case _ => // doesn't look like a dependency, do nothing
    }
  }
}

object ScalaDirectiveDependencyCompletionProvider {
  private[this] val CrossPublishedArtifact = "^(.+)_(\\d+.*)$".r
  private[this] val Scala2MajorVersions = LatestScalaVersions.all
    .collect { case version if version.isScala2 => version.major }
  private[this] val ScalaMajorVersions = Scala2MajorVersions :+ "3"

  private def toArtifactStringWithoutVersion(pkg: ApiPackage): String = {
    val artifactId = pkg.artifactId match {
      case CrossPublishedArtifact(artifactId, version) =>

        /**
         * group:artifact   -> group:artifact
         * group::artifact  -> group:artifact_3, group:artifact_2.12, etc.
         * group:::artifact -> group:artifact_3.3.0, group:artifact_2.12.15, group:artifact_2.13.0-RC3, etc.
         */
        val crossVersionPrefix =
          if (ScalaMajorVersions.contains(version)) ":"
          else "::"

        s"$crossVersionPrefix$artifactId"
      case artifactId => artifactId
    }

    s"${pkg.groupId}:$artifactId:"
  }

  private def clean(text: String): String = {
    val idx = text.indexOf(DUMMY_IDENTIFIER_TRIMMED)
    text.pipeIf(idx >= 0)(_.substring(0, idx))
  }

  private[completion] final implicit class CompletionResultSetExt(private val resultSet: CompletionResultSet) extends AnyVal {
    def addAllAndStop(elements: Seq[LookupElement]): Unit = {
      resultSet.addAllElements(elements.asJava)
      resultSet.stopHere()
    }
  }
}
