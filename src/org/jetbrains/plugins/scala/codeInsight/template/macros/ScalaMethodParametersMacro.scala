package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaMethodParametersMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    import scala.collection.JavaConversions._
    Option(PsiTreeUtil.getParentOfType(context.getPsiElementAtStartOffset, classOf[ScFunction])).
            flatMap(fun => Option(fun.getParameterList)).map(_.getParameters.map(param => new TextResult(param.getName))).
            map(resArr => new ListResult(resArr.toList)).orNull
  }

  override def getName: String = MacroUtil.scalaIdPrefix + "methodParameters"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.method.parameters")

  override def getDefaultValue = "a"

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]
}
