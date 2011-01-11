package org.jetbrains.plugins.scala.codeInspection.defaultFileTemplateInspection

import java.lang.String
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import com.intellij.psi.PsiFile
import com.intellij.codeInspection._
/**
 * @author Alexander Podkhalyuzin
 */

class ScalaDefaultFileTemplateUsageInspection extends LocalInspectionTool {
  def getShortName: String = "ScalaDefaultFileTemplate"

  def getDisplayName: String = "Default file template inspection"

  def getGroupDisplayName: String = InspectionsUtil.SCALA

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection to show default file template usage"

  override def getID: String = "ScalaDefFileTempl"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    val descriptor = FileHeaderChecker.checkFileHeader(file, manager, isOnTheFly)
    if (descriptor != null) Array(descriptor) else Array.empty
  }
}