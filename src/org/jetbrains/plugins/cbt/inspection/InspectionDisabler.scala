package org.jetbrains.plugins.cbt.inspection

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cbt.CBT
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker
import  org.jetbrains.plugins.cbt._
class InspectionDisabler extends IntentionAvailabilityChecker {
  val disabledInspections = Seq("ScalaFileName", "ScalaPackageName")

  override def canCheck(psiElement: PsiElement): Boolean = {
    val project = psiElement.getProject
    project.isCbtProject
  }

  override def isInspectionAvailable(inspection: InspectionProfileEntry, psiElement: PsiElement): Boolean =
    !disabledInspections.contains(inspection.getShortName)
}
