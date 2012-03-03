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

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.07.2009
 */

class ScalaFileNameInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String = "Inspection for files without type definition with corresponding name"

  override def getID: String = "ScalaFileName"

  override def checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array[ProblemDescriptor] = {
    if (!file.isInstanceOf[ScalaFile] ||
            InjectedLanguageManager.getInstance(file.getProject).isInjectedFragment(file)) return Array.empty
    if (file.getName == ScalaLanguageConsoleView.SCALA_CONSOLE) return Array.empty

    val name = file.name.substring(0, file.name.length - 6)
    val scalaFile = file.asInstanceOf[ScalaFile]
    var hasProblems = true
    for (clazz <- scalaFile.typeDefinitions) {
      clazz match {
        case o: ScObject if file.name == "package.scala" && o.isPackageObject => hasProblems = false
        case _ if clazz.name == name => hasProblems = false
        case _ =>
      }
    }

    val res = new ArrayBuffer[ProblemDescriptor]
    if (hasProblems) {
      for (clazz <- scalaFile.typeDefinitions;
           scalaClass: ScTypeDefinition = clazz.asInstanceOf[ScTypeDefinition]) {
        res += manager.createProblemDescriptor(scalaClass.nameId, "Class doesn't correspond to file name",
          Array[LocalQuickFix](new ScalaRenameClassQuickFix(scalaClass, name),
            new ScalaRenameFileQuickFix(scalaFile, clazz.name + ".scala")), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
    res.toArray
  }
}