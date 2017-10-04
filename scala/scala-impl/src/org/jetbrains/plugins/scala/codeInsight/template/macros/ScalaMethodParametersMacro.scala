package org.jetbrains.plugins.scala.codeInsight.template.macros

import java.util

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

import scala.collection.JavaConverters._

/**
  * @author Roman.Shein
  * @since 22.09.2015.
  */
class ScalaMethodParametersMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val maybeFunction = Option(context.getPsiElementAtStartOffset)
      .flatMap(offset => Option(getParentOfType(offset, classOf[ScFunction])))

    val textResults = maybeFunction.toSeq
      .flatMap(_.parameters)
      .map(_.getName)
      .map(new TextResult(_))

    textResults match {
      case Seq() => null
      case seq =>
        // ListResult won't accept seq.asJava because invariance
        new ListResult(seq.asInstanceOf[Seq[Result]].asJava)
    }
  }

  override def getName: String = MacroUtil.scalaIdPrefix + "methodParameters"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.method.parameters")

  override def getDefaultValue = "a"

  override def isAcceptableInContext(context: TemplateContextType): Boolean =
    context.isInstanceOf[ScalaCodeContextType]
}
