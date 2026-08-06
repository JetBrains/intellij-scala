package org.jetbrains.plugins.scala.gotoclass

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityStatistician
import org.jetbrains.plugins.scala.lang.completion.statistician.ScalaStatisticManager

class ScalaProximityStatistician extends ProximityStatistician {
  override def serialize(element: PsiElement, location: ProximityLocation): StatisticsInfo = {
    if (DumbService.isDumb(element.getProject)) return null

    ScalaStatisticManager.memberKey(element)
      .map(el => new StatisticsInfo("scalaMember#", el))
      .orNull
  }
}
