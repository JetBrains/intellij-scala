package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * @author Roman.Shein
 * @since 19.09.2015.
 */
class ScalaMethodNameMacro extends ScalaMacro("macro.methodname") {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[ScFunction])).
            map(scFun => new TextResult(scFun.getName)).orNull

  override def getDefaultValue: String = ScalaMacro.DefaultValue
}
