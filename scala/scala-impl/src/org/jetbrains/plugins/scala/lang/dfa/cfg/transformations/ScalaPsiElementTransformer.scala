package org.jetbrains.plugins.scala.lang.dfa.cfg.transformations

import org.jetbrains.plugins.scala.lang.dfa.cfg.ScalaDfaControlFlowBuilder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDefinitionWithAssignment

class ScalaPsiElementTransformer(element: ScalaPsiElement) extends Transformable {

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
