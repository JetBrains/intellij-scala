package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.TypePresentationContext
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.structureView.element.AbstractItemPresentation.withSimpleNames

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
private class Function(function: ScFunction, inherited: Boolean, override val showType: Boolean)
  extends AbstractTreeElement(function, inherited) with Typed {

  override def location: Option[String] = Option(function.containingClass).map(_.name)

  override def getPresentableText: String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(function)

    val presentation = renderFunctionFromStubs(function)

    val inferredType =
      if (function.isConstructor) None
      else function.returnTypeElement match {
        case Some(_) => None
        case None => if (showType) function.returnType.toOption.map(ScTypePresentation.withoutAliases) else None
      }

    withSimpleNames(presentation + inferredType.map(": " + _).mkString)
  }

  private def renderFunctionFromStubs(function: ScFunction): String = {
    val typeParameters = function.typeParametersClause.fold("")(_.getTextByStub)
    val parameters = function.paramClauses.toOption.fold("")(FromStubsParameterRenderer.renderClauses)
    val typeAnnotation = function.returnTypeElement.map(_.getText).fold("")(": " + _)

    s"${function.name}$typeParameters$parameters$typeAnnotation"
  }

  override def children: Seq[PsiElement] = function match {
    case definition: ScFunctionDefinition => definition.body match {
      case Some(block: ScBlockExpr) => Block.childrenOf(block)
      case _ => Seq.empty
    }
    case _ => Seq.empty
  }

  override def isAlwaysLeaf: Boolean = false
}
