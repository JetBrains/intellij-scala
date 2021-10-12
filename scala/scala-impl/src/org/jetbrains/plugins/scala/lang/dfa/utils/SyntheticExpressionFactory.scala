package org.jetbrains.plugins.scala.lang.dfa.utils

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode.ScalaCodeContext
import org.jetbrains.plugins.scala.project.ProjectContext

object SyntheticExpressionFactory {

  def wrapInTupleExpression(tupleContents: Seq[ScExpression])(implicit context: ProjectContext): ScExpression = {
    val tupleContentsInText = tupleContents.map(_.getText).mkString(", ")
    code"($tupleContentsInText)".asInstanceOf[ScExpression]
  }

  def wrapInSplatListExpression(varargContents: Seq[ScExpression])(implicit context: ProjectContext): ScExpression = {
    val tupleContentsInText = varargContents.map(_.getText).mkString(" :: ")
    if (varargContents.isEmpty) code"Nil: _*".asInstanceOf[ScExpression]
    code"$tupleContentsInText :: Nil: _*".asInstanceOf[ScExpression]
  }
}
