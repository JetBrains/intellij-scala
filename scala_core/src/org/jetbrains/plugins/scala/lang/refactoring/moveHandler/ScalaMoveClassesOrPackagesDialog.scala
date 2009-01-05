package org.jetbrains.plugins.scala.lang.refactoring.moveHandler

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesDialog
import com.intellij.usageView.{UsageInfo, UsageViewDescriptor}
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.12.2008
 */

class ScalaMoveClassesOrPackagesDialog(project: Project, searchTextOccurences: Boolean, psiElements: Array[PsiElement],
                                      initialTargetElement: PsiElement, moveCallback: MoveCallback)
        extends MoveClassesOrPackagesDialog(project, searchTextOccurences, psiElements, initialTargetElement, moveCallback) {
  override def doAction: Unit = {
    if (isMoveToPackage) moveToPackage
    else {}
  }

  private def moveToPackage {
    
  }
}