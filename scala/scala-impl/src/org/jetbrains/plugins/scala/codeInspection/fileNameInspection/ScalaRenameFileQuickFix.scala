package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection


import com.intellij.openapi.application.ApplicationManager
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
  def doApplyFix(project: Project): Unit = {
    /*(new RenameRefactoringImpl(project, file, name, false, true)).run*/
    val file = getElement
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        val processor: RenameProcessor = new RenameProcessor(project, file, name, false, false)
        processor.run()
      }
    })

  }

  override def getFamilyName: String = "Rename File"
}