package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection


import collection.mutable.ArrayBuffer
import lang.psi.api.ScalaFile
import com.intellij.codeInspection._
import com.intellij.psi.PsiFile
import java.lang.String
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import com.intellij.lang.injection.InjectedLanguageManager
import extensions.toPsiNamedElementExt
import console.ScalaLanguageConsoleView
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */

class ScalaFileNameInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getID: String = "ScalaFileName"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile] ||
            InjectedLanguageManager.getInstance(file.getProject).isInjectedFragment(file) || 
      !IntentionAvailabilityChecker.checkInspection(this, file)) return Array.empty
    if (file.getName == ScalaLanguageConsoleView.SCALA_CONSOLE) return Array.empty

    val virtualFile = file.getVirtualFile

    if (virtualFile == null) return Array.empty

    val name = virtualFile.getNameWithoutExtension
    val scalaFile = file.asInstanceOf[ScalaFile]
    if (scalaFile.isScriptFile() || scalaFile.isWorksheetFile) return Array.empty
    val definitions = scalaFile.typeDefinitions

    if (definitions.length > 1) return Array.empty

    var hasProblems = true
    for (clazz <- definitions) {
      clazz match {
        case o: ScObject if file.name == "package.scala" && o.isPackageObject => hasProblems = false
        case _ if ScalaPsiUtil.memberNamesEquals(clazz.name, name) => hasProblems = false
        case _ =>
      }
    }

    val res = new ArrayBuffer[ProblemDescriptor]
    if (hasProblems) {
      for (clazz <- definitions;
           scalaClass: ScTypeDefinition = clazz) {
        res += manager.createProblemDescriptor(scalaClass.nameId, "Class doesn't correspond to file name",
          Array[LocalQuickFix](new ScalaRenameClassQuickFix(scalaClass, name),
            new ScalaRenameFileQuickFix(scalaFile, clazz.name + ".scala")), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
    res.toArray
  }
}