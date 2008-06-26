package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.PsiElement

/**
* User: Alexander Podkhalyuzin
* Date: 24.06.2008
*/

class ScalaVariableValidator(introduceVariableBase: ScalaIntroduceVariableBase,
                            myProject: Project,
                            selectedExpr: ScExpression,
                            occurrences: Array[ScExpression],
                            enclosingContainer: PsiElement) extends ScalaValidator {
  def getProject(): Project = {
    myProject;
  }

  def isOK(dialog: ScalaIntroduceVariableDialogInterface): Boolean = true

  def validateName(name: String, increaseNumber: Boolean): String = name
}