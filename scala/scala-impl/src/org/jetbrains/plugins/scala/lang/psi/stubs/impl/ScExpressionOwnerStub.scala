package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import org.jetbrains.plugins.scala.lang.psi.stubs.ScStubElement
import com.intellij.psi.PsiElement
import com.intellij.util.SofterReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText

trait ScExpressionOwnerStub[E <: PsiElement] extends ScStubElement[E] with PsiOwner[E] {

  def bodyText: Option[String]

  private[impl] var expressionElementReference: SofterReference[Option[ScExpression]] = _

  def bodyExpression: Option[ScExpression] = {
    getFromOptionalReference(expressionElementReference) {
      case (context, child) =>
        bodyText.map {
          createExpressionWithContextFromText(_, context, child)
        }
    } (expressionElementReference = _)
  }
}