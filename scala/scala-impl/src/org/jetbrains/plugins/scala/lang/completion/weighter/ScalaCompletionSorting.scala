package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionUtil, ScalaCompletionUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
  * Created by Kate Ustyuzhanina on 12/2/16.
  */
object ScalaCompletionSorting {

  implicit class ResultOps(val result: CompletionResultSet) extends AnyVal {

    def withScalaSorting(parameters: CompletionParameters): CompletionResultSet = {
      val isSmart = parameters.getCompletionType == CompletionType.SMART
      val position = ScalaCompletionUtil.positionFromParameters(parameters)
      val isAfterNew = ScalaAfterNewCompletionUtil.afterNewPattern.accepts(position)

      val defaultSorter = CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher)

      val sorter =
        if (isSmart)
          defaultSorter
        else if (isAfterNew)
          defaultSorter
            .weighBefore("liftShorter", new ScalaByTypeWeigher(position))
            .weighAfter("scalaTypeCompletionWeigher", new ScalaByExpectedTypeWeigher(position, isAfterNew))
        else
          defaultSorter
            .weighBefore("liftShorter", new ScalaByTypeWeigher(position))
            .weighAfter("scalaKindWeigher", new ScalaByExpectedTypeWeigher(position, isAfterNew))

      result.withRelevanceSorter(sorter)
    }

    def withBacktickMatcher(): CompletionResultSet = {
      result.withPrefixMatcher(new BacktickPrefixMatcher(result.getPrefixMatcher))
    }
  }

  private class BacktickPrefixMatcher(other: PrefixMatcher) extends PrefixMatcher(other.getPrefix) {
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

}