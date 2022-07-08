package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final class ScalaMethodNameMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[ScFunction])).
            map(scFun => new TextResult(scFun.getName)).orNull

  override def getDefaultValue: String = ScalaMacro.DefaultValue

  override def getPresentableName: String = CodeInsightBundle.message("macro.methodname")
}
