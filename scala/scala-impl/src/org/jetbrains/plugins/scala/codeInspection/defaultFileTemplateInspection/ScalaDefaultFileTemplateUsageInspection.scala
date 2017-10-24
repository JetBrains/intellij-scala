package org.jetbrains.plugins.scala.codeInspection.defaultFileTemplateInspection

import com.intellij.codeInspection._
import com.intellij.psi.PsiFile
/**
 * @author Alexander Podkhalyuzin
 */

class ScalaDefaultFileTemplateUsageInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaDefFileTempl"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val descriptor = FileHeaderChecker.checkFileHeader(file, manager, isOnTheFly)
    if (descriptor != null) Array(descriptor) else Array.empty
  }
}