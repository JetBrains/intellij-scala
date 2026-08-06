package org.jetbrains.plugins.scala.codeInspection.scalastyle

import com.intellij.codeInspection.{InspectionManager, ProblemDescriptor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile

trait ScalastyleService {

  def checkFile(file: PsiFile, manager: InspectionManager): Array[ProblemDescriptor]

  def settingsFor(file: PsiFile): ScalastyleSettings
}

object ScalastyleService {
  def instance: ScalastyleService =
    ApplicationManager.getApplication.getService(classOf[ScalastyleService])
}
