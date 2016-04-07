package org.jetbrains.plugins.scala.codeInsight

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

/**
  * @author Nikolay.Tropin
  */
class ScalaMoveLeftRightHandler extends MoveElementLeftRightHandler {
  override def getMovableSubElements(element: PsiElement): Array[PsiElement] = {
    element match {
      case argList: ScArgumentExprList =>
        argList.exprs.toArray
      case paramClause: ScParameterClause =>
        paramClause.parameters.toArray
      case _ =>
        Array.empty
    }
  }
}
