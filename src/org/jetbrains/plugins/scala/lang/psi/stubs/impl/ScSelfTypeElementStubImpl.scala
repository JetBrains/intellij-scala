package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSelfTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeElementFromText
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StringRefArrayExt, StubBaseExt}

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.06.2009
 */
class ScSelfTypeElementStubImpl(parent: StubElement[_ <: PsiElement],
                                elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                private val nameRef: StringRef,
                                private val typeElementTextRef: Option[StringRef],
                                private var typeNamesRefs: Array[StringRef])
  extends StubBase[ScSelfTypeElement](parent, elementType) with ScSelfTypeElementStub {

  private var typeElementReference: SofterReference[Option[ScTypeElement]] = null

  override def getName: String = StringRef.toString(nameRef)

  override def typeElementText: Option[String] = typeElementTextRef.asString

  override def typeElement: Option[ScTypeElement] = {
    typeElementReference = this.updateOptionalReference(typeElementReference) {
      case (context, child) =>
        typeElementText.map {
          createTypeElementFromText(_, context, child)
        }
    }
    typeElementReference.get
  }

  override def classNames: Array[String] = typeNamesRefs.asStrings
}