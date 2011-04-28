package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection


import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import com.intellij.openapi.project.Project
import com.intellij.refactoring.{RefactoringFactory, RefactoringActionHandlerFactory}
import java.lang.String
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.application.ApplicationManager

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

  def getName: String = "Rename Type Definition " + clazz.getName + " to " + name

  def getFamilyName: String = "Rename Type Definition"
}