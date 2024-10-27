package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{
  ScDependentFunctionTypeElement,
  ScDesugarizableToParametrizedTypeElement,
  ScTypeElement
}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScDependentFunctionTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
  with ScDependentFunctionTypeElement
  with ScDesugarizableToParametrizedTypeElement {
  override def parameterClause: ScParameterClause       = findChild[ScParameterClause].get
  override def returnTypeElement: Option[ScTypeElement] = getLastChild.asOptionOf[ScTypeElement]

  override def desugarizedText: String = {
    val typeParams =
      parameterClause.parameters
        .flatMap(_.typeElement)
        .map(_.getParamTypeText) :+ returnTypeElement.map(_.getText).getOrElse("Any")

    val className = "_root_.scala.Function"

    s"$className${typeParams.length - 1}${typeParams.mkString("[", ",", "]")}"
  }
}
