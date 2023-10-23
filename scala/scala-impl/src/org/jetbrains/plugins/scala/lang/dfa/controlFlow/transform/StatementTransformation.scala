package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transform

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockStatement, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDefinitionWithAssignment
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}

trait StatementTransformation { this: ScalaDfaControlFlowBuilder =>
  def transformStatement(stmt: ScBlockStatement, rreq: ResultReq): rreq.Result = {
    val result = stmt match {
      case expression: ScExpression =>
        transformExpression(expression, rreq)
      case definition: ScDefinitionWithAssignment =>
        transformDefinition(definition)
        pushUnit(rreq)
      //case statement: ScDeclaration with ScBlockStatement => pushUnknownCall(statement, 0)
      case _: ScImportStmt =>
        // nothing to do
        pushUnit(rreq)
      case _: ScImportExpr =>
      // nothing to do
        pushUnit(rreq)
      case _ =>
        throw TransformationFailedException.todo(stmt)
    }

    result
  }
}
