package org.jetbrains.plugins.scala
package annotator.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.ScTypeExt
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class WrapInOptionQuickFix(expr: ScExpression, expectedType: TypeResult, exprType: TypeResult) extends IntentionAction {

  override def getText: String = ScalaBundle.message("wrap.in.option.hint")

  override def getFamilyName: String = ScalaBundle.message("wrap.in.option.name")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    WrapInOptionQuickFix.isAvailable(expectedType, exprType)
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    if (expr.isValid) {
      val newText = "Option(" + expr.getText + ")"
      expr.replaceExpression(createExpressionFromText(newText)(expr.getManager), removeParenthesis = true)
    }
  }

  override def startInWriteAction(): Boolean = true

}

object WrapInOptionQuickFix {

  def isAvailable(expectedType: TypeResult, exprType: TypeResult): Boolean = {
    var result = false
    for {
      scType <- exprType
      expectedType <- expectedType
    } {
      expectedType match {
        case ParameterizedType(des, Seq(typeArg)) =>
          des.extractClass match {
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