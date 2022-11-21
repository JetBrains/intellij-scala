package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import com.intellij.ide.IdeDeprecatedMessagesBundle
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

import scala.jdk.CollectionConverters._

final class ScalaMethodParametersMacro extends ScalaMacro {

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

  override def getDefaultValue: String = ScalaMacro.DefaultValue

  override def getPresentableName: String = IdeDeprecatedMessagesBundle.message("macro.method.parameters")
}
