package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaCurrentPackageMacro extends ScalaMacro("macro.current.package") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    PsiDocumentManager.getInstance(context.getProject).getPsiFile(context.getEditor.getDocument) match {
      case scFile: ScalaFile => new TextResult(scFile.getPackageName)
      case _ => new TextResult("")
    }
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)
}
