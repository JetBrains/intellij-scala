package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationStub

class ScAnnotationStubImpl(parent: StubElement[_ <: PsiElement],
                           elementType: IElementType,
                           override val annotationText: String,
                           override val name: Option[String])
  extends StubBase[ScAnnotation](parent, elementType) with ScAnnotationStub with PsiOwner[ScAnnotation] {

  private[impl] var annotationExprRef: SofterReference[Option[ScAnnotationExpr]] = _

  override def annotationExpr: Option[ScAnnotationExpr] = {
    getFromOptionalReference(annotationExprRef) {
      case (context, _) =>
        val annotation = ScalaPsiElementFactory.createAnAnnotation(annotationText, context)(getProject)
        val annotationExpr = annotation.annotationExpr
        annotationExpr.context = context
        Some(annotationExpr)
    } (annotationExprRef = _)

  }

  override def typeElement: Option[ScTypeElement] = annotationExpr.map(_.constructorInvocation.typeElement)
}
