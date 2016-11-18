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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StubBaseExt}

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */
class ScParameterStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          private val nameRef: StringRef,
                          private val typeTextRef: Option[StringRef],
                          val isStable: Boolean,
                          val isDefaultParameter: Boolean,
                          val isRepeated: Boolean,
                          val isVal: Boolean,
                          val isVar: Boolean,
                          val isCallByNameParameter: Boolean,
                          private val defaultExprTextRef: Option[StringRef],
                          private val deprecatedNameRef: Option[StringRef])
  extends StubBase[ScParameter](parent, elementType) with ScParameterStub {

  private var typeElementReference: SofterReference[Option[ScTypeElement]] = null
  private var defaultExpressionReference: SofterReference[Option[ScExpression]] = null

  override def getName: String = StringRef.toString(nameRef)

  override def typeText: Option[String] = typeTextRef.asString

  def typeElement: Option[ScTypeElement] = {
    typeElementReference = this.updateOptionalReference(typeElementReference) {
      case (context, child) =>
        typeText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    typeElementReference.get
  }

  override def defaultExprText: Option[String] = defaultExprTextRef.asString

  def defaultExpr: Option[ScExpression] = {
    defaultExpressionReference = this.updateOptionalReference(defaultExpressionReference) {
      case (context, child) =>
        defaultExprText.map {
          createExpressionWithContextFromText(_, context, child)
        }
    }
    defaultExpressionReference.get
  }

  override def deprecatedName: Option[String] = deprecatedNameRef.asString
}