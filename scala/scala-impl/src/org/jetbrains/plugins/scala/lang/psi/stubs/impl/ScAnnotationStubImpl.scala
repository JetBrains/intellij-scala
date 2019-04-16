package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createAnnotationExpression

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
      case (context, _) =>
        val annotationExpr = createAnnotationExpression(annotationText)(getProject)
        annotationExpr.context = context
        Some(annotationExpr)
    } (annotationExprRef = _)

  }

  def typeElement: Option[ScTypeElement] = annotationExpr.map(_.constructorInvocation.typeElement)

  def name: Option[String] = {
    //this method is used during indexing when stubs don't have valid psi available
    val dummyExpr = createAnnotationExpression(annotationText)(ProjectManager.getInstance().getDefaultProject)
    val typeElem = dummyExpr.constructorInvocation.typeElement

    val simpleTypeElement = typeElem match {
      case parenthesised: ScParenthesisedTypeElement => parenthesised.innerElement.asOptionOf[ScSimpleTypeElement]
      case simple: ScSimpleTypeElement => Some(simple)
      case _ => None
    }
    simpleTypeElement.flatMap(_.reference.map(_.refName))
  }
}
