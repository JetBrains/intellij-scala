package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection


import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.07.2009
 */

class ScalaRenameFileQuickFix(file: ScalaFile, name: String) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    /*(new RenameRefactoringImpl(project, file, name, false, true)).run*/
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        val processor: RenameProcessor = new RenameProcessor(project, file, name, false, false)
        processor.run
      }
    })
  }

  def getName: String = "Rename File " + file.name + " to " + name

  def getFamilyName: String = "Rename File"
}