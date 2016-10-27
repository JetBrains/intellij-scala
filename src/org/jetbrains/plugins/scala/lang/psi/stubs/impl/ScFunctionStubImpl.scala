package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StringRefArrayExt, StubBaseExt}

/**
  * User: Alexander Podkhalyuzin
  * Date: 14.10.2008
  */
class ScFunctionStubImpl(parent: StubElement[_ <: PsiElement],
                         elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                         private val nameRef: StringRef,
                         val isDeclaration: Boolean,
                         private val annotationsRefs: Array[StringRef],
                         private val returnTypeTextRef: Option[StringRef],
                         private val bodyTextRef: Option[StringRef],
                         val hasAssign: Boolean,
                         val isImplicit: Boolean,
                         val isLocal: Boolean)
  extends StubBase[ScFunction](parent, elementType) with ScFunctionStub {

  private var returnTypeElementReference: SofterReference[Option[ScTypeElement]] = null
  private var bodyTextElementReference: SofterReference[Option[ScExpression]] = null

  def getName: String = StringRef.toString(nameRef)

  def annotations: Array[String] = annotationsRefs.asStrings

  def returnTypeText: Option[String] = returnTypeTextRef.asString

  def returnTypeElement: Option[ScTypeElement] = {
    returnTypeElementReference = this.updateOptionalReference(returnTypeElementReference) {
      case (context, child) =>
        returnTypeText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    returnTypeElementReference.get
  }

  def bodyText: Option[String] = bodyTextRef.asString

  def bodyExpression: Option[ScExpression] = {
    bodyTextElementReference = this.updateOptionalReference(bodyTextElementReference) {
      case (context, child) =>
        bodyText.map {
          createExpressionWithContextFromText(_, context, child)
        }
    }
    bodyTextElementReference.get
  }
}