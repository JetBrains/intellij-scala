package org.jetbrains.plugins.scalaDirective.lang.completion

import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.completion.{CompletionResultSet, CompletionSorter}
import com.intellij.icons.AllIcons
import org.jetbrains.plugins.scala.packagesearch.lang.completion.DependencyVersionWeigher
import org.jetbrains.plugins.scala.packagesearch.util.DependencyUtil
import org.jetbrains.plugins.scalaDirective.dependencies.ScalaDirectiveDependencyDescriptor
import org.jetbrains.plugins.scalaDirective.lang.completion.ScalaDirectiveDependencyCompletionProviderBase.DependencyCompletionParameters
import org.jetbrains.plugins.scalaDirective.lang.completion.lookups.ScalaDirectiveDependencyVersionLookupItem

import scala.jdk.CollectionConverters.IterableHasAsJava

final class ScalaDirectiveDependencyVersionCompletionContributor extends ScalaDirectiveDependencyCompletionContributorBase {
  protected def provider: ScalaDirectiveDependencyCompletionProviderBase = new ScalaDirectiveDependencyVersionCompletionProvider
}

final class ScalaDirectiveDependencyVersionCompletionProvider extends ScalaDirectiveDependencyCompletionProviderBase {
  override protected def addCompletions(params: DependencyCompletionParameters, resultSet: CompletionResultSet): Unit =
    params.currentTokenIdx match {
      case 2 =>
        params.placeText match {
          case ScalaDirectiveDependencyDescriptor(descriptor) =>
            val onlyStableVersions = !params.completionParams.isExtendedCompletion
            val versions = DependencyUtil.getDependencyVersions(descriptor, params.place, onlyStable = onlyStableVersions)
            val icon = Some(AllIcons.Build.CompletionLocalCache)
            val lookupElements =
              versions.map(version => ScalaDirectiveDependencyVersionLookupItem(version, descriptor, params.valueKind, icon))

            val sorter = CompletionSorter.emptySorter()
              .weigh(new RealPrefixMatchingWeigher)
              .weigh(DependencyVersionWeigher)

            val result = resultSet.withRelevanceSorter(sorter)
            result.addAllElements(lookupElements.asJava)
            result.stopHere()
          case _ =>
        }
      case _ =>
        // either `0` or `1`, dependency coordinates (groupId, artifactId), will be handled by ScalaDirectiveDependencyCoordinatesCompletionProvider
        // or doesn't look like a dependency, do nothing
    }
}
