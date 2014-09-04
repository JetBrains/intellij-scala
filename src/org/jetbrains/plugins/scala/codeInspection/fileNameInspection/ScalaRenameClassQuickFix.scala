package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection


import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringFactory
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.07.2009
 */

class ScalaRenameClassQuickFix(clazz: ScTypeDefinition, name: String) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        RefactoringFactory.getInstance(project).createRename(clazz, name).run
      }
    })
  }

  def getName: String = "Rename Type Definition " + clazz.name + " to " + name

  def getFamilyName: String = "Rename Type Definition"
}