package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScDefinitionWithAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}

trait ScalaPsiElementTransformation { this: ScalaDfaControlFlowBuilder =>
  def transformPsiElement(element: ScalaPsiElement): Unit = {
    element match {
      case expression: ScExpression => transformExpression(expression)
      case definition: ScDefinitionWithAssignment => transformDefinition(definition)
      case statement: ScDeclaration with ScBlockStatement => pushUnknownCall(statement, 0)
      case _: ScImportStmt => pushUnknownValue()
      case _: ScImportExpr => pushUnknownValue()
      case _ => throw TransformationFailedException(element, "Unsupported PSI element.")
    }

    finishElement(element)
  }
}
