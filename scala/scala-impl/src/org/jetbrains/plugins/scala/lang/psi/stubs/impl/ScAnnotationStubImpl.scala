package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationExpr}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
  * User: Alexander Podkhalyuzin
  * Date: 22.06.2009
  */
class ScAnnotationStubImpl(parent: StubElement[_ <: PsiElement],
                           elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                           val annotationText: String)
  extends StubBase[ScAnnotation](parent, elementType) with ScAnnotationStub with PsiOwner[ScAnnotation] {

  private[impl] var annotationExprRef: SofterReference[Option[ScAnnotationExpr]] = null

  def annotationExpr: Option[ScAnnotationExpr] = {
    getFromOptionalReference(annotationExprRef) {
      case (context, child) =>
        val annotation = ScalaPsiElementFactory.createAnAnnotation(annotationText)(getProject)
        val expr = annotation.annotationExpr
        expr.setContext(this.getPsi, null)
        Some(expr)

    } (annotationExprRef = _)

  }

  def typeElement: Option[ScTypeElement] = annotationExpr.map(_.constr.typeElement)

  def name: Option[String] = {
    typeElement.map {
      case parenthesised: ScParenthesisedTypeElement => parenthesised.innerElement
      case simple: ScSimpleTypeElement => Some(simple)
      case _ => None
    }.collect {
      case simple: ScSimpleTypeElement => simple
    }.flatMap {
      _.reference
    }.map {
      _.refName
    }
  }
}
