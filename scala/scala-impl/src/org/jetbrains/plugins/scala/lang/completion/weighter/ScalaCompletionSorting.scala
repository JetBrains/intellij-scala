package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.completion.ScalaSmartCompletionContributor.ReferenceWithElement
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionUtil, ScalaCompletionUtil, ScalaSmartCompletionContributor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Created by Kate Ustyuzhanina on 12/2/16.
  */
object ScalaCompletionSorting {
  def addScalaSorting(parameters: CompletionParameters, result: CompletionResultSet): CompletionResultSet = {
    val isSmart = parameters.getCompletionType == CompletionType.SMART
    val position = ScalaCompletionUtil.positionFromParameters(parameters)
    val isAfterNew = ScalaAfterNewCompletionUtil.afterNewPattern.accepts(position)

    var sorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher)

    val expectedTypes: Array[ScType] = ScalaSmartCompletionContributor.extractReference(position) match {
      case Some(ReferenceWithElement(reference, _)) => reference.expectedTypes()
      case _ if isAfterNew =>
        Option(PsiTreeUtil.getContextOfType(position, classOf[ScNewTemplateDefinition])).map(_.expectedTypes()).getOrElse(Array.empty);
      case _ => Array.empty
    }

    if (!isSmart && isAfterNew) {
      sorter = sorter.weighBefore("liftShorter", new ScalaByTypeWeigher(position))
      sorter = sorter.weighAfter("scalaTypeCompletionWeigher", new ScalaByExpectedTypeWeigher(expectedTypes, position))
    } else if (!isSmart) {
      sorter = sorter.weighBefore("scalaContainingClassWeigher", new ScalaByTypeWeigher(position))
      sorter = sorter.weighAfter("scalaKindWeigher", new ScalaByExpectedTypeWeigher(expectedTypes, position))
    }

    class BacktickedsPrefixMatcher(other: PrefixMatcher) extends PrefixMatcher(other.getPrefix) {
      private val matcherWithoutBackticks = other.cloneWithPrefix(cleanHelper(myPrefix))

      override def prefixMatches(name: String): Boolean =
        if (myPrefix == "`") other.prefixMatches(name)
        else matcherWithoutBackticks.prefixMatches(ScalaNamesUtil.clean(name))

      override def cloneWithPrefix(prefix: String): PrefixMatcher = matcherWithoutBackticks.cloneWithPrefix(prefix)

      override def isStartMatch(name: String): Boolean =
        if (myPrefix == "`") other.isStartMatch(name)
        else matcherWithoutBackticks.isStartMatch(ScalaNamesUtil.clean(name))

      private def cleanHelper(prefix: String): String = {
        if (prefix == null || prefix.isEmpty || prefix == "`") prefix
        else prefix match {
          case ScalaNamesUtil.isBacktickedName(s) => s
          case p if p.head == '`' => p.substring(1)
          case p if p.last == '`' => prefix.substring(0, prefix.length - 1)
          case _ => prefix
        }
      }
    }

    result
      .withRelevanceSorter(sorter)
      .withPrefixMatcher(new BacktickedsPrefixMatcher(result.getPrefixMatcher))
  }
}