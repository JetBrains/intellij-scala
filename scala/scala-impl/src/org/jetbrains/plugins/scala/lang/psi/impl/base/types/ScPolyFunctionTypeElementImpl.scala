package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParenthesisedTypeElement.InnermostTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScFunctionalTypeElement, ScPolyFunctionTypeElement, ScTupleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

class ScPolyFunctionTypeElementImpl(node: ASTNode)
  extends ScalaPsiElementImpl(node)
  with ScPolyFunctionTypeElement {
  override def resultTypeElement: Option[ScTypeElement] = getLastChild.asOptionOf[ScTypeElement]

  override def desugarizedText: String = {
    val (parametersText, resultTypeText) = resultTypeElement match {
      case Some(InnermostTypeElement(depFnType: ScDependentFunctionTypeElementImpl)) =>
        val paramClause    = depFnType.parameterClause
        val returnTypeText = depFnType.returnTypeElement.fold("scala.Any")(_.getText)
        paramClause.getText -> returnTypeText
      case Some(InnermostTypeElement(fnType: ScFunctionalTypeElement)) =>
        val paramsTypeElement = fnType.paramTypeElement
        val returnTypeText    = fnType.returnTypeElement.fold("scala.Any")(_.getText)

        paramsTypeElement match {
          case tuple: ScTupleTypeElement =>
            tuple.components.mapWithIndex { case (p, idx) =>
              s"x$idx: ${p.getText}"
            }.mkString("(", ", ", ")") -> returnTypeText
          case other => s"(x: ${other.getText})" -> returnTypeText
        }
      case _ => "()" -> "scala.Any"
    }

    val typeParametersText = typeParametersClause.fold("")(_.getTextByStub)

    s"""
       |scala.PolyFunction {
       |  def apply$typeParametersText$parametersText: $resultTypeText
       |}
       |""".stripMargin
  }
}
