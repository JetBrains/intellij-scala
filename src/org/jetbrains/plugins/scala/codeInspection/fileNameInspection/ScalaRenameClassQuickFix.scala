package org.jetbrains.plugins.scala
package codeInspection
package fileNameInspection


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringFactory
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.07.2009
 */

class ScalaRenameClassQuickFix(clazz: ScTypeDefinition, name: String)
        extends AbstractFixOnPsiElement("Rename Type Definition " + clazz.name + " to " + name, clazz) {
  def doApplyFix(project: Project): Unit = {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        val td = getElement
        RefactoringFactory.getInstance(project).createRename(td, name).run()
      }
    })
  }

  override def getFamilyName: String = "Rename Type Definition"
}