package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class ScalaCurrentPackageMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    PsiDocumentManager.getInstance(context.getProject).getPsiFile(context.getEditor.getDocument) match {
      case scFile: ScalaFile => new TextResult(scFile.getPackageName)
      case _ => new TextResult("")
    }
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def getPresentableName: String = CodeInsightBundle.message("macro.current.package")
}
