package org.jetbrains.plugins.scala.gotoclass

import com.intellij.psi.PsiElement
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityStatistician
import org.jetbrains.plugins.scala.lang.completion.statistician.ScalaStatisticManager
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
  * @author Alefas
  * @since  21/10/15
  */
class ScalaProximityStatistician extends ProximityStatistician {
  override def serialize(element: PsiElement, location: ProximityLocation): StatisticsInfo = {
    ScalaStatisticManager.memberKey(element).map(el =>
      new StatisticsInfo("scalaMember#", el)).getOrElse(return null)
  }
}