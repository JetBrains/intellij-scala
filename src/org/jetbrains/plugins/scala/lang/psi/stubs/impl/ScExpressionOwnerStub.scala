package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.MaybeStringRefExt

/**
  * @author adkozlov
  */
trait ScExpressionOwnerStub[E <: PsiElement] extends StubElement[E] with PsiOwner[E] {
  protected[impl] val bodyTextRef: Option[StringRef]

  def bodyText: Option[String] = bodyTextRef.asString

  private[impl] var expressionElementReference: SofterReference[Option[ScExpression]] = null

  def bodyExpression: Option[ScExpression] = {
    expressionElementReference = updateOptionalReference(expressionElementReference) {
      case (context, child) =>
        bodyText.map {
          createExpressionWithContextFromText(_, context, child)
        }
    }
    expressionElementReference.get
  }
}