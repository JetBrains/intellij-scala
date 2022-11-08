package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

final class ScalaMethodNameMacro extends ScalaMacro {

  override def getNameShort: String = "methodName"

  override def getDefaultValue: String = ScalaMacro.DefaultValue

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val parentFunction = Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[ScFunction]))
    parentFunction.map(fun => new TextResult(fun.getName)).orNull
  }
}
