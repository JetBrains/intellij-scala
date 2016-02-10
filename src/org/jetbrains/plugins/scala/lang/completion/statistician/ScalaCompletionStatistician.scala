package org.jetbrains.plugins.scala.lang.completion.statistician

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionStatistician}
import com.intellij.codeInsight.lookup.{LookupItem, LookupElement}
import com.intellij.psi.{PsiMember, PsiNamedElement}
import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem

/**
 * @author Alefas
 * @since 06.07.12
 */

class ScalaCompletionStatistician extends CompletionStatistician {
  def serialize(element: LookupElement, location: CompletionLocation): StatisticsInfo = {
    val currentElement = Option(element.as(LookupItem.CLASS_CONDITION_KEY)).getOrElse(return null)
    ScalaLookupItem.original(currentElement) match {
      case s: ScalaLookupItem if s.isLocalVariable => StatisticsInfo.EMPTY
      case s: ScalaLookupItem => helper(s.element, location)
      case _ => null //don't impact on java Lookups, no statistics for scala keyword elements
    }
  }

  def helper(element: PsiNamedElement, location: CompletionLocation): StatisticsInfo = {
    element match {
      case member: PsiMember =>
        val key = ScalaStatisticManager.memberKey(member).getOrElse(return StatisticsInfo.EMPTY)
        val containingClass = member.getContainingClass
        if (containingClass != null) {
          val context = ScalaStatisticManager.memberKey(containingClass)
            .getOrElse(return new StatisticsInfo("scalaMember#", key))
          return new StatisticsInfo(context, key)
        }
        new StatisticsInfo("scalaMember#", key)
      case _ => StatisticsInfo.EMPTY
    }
  }
}
