package org.jetbrains.plugins.scala.lang.dfa.controlFlow.transformations

import org.jetbrains.plugins.scala.lang.dfa.controlFlow.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDefinitionWithAssignment

/**
 * [[Transformable]] wrapper for all Scala PSI elements.
 *
 * It is the most likely entrypoint for building control flow for any Scala code.
 * It passes responsibility further to specific transformers.
 */
class ScalaPsiElementTransformer(val element: ScalaPsiElement) extends Transformable {

  override def toString: String = s"ScalaPsiElementTransformer: $element"

  override def transform(builder: ScalaDfaControlFlowBuilder): Unit = {
    val transformer = element match {
      case expression: ScExpression => new ExpressionTransformer(expression)
      case definition: ScDefinitionWithAssignment => new DefinitionTransformer(definition)
      case _ => throw TransformationFailedException(element, "Unsupported PSI element.")
    }

    transformer.transform(builder)
    builder.finishElement(element)
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
