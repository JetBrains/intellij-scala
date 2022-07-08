package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

class ScAnnotationStubImpl(parent: StubElement[_ <: PsiElement],
                           elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                           override val annotationText: String,
                           override val name: Option[String])
  extends StubBase[ScAnnotation](parent, elementType) with ScAnnotationStub with PsiOwner[ScAnnotation] {

  private[impl] var annotationExprRef: SofterReference[Option[ScAnnotationExpr]] = _

  override def annotationExpr: Option[ScAnnotationExpr] = {
    getFromOptionalReference(annotationExprRef) {
      case (context, _) =>
        val annotation = ScalaPsiElementFactory.createAnAnnotation(annotationText)(getProject)
        val annotationExpr = annotation.annotationExpr
        annotationExpr.context = context
        Some(annotationExpr)
    } (annotationExprRef = _)

  }

  override def typeElement: Option[ScTypeElement] = annotationExpr.map(_.constructorInvocation.typeElement)
}
