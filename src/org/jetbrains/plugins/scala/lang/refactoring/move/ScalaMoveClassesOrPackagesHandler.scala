package org.jetbrains.plugins.scala
package lang.refactoring.move

import com.intellij.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import lang.psi.api.ScalaFile
import lang.psi.light.PsiClassWrapper
import lang.psi.api.toplevel.typedef.ScObject
import com.intellij.refactoring.move.MoveCallback
import lang.psi.impl.ScalaFileImpl

/**
 * @author Alefas
 * @since 02.11.12
 */
class ScalaMoveClassesOrPackagesHandler extends JavaMoveClassesOrPackagesHandler {
  override def doMove(project: Project, elements: Array[PsiElement], targetContainer: PsiElement, callback: MoveCallback) {
    ScalaFileImpl.performMoveRefactoring {
      super.doMove(project, elements, targetContainer, callback)
    }
  }
}
