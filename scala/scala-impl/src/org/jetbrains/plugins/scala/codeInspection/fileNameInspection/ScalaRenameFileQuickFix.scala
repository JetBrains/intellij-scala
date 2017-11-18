package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection

import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
  * User: Alexander Podkhalyuzin
  * Date: 03.07.2009
  */
class ScalaRenameFileQuickFix(myFile: ScalaFile, name: String) extends
  AbstractFixOnPsiElement("Rename File " + myFile.name + " to " + name, myFile) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit =
    invokeLater {
      // new RenameRefactoringImpl(project, file, name, false, true)).run
      new RenameProcessor(project, file, name, false, false).run()
    }

  override def getFamilyName: String = "Rename File"
}