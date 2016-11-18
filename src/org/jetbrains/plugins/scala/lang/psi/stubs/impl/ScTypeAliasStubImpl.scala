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
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StubBaseExt}

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
class ScTypeAliasStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          private val nameRef: StringRef,
                          private val typeElementTextRef: Option[StringRef],
                          private val lowerTypeElementTextRef: Option[StringRef],
                          private val upperTypeElementTextRef: Option[StringRef],
                          val isLocal: Boolean,
                          val isDeclaration: Boolean,
                          val isStableQualifier: Boolean)
  extends StubBase[ScTypeAlias](parent, elementType) with ScTypeAliasStub {
  private var typeElementReference: SofterReference[Option[ScTypeElement]] = null
  private var lowerTypeElementReference: SofterReference[Option[ScTypeElement]] = null
  private var upperTypeElementReference: SofterReference[Option[ScTypeElement]] = null

  def getName: String = StringRef.toString(nameRef)

  def typeElementText: Option[String] = typeElementTextRef.asString

  def typeElement: Option[ScTypeElement] = {
    typeElementReference = this.updateOptionalReference(typeElementReference) {
      case (context, child) =>
        typeElementText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    typeElementReference.get
  }

  def lowerBoundElementText: Option[String] = lowerTypeElementTextRef.asString

  def lowerBoundTypeElement: Option[ScTypeElement] = {
    lowerTypeElementReference = this.updateOptionalReference(lowerTypeElementReference) {
      case (context, child) =>
        lowerBoundElementText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    lowerTypeElementReference.get
  }

  def upperBoundElementText: Option[String] = upperTypeElementTextRef.asString

  def upperBoundTypeElement: Option[ScTypeElement] = {
    upperTypeElementReference = this.updateOptionalReference(upperTypeElementReference) {
      case (context, child) =>
        upperBoundElementText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    upperTypeElementReference.get
  }
}