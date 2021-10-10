package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDefinitionWithAssignment

/**
 * [[Transformable]] wrapper for all Scala PSI elements.
 *
 * It is the most likely entrypoint for building control flow for Scala code.
 * It passes responsibility further to more specific transformers.
 */
class ScalaPsiElementTransformer(val transformedElement: ScalaPsiElement) extends Transformable {

  override def toString: String = s"ScalaPsiElementTransformer: $transformedElement"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = {
    val transformer = transformedElement match {
      case expression: ScExpression => new ExpressionTransformer(expression)
      case definition: ScDefinitionWithAssignment => new DefinitionTransformer(definition)
      case _ => throw TransformationFailedException(transformedElement, "Unsupported PSI element.")
    }

    transformer.transform(builder)
    builder.finishElement(transformedElement)
  }

  protected def transformPsiElement(element: ScalaPsiElement, builder: ScalaDfaControlFlowBuilder): Unit = {
    new ScalaPsiElementTransformer(element).transform(builder)
  }

  protected def transformIfPresent(container: Option[ScalaPsiElement], builder: ScalaDfaControlFlowBuilder): Unit = {
    container match {
      case Some(element) => transformPsiElement(element, builder)
      case None => builder.pushUnknownValue()
    }
  }
}
