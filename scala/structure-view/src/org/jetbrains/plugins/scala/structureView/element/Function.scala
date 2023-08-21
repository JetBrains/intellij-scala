package org.jetbrains.plugins.scala.structureView.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.structureView.element.AbstractItemPresentation.withSimpleNames

private class Function(function: ScFunction, inherited: Boolean)
  extends AbstractTreeElementDelegatingChildrenToPsi(function, inherited)
  with InheritedLocationStringItemPresentation {

  override def location: Option[String] = Option(function.containingClass).map(_.name)

  override def getPresentableText: String = {
    val presentation = renderFunctionFromStubs(function)
    withSimpleNames(presentation)
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
