package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createConstructorFromText, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{MaybeStringRefExt, StringRefArrayExt}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateParentsStubImpl._

/**
  * User: Alexander Podkhalyuzin
  */
class ScTemplateParentsStubImpl[P <: ScTemplateParents](parent: StubElement[_ <: PsiElement],
                                                        elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                        private val parentTypeTextRefs: Array[StringRef],
                                                        private val constructorRef: Option[StringRef])
  extends StubBase[P](parent, elementType) with ScTemplateParentsStub[P] with PsiOwner[P] {

  private var constructorAndParentTypeElementsReference: SofterReference[Data] = null

  private def constructorAndParentTypeElements: Data = {
    getFromReferenceWithFilter[PsiElement, Data](constructorAndParentTypeElementsReference, {
      case (context, child) =>
        val constructor = constructorText.flatMap { text =>
          Option(createConstructorFromText(text, context, child))
        }
        val parentTypeElems = parentTypesTexts.toSeq.map {
          createTypeElementFromText(_, context, child)
        }
        (constructor, parentTypeElems)
    }, constructorAndParentTypeElementsReference = _)
  }

  def parentTypesTexts: Array[String] = parentTypeTextRefs.asStrings

  def parentTypeElements: Seq[ScTypeElement] = {
    constructorAndParentTypeElements match {
      case (Some(constr), typeElems) => constr.typeElement +: typeElems
      case (_, typeElems) => typeElems
    }
  }

  def constructorText: Option[String] = constructorRef.asString
}

private object ScTemplateParentsStubImpl {
  type Data = (Option[ScConstructor], Seq[ScTypeElement])

  implicit def flatten: Data => Seq[PsiElement] = {
    case (opt, seq) => opt.toSeq ++ seq
  }
}