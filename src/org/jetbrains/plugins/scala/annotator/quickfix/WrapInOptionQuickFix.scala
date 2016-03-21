package org.jetbrains.plugins.scala
package annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScTypeExt}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Nikolay.Tropin
 * 6/27/13
 */
class WrapInOptionQuickFix(expr: ScExpression, expectedType: TypeResult[ScType], exprType: TypeResult[ScType]) extends IntentionAction {
  def getText: String = ScalaBundle.message("wrap.in.option.hint")

  def getFamilyName: String = ScalaBundle.message("wrap.in.option.name")

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    WrapInOptionQuickFix.isAvailable(expr, expectedType, exprType)(project.typeSystem)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (expr.isValid) {
      val newText = "Option(" + expr.getText + ")"
      val newExpr = ScalaPsiElementFactory.createExpressionFromText(newText, expr.getManager)
      expr.replaceExpression(newExpr, removeParenthesis = true)
    }
  }

  def startInWriteAction(): Boolean = true

}

object WrapInOptionQuickFix {
  def isAvailable(expr: ScExpression, expectedType: TypeResult[ScType], exprType: TypeResult[ScType])
                 (implicit typeSystem: TypeSystem): Boolean = {
    var result = false
    for {
      scType <- exprType
      expectedType <- expectedType
    } {
      expectedType match {
        case ScParameterizedType(des, Seq(typeArg)) =>
          des.extractClass() match {
            case Some(scClass: ScClass)
              if scClass.qualifiedName == "scala.Option" && scType.conforms(typeArg) => result = true
            case _ =>
          }
        case _ =>
      }
    }
    result
  }
}