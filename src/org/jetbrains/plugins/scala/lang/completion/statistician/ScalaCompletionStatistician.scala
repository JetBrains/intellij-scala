package org.jetbrains.plugins.scala.lang.completion.statistician

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionStatistician}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.statistics.StatisticsInfo

/**
 * @author Alefas
 * @since 06.07.12
 */

class ScalaCompletionStatistician extends CompletionStatistician {
  def serialize(element: LookupElement, location: CompletionLocation): StatisticsInfo = {
    StatisticsInfo.EMPTY //todo:
  }
}
