package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.{ScalaDfaControlFlowBuilder, TransformationFailedException}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScDefinitionWithAssignment}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportStmt}

/**
 * [[Transformable]] wrapper for all Scala PSI elements.
 *
 * It is the most likely entrypoint for building control flow for Scala code.
 * It passes responsibility further to more specific transformers.
 */
class ScalaPsiElementTransformer(override val builder: ScalaDfaControlFlowBuilder)
  extends Transformer
    with DefinitionTransformer
    with ExpressionTransformer
    with InvocationTransformer
    with TransformerUtils
    with specialSupport.CollectionAccessAssertionTransformer
    with specialSupport.SyntheticMethodsSpecialSupportTransformer
{

  def transformPsiElement(element: ScalaPsiElement): Unit = {
    element match {
      case expression: ScExpression => transformExpression(expression)
      case definition: ScDefinitionWithAssignment => transformDefinition(definition)
      case statement: ScDeclaration with ScBlockStatement => builder.pushUnknownCall(statement, 0)
      case _: ScImportStmt => builder.pushUnknownValue()
      case _: ScImportExpr => builder.pushUnknownValue()
      case _ => throw TransformationFailedException(element, "Unsupported PSI element.")
    }

    builder.finishElement(element)
  }
}
