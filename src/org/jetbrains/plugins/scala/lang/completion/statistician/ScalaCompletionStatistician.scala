package org.jetbrains.plugins.scala.lang.completion.statistician

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionStatistician}
import com.intellij.codeInsight.lookup.{LookupElement, LookupItem}
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.{PsiClass, PsiMember, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.completion.ScalaTextLookupItem
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Alefas
 * @since 06.07.12
 */

class ScalaCompletionStatistician extends CompletionStatistician {
  def serialize(element: LookupElement, location: CompletionLocation): StatisticsInfo = {
    ScalaLookupItem.original(element) match {
      case s: ScalaLookupItem if s.isLocalVariable || s.isNamedParameter || s.isDeprecated => StatisticsInfo.EMPTY
      case s: ScalaLookupItem =>
        s.element match {
          case withImplicit: ScModifierListOwner if withImplicit.hasModifierPropertyScala("implicit") =>
            StatisticsInfo.EMPTY
          case _ => helper(s.element, location)
        }
      // return empty statistic when using  scala completion but ScalaLookupItem didn't use.
      // otherwise will be computed java statistic that may lead to ClassCastError
      case _: ScalaTextLookupItem => StatisticsInfo.EMPTY
      case _ if location.getCompletionParameters.getOriginalFile.isInstanceOf[ScalaFile] => StatisticsInfo.EMPTY
      case _ => null //don't impact on java Lookups, no statistics for scala keyword elements
    }
  }

  def helper(element: PsiNamedElement, location: CompletionLocation): StatisticsInfo = {
    element match {
      case member: PsiMember =>
        val key = ScalaStatisticManager.memberKey(member).getOrElse(return StatisticsInfo.EMPTY)
        member match {
          case (_: ScTypeAlias) | (_: ScTypeDefinition) | (_: PsiClass) =>
            new StatisticsInfo("scalaMember", key)
          case _ =>
            val containingClass = member.getContainingClass
            if (containingClass != null) {
              val context = ScalaStatisticManager.memberKey(containingClass).getOrElse("")
              new StatisticsInfo("scalaMember#" + context, key)
            } else {
              StatisticsInfo.EMPTY
            }
        }
      case _ => StatisticsInfo.EMPTY
    }
  }
}
