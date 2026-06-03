package org.jetbrains.plugins.scala.codeInspection.scalastyle

import com.intellij.codeInspection.{InspectionManager, LocalInspectionTool, ProblemDescriptor}
import com.intellij.psi.PsiFile

class ScalastyleCodeInspection extends LocalInspectionTool {
  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    ScalastyleService.instance.checkFile(file, manager)
  }
}
