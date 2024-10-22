package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.openapi.project.DumbAware
import com.intellij.util.ProcessingContext
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.plugins.scala.lang.completion.positionFromParameters
import org.jetbrains.plugins.scala.packagesearch.lang.completion.DependencyVersionWeigher
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveScalaVersionCompletionContributor.fetchVersions
import org.jetbrains.plugins.scalaDirective.lang.completion.lookups.ScalaDirectiveDependencyVersionLookupItem
import org.jetbrains.plugins.scalaDirective.util.ScalaDirectiveValueKind

import scala.jdk.CollectionConverters.IterableHasAsJava

final class ScalaDirectiveScalaVersionCompletionContributor extends CompletionContributor with DumbAware {
  extend(CompletionType.BASIC, ScalaDirectiveScalaVersionPattern, new CompletionProvider[CompletionParameters] {
    override def addCompletions(params: CompletionParameters, processingContext: ProcessingContext, resultSet: CompletionResultSet): Unit = {
      val place = positionFromParameters(params)
      val (_, valueKind) = ScalaDirectiveValueKind.extract(place.getText)

      val onlyStableVersions = !params.isExtendedCompletion
      val versions = fetchVersions(onlyStableVersions)
      val lookupElements = versions.map(version => ScalaDirectiveDependencyVersionLookupItem(version, valueKind))

      val sorter = CompletionSorter.emptySorter()
        .weigh(new RealPrefixMatchingWeigher)
        .weigh(DependencyVersionWeigher)
      val newResultSet = resultSet.withRelevanceSorter(sorter)

      newResultSet.addAllElements(lookupElements.asJava)
      newResultSet.stopHere()
    }
  })
}

object ScalaDirectiveScalaVersionCompletionContributor {
  private def fetchVersions(onlyStable: Boolean): Seq[ComparableVersion] = {
    val scala2 = DependencyUtil.getScala2CompilerVersions(onlyStable)
    val scala3 = DependencyUtil.getScala3CompilerVersions(onlyStable)

    scala2 ++ scala3
  }
}
