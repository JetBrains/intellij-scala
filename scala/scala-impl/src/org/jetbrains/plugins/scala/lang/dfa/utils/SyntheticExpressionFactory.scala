package org.jetbrains.plugins.scala.lang.dfa.utils

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode.ScalaCodeContext

object SyntheticExpressionFactory {

  def wrapInTupleExpression(tupleContents: Seq[ScExpression])(implicit context: Project): ScExpression = {
    val tupleContentsInText = tupleContents.map(_.getText).mkString(", ")
    code"($tupleContentsInText)".asInstanceOf[ScExpression]
  }

  def wrapInSplatListExpression(varargContents: Seq[ScExpression])(implicit context: Project): ScExpression = {
    val tupleContentsInText = varargContents.map(_.getText).mkString(" :: ")
    if (varargContents.isEmpty) code"Nil: _*".asInstanceOf[ScExpression]
    else code"$tupleContentsInText :: Nil: _*".asInstanceOf[ScExpression]
  }

  def createIntegerLiteralExpression(value: Int)(implicit context: Project): ScExpression = {
    code"$value".asInstanceOf[ScExpression]
  }
}
