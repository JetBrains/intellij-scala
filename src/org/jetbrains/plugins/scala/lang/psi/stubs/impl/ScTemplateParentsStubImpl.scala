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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createConstructorTypeElementFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StringRefArrayExt}

/**
  * User: Alexander Podkhalyuzin
  */
class ScTemplateParentsStubImpl[P <: ScTemplateParents](parent: StubElement[_ <: PsiElement],
                                                        elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                        private val parentTypeTextRefs: Array[StringRef],
                                                        private val constructorRef: Option[StringRef])
  extends StubBase[P](parent, elementType) with ScTemplateParentsStub[P] with PsiOwner[P] {
  private var parentTypesElementReferences: SofterReference[Seq[ScTypeElement]] = null

  def parentTypesTexts: Array[String] = parentTypeTextRefs.asStrings

  def parentTypeElements: Seq[ScTypeElement] = {
    parentTypesElementReferences = updateReference(parentTypesElementReferences) {
      case (context, child) =>
        constructorText.toSeq.map {
          createConstructorTypeElementFromText(_, context, child)
        } ++ parentTypesTexts.map {
          createTypeElementFromText(_, context, child)
        }
    }
    parentTypesElementReferences.get
  }

  def constructorText: Option[String] = constructorRef.asString
}