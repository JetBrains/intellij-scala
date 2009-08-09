package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection


import collection.mutable.ArrayBuffer
import com.intellij.codeHighlighting.HighlightDisplayLevel
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import lang.psi.api.ScalaFile
import com.intellij.codeInspection._
import com.intellij.codeInspection.ex.ProblemDescriptorImpl
import com.intellij.psi.PsiFile
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */

class FileNameInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "File Name Inspection"

  def getShortName: String = "File Name"

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for files without type definition with corresponding name"

  override def getID: String = "File Name"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile]) return Array[ProblemDescriptor]()
    val name = file.getName().substring(0, file.getName.length - 6)
    val scalaFile = file.asInstanceOf[ScalaFile]
    var hasProblems = true
    for (clazz <- scalaFile.getClasses) {
      if (clazz.getName == name) hasProblems = false
    }
    val res = new ArrayBuffer[ProblemDescriptor]
    if (hasProblems) {
      for (clazz <- scalaFile.getClasses; scalaClass: ScTypeDefinition = clazz.asInstanceOf[ScTypeDefinition]) {
        res +=  manager.createProblemDescriptor(scalaClass.nameId, "Class doesn't correspond to file name", 
          Array[LocalQuickFix](new ScalaRenameClassQuickFix(scalaClass, name),
            new ScalaRenameFileQuickFix(scalaFile, clazz.getName + ".scala")), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
    return res.toArray
  }
}