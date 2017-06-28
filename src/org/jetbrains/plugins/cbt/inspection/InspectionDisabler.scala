package org.jetbrains.plugins.cbt.inspection

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cbt.CBT
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

class InspectionDisabler extends IntentionAvailabilityChecker {
  val disabledInspections = Seq("ScalaFileName", "ScalaPackageName")

  override def canCheck(psiElement: PsiElement): Boolean = {
    val project = psiElement.getProject
    CBT.isCbtProject(project)
  }

  override def isInspectionAvailable(inspection: InspectionProfileEntry, psiElement: PsiElement): Boolean =
    !disabledInspections.contains(inspection.getShortName)
}
