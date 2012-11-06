package org.jetbrains.plugins.scala
package lang.refactoring.move

import com.intellij.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiElement}
import lang.psi.api.ScalaFile
import lang.psi.light.PsiClassWrapper
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import com.intellij.refactoring.move.MoveCallback
import lang.psi.impl.ScalaFileImpl
import com.intellij.openapi.ui.Messages

/**
 * @author Alefas
 * @since 02.11.12
 */
class ScalaMoveClassesOrPackagesHandler extends JavaMoveClassesOrPackagesHandler {
  override def doMove(project: Project, elements: Array[PsiElement], targetContainer: PsiElement, callback: MoveCallback) {
    def refactoringIsNotSupported() {
      Messages.showErrorDialog(ScalaBundle.message("move.to.inner.is.not.supported"), ScalaBundle.message("move.to.inner.is.not.supported.title"))
    }
    targetContainer match {
      case td: ScTypeDefinition =>
        refactoringIsNotSupported()
        return
      case clazz: PsiClass =>
        if (elements.find(_.isInstanceOf[ScTypeDefinition]).isDefined) {
          refactoringIsNotSupported()
          return
        }
    }
    ScalaFileImpl.performMoveRefactoring {
      super.doMove(project, elements, targetContainer, callback)
    }
  }
}
